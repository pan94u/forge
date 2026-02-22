/**
 * Unified SSO Client for Forge Platform
 * Supports multiple authentication providers:
 * - Keycloak (existing OIDC)
 * - Local account (new User Service)
 * - GitHub OAuth (new)
 */

// Configuration
const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:9443";
const KEYCLOAK_URL = process.env.NEXT_PUBLIC_KEYCLOAK_URL || "http://localhost:8180";
const KEYCLOAK_REALM = process.env.NEXT_PUBLIC_KEYCLOAK_REALM || "forge";
const KEYCLOAK_CLIENT_ID = process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID || "forge-web-ide";

// Token keys
const TOKEN_KEY = "forge_access_token";
const REFRESH_TOKEN_KEY = "forge_refresh_token";
const TOKEN_EXPIRY_KEY = "forge_token_expiry";
const AUTH_PROVIDER_KEY = "forge_auth_provider";

// ==================== Types ====================

export type AuthProvider = "keycloak" | "local" | "github" | "guest";

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserInfo;
}

export interface UserInfo {
  id: string;
  username: string;
  email: string | null;
  status: string;
  avatar: string | null;
}

export interface LoginResult {
  success: boolean;
  error?: string;
  data?: AuthResponse;
}

// ==================== Keycloak SSO ====================

export async function loginWithKeycloak(): Promise<void> {
  const verifier = generateCodeVerifier();
  const challenge = await generateCodeChallenge(verifier);

  sessionStorage.setItem("forge_code_verifier", verifier);
  sessionStorage.setItem("forge_redirect_after_login", window.location.pathname);
  sessionStorage.setItem("forge_auth_provider", "keycloak");

  const params = new URLSearchParams({
    client_id: KEYCLOAK_CLIENT_ID,
    redirect_uri: `${window.location.origin}/auth/callback`,
    response_type: "code",
    scope: "openid profile email",
    code_challenge: challenge,
    code_challenge_method: "S256",
  });

  const authUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/auth?${params}`;
  window.location.href = authUrl;
}

export async function handleKeycloakCallback(code: string): Promise<LoginResult> {
  const verifier = sessionStorage.getItem("forge_code_verifier");
  if (!verifier) {
    return { success: false, error: "No code verifier found" };
  }

  try {
    const response = await fetch(
      `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/token`,
      {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          grant_type: "authorization_code",
          client_id: KEYCLOAK_CLIENT_ID,
          code,
          redirect_uri: `${window.location.origin}/auth/callback`,
          code_verifier: verifier,
        }),
      }
    );

    if (!response.ok) {
      return { success: false, error: "Token exchange failed" };
    }

    const data = await response.json();
    const authResponse: AuthResponse = {
      accessToken: data.access_token,
      refreshToken: data.refresh_token,
      tokenType: "Bearer",
      expiresIn: data.expires_in,
      user: await fetchKeycloakUserInfo(data.access_token),
    };

    saveAuthResponse(authResponse, "keycloak");
    sessionStorage.removeItem("forge_code_verifier");

    return { success: true, data: authResponse };
  } catch (error) {
    return { success: false, error: String(error) };
  }
}

async function fetchKeycloakUserInfo(token: string): Promise<UserInfo> {
  const response = await fetch(
    `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/userinfo`,
    {
      headers: { Authorization: `Bearer ${token}` },
    }
  );

  if (!response.ok) {
    return { id: "", username: "unknown", email: null, status: "active", avatar: null };
  }

  const data = await response.json();
  return {
    id: data.sub,
    username: data.preferred_username || data.name || "user",
    email: data.email,
    status: "active",
    avatar: data.picture || null,
  };
}

// ==================== Local Account ====================

export async function loginWithLocal(
  username: string,
  password: string
): Promise<LoginResult> {
  try {
    const response = await fetch(`${API_URL}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
      const error = await response.json();
      return { success: false, error: error.message || "Login failed" };
    }

    const data = await response.json();
    saveAuthResponse(data, "local");

    return { success: true, data };
  } catch (error) {
    return { success: false, error: String(error) };
  }
}

export async function registerLocal(
  username: string,
  password: string,
  email?: string
): Promise<LoginResult> {
  try {
    const response = await fetch(`${API_URL}/api/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password, email }),
    });

    if (!response.ok) {
      const error = await response.json();
      return { success: false, error: error.message || "Registration failed" };
    }

    const data = await response.json();
    saveAuthResponse(data, "local");

    return { success: true, data };
  } catch (error) {
    return { success: false, error: String(error) };
  }
}

export async function refreshLocalToken(): Promise<boolean> {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) return false;

  try {
    const response = await fetch(`${API_URL}/api/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });

    if (!response.ok) {
      clearTokens();
      return false;
    }

    const data = await response.json();
    const currentProvider = getAuthProvider();

    saveAuthResponse(
      {
        ...data,
        user: getUserInfo(),
      },
      currentProvider || "local"
    );

    return true;
  } catch {
    clearTokens();
    return false;
  }
}

// ==================== Guest Login ====================

export async function loginAsGuest(
  email?: string,
  phone?: string,
  displayName?: string
): Promise<LoginResult> {
  try {
    const response = await fetch(`${API_URL}/api/auth/guest`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, phone, displayName }),
    });

    if (!response.ok) {
      const error = await response.json();
      return { success: false, error: error.message || "Guest login failed" };
    }

    const data = await response.json();
    saveAuthResponse(data, "guest");

    return { success: true, data };
  } catch (error) {
    return { success: false, error: String(error) }
  }
}

// ==================== GitHub OAuth ====================

export function loginWithGithub(): void {
  const state = generateRandomString(32);
  const redirectUri = `${window.location.origin}/auth/callback/github`;
  sessionStorage.setItem("forge_github_state", state);
  sessionStorage.setItem("forge_auth_provider", "github");

  // Store redirect URL
  sessionStorage.setItem("forge_redirect_after_login", window.location.pathname);

  // Redirect to backend OAuth endpoint
  const authUrl = `${API_URL}/api/auth/oauth/github/authorize?state=${state}&redirectUri=${encodeURIComponent(redirectUri)}`;
  window.location.href = authUrl;
}

export async function handleGithubCallback(
  code: string,
  state: string
): Promise<LoginResult> {
  const storedState = sessionStorage.getItem("forge_github_state");
  if (state !== storedState) {
    return { success: false, error: "Invalid state parameter" };
  }

  try {
    const redirectUri = `${window.location.origin}/auth/callback/github`;
    const response = await fetch(
      `${API_URL}/api/auth/oauth/github/callback?code=${code}&state=${state}&redirectUri=${encodeURIComponent(redirectUri)}`
    );

    if (!response.ok) {
      const error = await response.json();
      return { success: false, error: error.message || "GitHub login failed" };
    }

    const data = await response.json();
    saveAuthResponse(data.auth, "github");
    sessionStorage.removeItem("forge_github_state");

    return { success: true, data: data.auth };
  } catch (error) {
    return { success: false, error: String(error) };
  }
}

// ==================== Common Functions ====================

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getAuthProvider(): AuthProvider | null {
  return localStorage.getItem(AUTH_PROVIDER_KEY) as AuthProvider | null;
}

export function isAuthenticated(): boolean {
  const token = getToken();
  if (!token) return false;

  const expiry = localStorage.getItem(TOKEN_EXPIRY_KEY);
  if (expiry && Date.now() > parseInt(expiry, 10)) {
    clearTokens();
    return false;
  }

  return true;
}

export function clearTokens(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(TOKEN_EXPIRY_KEY);
  localStorage.removeItem(AUTH_PROVIDER_KEY);
}

export function logout(): void {
  const provider = getAuthProvider();
  clearTokens();

  if (provider === "keycloak") {
    const params = new URLSearchParams({
      client_id: KEYCLOAK_CLIENT_ID,
      post_logout_redirect_uri: window.location.origin,
    });
    window.location.href = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect/logout?${params}`;
  } else {
    // For local/github, just redirect to home
    window.location.href = "/";
  }
}

export function getAuthHeaders(): Record<string, string> {
  const token = getToken();
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}

export function getUserInfo(): UserInfo {
  const userInfo = localStorage.getItem("forge_user_info");
  if (userInfo) {
    return JSON.parse(userInfo);
  }
  return { id: "", username: "unknown", email: null, status: "active", avatar: null };
}

export function getRedirectAfterLogin(): string {
  const path = sessionStorage.getItem("forge_redirect_after_login");
  sessionStorage.removeItem("forge_redirect_after_login");
  return path || "/";
}

// ==================== Private Helpers ====================

function saveAuthResponse(response: AuthResponse, provider: AuthProvider): void {
  localStorage.setItem(TOKEN_KEY, response.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, response.refreshToken);
  localStorage.setItem(TOKEN_EXPIRY_KEY, (Date.now() + response.expiresIn * 1000).toString());
  localStorage.setItem(AUTH_PROVIDER_KEY, provider);
  localStorage.setItem("forge_user_info", JSON.stringify(response.user));
}

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
  const digest = await crypto.subtle.digest("SHA-256", data);
  return btoa(String.fromCharCode(...new Uint8Array(digest)))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function generateRandomString(length: number): string {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  return array.map((x) => chars[x % chars.length]).join("");
}

// ==================== Auto Token Refresh ====================

export function startTokenRefresh(): void {
  // Refresh token 1 minute before expiry
  const expiry = localStorage.getItem(TOKEN_EXPIRY_KEY);
  if (!expiry) return;

  const expiresAt = parseInt(expiry, 10);
  const refreshAt = expiresAt - 60000; // 1 minute before

  const delay = Math.max(0, refreshAt - Date.now());

  setTimeout(async () => {
    const provider = getAuthProvider();
    if (provider === "local" || provider === "github") {
      await refreshLocalToken();
    }
    // Keycloak handles refresh separately
  }, delay);
}