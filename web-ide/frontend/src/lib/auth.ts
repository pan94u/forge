/**
 * Authentication helper for Forge Web IDE.
 *
 * Uses @opc-ai/auth SDK for token-first auth:
 * - Gateway OIDC callback delivers JWT via URL params (_token/_refresh)
 * - initAuth() captures token, stores in localStorage as 'forge_access_token'
 * - All 6 API files (skill-api, workspace-api, etc.) read this key automatically
 * - Cross-domain calls work via Bearer token
 *
 * Dev mode: No gateway, no token, all API files' getAuthHeader() returns {}
 */
import { initAuth as sdkInit, getToken as sdkGetToken, getUser as sdkGetUser, logout as sdkLogout, clearTokens as sdkClear, authFetch } from '@opc-ai/auth/client';

const AUTH_OPTIONS = {
  tokenKey: 'forge_access_token',
  refreshKey: 'forge_refresh_token',
};

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

/** Call on page load — captures _token from URL if present */
export function initAuth(): boolean {
  return sdkInit(AUTH_OPTIONS);
}

export async function login(): Promise<void> {
  const enabled = await checkAuthEnabled();
  if (!enabled) return;
  window.location.href = "/";
}

export async function logout(): Promise<void> {
  sdkLogout();
}

export function getToken(): string | null {
  return sdkGetToken();
}

export function isAuthenticated(): boolean {
  if (typeof window === 'undefined') return true;
  return !!sdkGetToken() || !_authEnabled;
}

export function clearTokens(): void {
  sdkClear();
}

export async function initTokenRefresh(): Promise<void> {}

export async function refreshToken(): Promise<boolean> {
  return true;
}

export function getRedirectAfterLogin(): string {
  return "/";
}

export function getAuthHeaders(): Record<string, string> {
  const token = sdkGetToken();
  if (!token) return {};
  return { Authorization: `Bearer ${token}` };
}

export async function handleCallback(_code: string): Promise<boolean> {
  return true;
}

export { authFetch };
