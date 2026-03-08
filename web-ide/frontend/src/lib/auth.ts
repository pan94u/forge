/**
 * OIDC authentication helper for Keycloak SSO.
 * Uses Authorization Code flow with PKCE for public clients.
 *
 * SSO URL discovery:
 * - Frontend fetches /api/auth/sso-config from the backend at runtime.
 * - This eliminates build-time NEXT_PUBLIC_* env var dependencies.
 * - The browser redirects directly to the SSO domain for login (standard OIDC).
 * - Token exchange and refresh also go directly to SSO (Keycloak handles CORS
 *   via the client's webOrigins configuration).
 *
 * Token lifecycle:
 * - After login, scheduleTokenRefresh() sets a timer to refresh 60s before expiry.
 * - initTokenRefresh() restores the schedule on page reload.
 * - On 401 from backend, callers should call refreshToken() before redirecting to login.
 */

import { sha256 } from "js-sha256";

const TOKEN_KEY = "forge_access_token";
const REFRESH_TOKEN_KEY = "forge_refresh_token";
const TOKEN_EXPIRY_KEY = "forge_token_expiry";
const CODE_VERIFIER_KEY = "forge_code_verifier";

// ---------------------------------------------------------------------------
// SSO config — fetched once from backend, cached in memory
// ---------------------------------------------------------------------------

interface SsoConfig {
  ssoUrl: string;
  realm: string;
  clientId: string;
}

let _ssoConfig: SsoConfig | null = null;
let _ssoConfigPromise: Promise<SsoConfig> | null = null;

async function ensureSsoConfig(): Promise<SsoConfig> {
  if (_ssoConfig) return _ssoConfig;
  if (!_ssoConfigPromise) {
    _ssoConfigPromise = fetch("/api/auth/sso-config")
      .then((res) => {
        if (!res.ok) throw new Error(`SSO config fetch failed: ${res.status}`);
        return res.json();
      })
      .then((data: SsoConfig) => {
        _ssoConfig = data;
        return data;
      })
      .catch((err) => {
        _ssoConfigPromise = null; // allow retry on failure
        throw err;
      });
  }
  return _ssoConfigPromise;
}

function getBaseUrl(): string {
  if (!_ssoConfig) {
    throw new Error("SSO config not loaded. Call ensureSsoConfig() first.");
  }
  return `${_ssoConfig.ssoUrl}/realms/${_ssoConfig.realm}/protocol/openid-connect`;
}

function getClientId(): string {
  if (!_ssoConfig) {
    throw new Error("SSO config not loaded. Call ensureSsoConfig() first.");
  }
  return _ssoConfig.clientId;
}

// ---------------------------------------------------------------------------
// PKCE helpers
// ---------------------------------------------------------------------------

function generateCodeVerifier(): string {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return btoa(String.fromCharCode(...array))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

async function generateCodeChallenge(verifier: string): Promise<string> {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);

  let digest: ArrayBuffer;
  if (typeof crypto !== "undefined" && crypto.subtle) {
    // Secure context (HTTPS or localhost) — use native Web Crypto
    digest = await crypto.subtle.digest("SHA-256", data);
  } else {
    // Non-secure context (e.g., http://forge.local) — use js-sha256 fallback
    const hash = sha256.arrayBuffer(data);
    digest = hash;
  }

  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

// ---------------------------------------------------------------------------
// Login / Callback
// ---------------------------------------------------------------------------

export async function login(): Promise<void> {
  const config = await ensureSsoConfig();
  const verifier = generateCodeVerifier();
  const challenge = await generateCodeChallenge(verifier);

  sessionStorage.setItem(CODE_VERIFIER_KEY, verifier);
  sessionStorage.setItem("forge_redirect_after_login", window.location.pathname);

  const params = new URLSearchParams({
    client_id: config.clientId,
    redirect_uri: `${window.location.origin}/auth/callback`,
    response_type: "code",
    scope: "openid profile email",
    code_challenge: challenge,
    code_challenge_method: "S256",
  });

  window.location.href = `${getBaseUrl()}/auth?${params}`;
}

export async function handleCallback(code: string): Promise<boolean> {
  const verifier = sessionStorage.getItem(CODE_VERIFIER_KEY);
  if (!verifier) {
    console.error("No code verifier found");
    return false;
  }

  try {
    await ensureSsoConfig();
    const response = await fetch(`${getBaseUrl()}/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        grant_type: "authorization_code",
        client_id: getClientId(),
        code,
        redirect_uri: `${window.location.origin}/auth/callback`,
        code_verifier: verifier,
      }),
    });

    if (!response.ok) {
      console.error("Token exchange failed:", response.status);
      return false;
    }

    const data = await response.json();
    storeTokens(data);
    sessionStorage.removeItem(CODE_VERIFIER_KEY);
    return true;
  } catch (err) {
    console.error("Token exchange error:", err);
    return false;
  }
}

// ---------------------------------------------------------------------------
// Token storage helpers
// ---------------------------------------------------------------------------

function storeTokens(data: {
  access_token: string;
  refresh_token?: string;
  expires_in: number;
}): void {
  localStorage.setItem(TOKEN_KEY, data.access_token);
  if (data.refresh_token) {
    localStorage.setItem(REFRESH_TOKEN_KEY, data.refresh_token);
  }
  const expiresAt = Date.now() + data.expires_in * 1000;
  localStorage.setItem(TOKEN_EXPIRY_KEY, expiresAt.toString());
  scheduleTokenRefresh(data.expires_in);
}

// ---------------------------------------------------------------------------
// Proactive token refresh — runs before the access token expires
// ---------------------------------------------------------------------------

let _refreshTimer: ReturnType<typeof setTimeout> | null = null;
let _refreshInFlight: Promise<boolean> | null = null;

function scheduleTokenRefresh(expiresIn: number): void {
  if (_refreshTimer) clearTimeout(_refreshTimer);
  // Refresh 60 seconds before expiry; minimum 5 seconds
  const delay = Math.max((expiresIn - 60) * 1000, 5_000);
  _refreshTimer = setTimeout(async () => {
    const ok = await refreshToken();
    if (!ok && typeof window !== "undefined") {
      console.warn("[auth] Token refresh failed — redirecting to login");
      clearTokens();
      window.location.href = "/login";
    }
  }, delay);
}

/**
 * Exchange the stored refresh_token for a new access_token.
 * Returns true on success, false if the refresh token is invalid/expired.
 * Concurrent calls are deduplicated — only one request is in-flight at a time.
 */
export async function refreshToken(): Promise<boolean> {
  // Deduplicate concurrent refresh attempts
  if (_refreshInFlight) return _refreshInFlight;

  const refreshTokenStr = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshTokenStr) return false;

  _refreshInFlight = (async () => {
    try {
      await ensureSsoConfig();
      const response = await fetch(`${getBaseUrl()}/token`, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "refresh_token",
          client_id: getClientId(),
          refresh_token: refreshTokenStr,
        }),
      });

      if (!response.ok) {
        console.error("[auth] Refresh token rejected:", response.status);
        return false;
      }

      const data = await response.json();
      storeTokens(data);
      return true;
    } catch (err) {
      console.error("[auth] Refresh token error:", err);
      return false;
    } finally {
      _refreshInFlight = null;
    }
  })();

  return _refreshInFlight;
}

/**
 * Call once on app startup (e.g. in the root layout useEffect) to restore
 * the refresh schedule after a page reload.
 * If the token is already expired, attempts an immediate refresh.
 */
export async function initTokenRefresh(): Promise<void> {
  if (typeof window === "undefined") return;

  // Pre-load SSO config so subsequent calls are synchronous
  await ensureSsoConfig().catch(() => {
    console.warn("[auth] Failed to load SSO config — SSO login may not work");
  });

  const expiry = localStorage.getItem(TOKEN_EXPIRY_KEY);
  const stored = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!expiry || !stored) return;

  const now = Date.now();
  const expiresAt = parseInt(expiry, 10);

  if (now >= expiresAt) {
    // Already expired — refresh immediately
    const ok = await refreshToken();
    if (!ok) {
      clearTokens();
      window.location.href = "/login";
    }
  } else {
    // Still valid — schedule refresh before expiry
    const expiresIn = Math.floor((expiresAt - now) / 1000);
    scheduleTokenRefresh(expiresIn);
  }
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function isAuthenticated(): boolean {
  const token = getToken();
  if (!token) return false;

  const expiry = localStorage.getItem(TOKEN_EXPIRY_KEY);
  if (expiry && Date.now() > parseInt(expiry, 10)) {
    // Token expired — do NOT clear tokens here; initTokenRefresh() handles
    // proactive renewal. Callers should use refreshToken() if needed.
    return false;
  }

  return true;
}

export function clearTokens(): void {
  if (_refreshTimer) {
    clearTimeout(_refreshTimer);
    _refreshTimer = null;
  }
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(TOKEN_EXPIRY_KEY);
}

export async function logout(): Promise<void> {
  clearTokens();
  try {
    await ensureSsoConfig();
    const params = new URLSearchParams({
      client_id: getClientId(),
      post_logout_redirect_uri: window.location.origin,
    });
    window.location.href = `${getBaseUrl()}/logout?${params}`;
  } catch {
    // SSO config unavailable — just go to login page
    window.location.href = "/login";
  }
}

export function getRedirectAfterLogin(): string {
  const path = sessionStorage.getItem("forge_redirect_after_login");
  sessionStorage.removeItem("forge_redirect_after_login");
  return path || "/";
}

/**
 * Get authorization headers for API requests.
 * Returns empty object if not authenticated.
 */
export function getAuthHeaders(): Record<string, string> {
  const token = getToken();
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}
