/**
 * Authentication helper for Forge Web IDE.
 *
 * Architecture:
 * - Local dev: No auth, no gateway. Backend returns {enabled: false} from /api/auth/sso-config.
 *   All API calls work without tokens. isAuthenticated() returns true.
 * - Production: synapse/gateway sits in front of backend, handles OIDC login via
 *   Synapse Enterprise Console. Gateway injects X-User-* headers. Frontend doesn't
 *   manage tokens — gateway manages session via httpOnly cookie.
 *
 * This file is intentionally minimal. Auth is handled by the gateway, not the frontend.
 */

let _authEnabled: boolean | null = null;

async function checkAuthEnabled(): Promise<boolean> {
  if (_authEnabled !== null) return _authEnabled;
  try {
    const res = await fetch("/api/auth/sso-config");
    const data = await res.json();
    _authEnabled = data.enabled === true;
  } catch {
    _authEnabled = false;
  }
  return _authEnabled;
}

export async function login(): Promise<void> {
  const enabled = await checkAuthEnabled();
  if (!enabled) return; // dev mode, no login needed
  // Production: gateway handles login redirect, just reload
  window.location.href = "/";
}

export async function logout(): Promise<void> {
  // Production: gateway clears session
  window.location.href = "/gateway/logout";
}

export function getToken(): string | null {
  return null; // Gateway handles auth via cookie, frontend doesn't manage tokens
}

export function isAuthenticated(): boolean {
  return true; // If request reached frontend, user is authenticated (gateway validated)
}

export function clearTokens(): void {
  // No-op: gateway manages session
}

export async function initTokenRefresh(): Promise<void> {
  // No-op: gateway manages token lifecycle
}

export async function refreshToken(): Promise<boolean> {
  return true; // Gateway handles refresh
}

export function getRedirectAfterLogin(): string {
  return "/";
}

export function getAuthHeaders(): Record<string, string> {
  return {}; // Gateway injects auth, frontend doesn't need to
}

export async function handleCallback(_code: string): Promise<boolean> {
  return true; // Gateway handles OIDC callback
}
