/**
 * API Interceptor for Forge Platform
 *
 * Features:
 * 1. Automatic 401/403 handling - redirect to login
 * 2. Request/Response logging
 * 3. Token refresh on 401
 * 4. Unified error handling
 */

import { getToken, refreshLocalToken, clearTokens, getAuthProvider, isAuthenticated } from "./sso-client";

// Configuration
const LOGIN_PATH = "/login";
const API_URL = process.env.NEXT_PUBLIC_API_URL || "http://localhost:9443";

// Error codes that should redirect to login
const AUTH_ERROR_CODES = [401, 403];

// Paths that don't require authentication
const PUBLIC_PATHS = [
  "/api/auth/login",
  "/api/auth/register",
  "/api/auth/refresh",
  "/api/auth/oauth/",
  "/actuator/health",
];

// ==================== Type Definitions ====================

export interface ApiResponse<T = unknown> {
  success: boolean;
  data?: T;
  message?: string;
  error?: string;
}

export interface ApiError {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

// ==================== Interceptor Setup ====================

let isRefreshing = false;
let refreshSubscribers: Array<(token: string) => void> = [];

function subscribeTokenRefresh(callback: (token: string) => void): void {
  refreshSubscribers.push(callback);
}

function onTokenRefreshed(token: string): void {
  refreshSubscribers.forEach((callback) => callback(token));
  refreshSubscribers = [];
}

async function doRefreshToken(): Promise<boolean> {
  const provider = getAuthProvider();
  if (provider === "local" || provider === "github") {
    return await refreshLocalToken();
  }
  // Keycloak handles refresh separately
  return false;
}

// ==================== Helper Functions ====================

function isPublicPath(path: string): boolean {
  return PUBLIC_PATHS.some((publicPath) => path.includes(publicPath));
}

function shouldRedirectToLogin(path: string): boolean {
  // Skip redirect for public paths
  if (isPublicPath(path)) {
    return false;
  }

  // Skip redirect for login page itself
  if (path === LOGIN_PATH || path.startsWith(LOGIN_PATH)) {
    return false;
  }

  return true;
}

function redirectToLogin(returnTo?: string): void {
  // Clear stale tokens
  clearTokens();

  // Store return URL
  const returnUrl = returnTo || window.location.pathname + window.location.search;
  sessionStorage.setItem("forge_redirect_after_login", returnUrl);

  // Redirect to login
  window.location.href = LOGIN_PATH;
}

function getErrorMessage(error: unknown): string {
  if (typeof error === "string") return error;
  if (error instanceof Error) return error.message;
  if (typeof error === "object" && error !== null) {
    const err = error as Record<string, unknown>;
    if (err.message) return String(err.message);
    if (err.error) return String(err.error);
  }
  return "An unknown error occurred";
}

// ==================== Main Interceptor ====================

/**
 * Wrapped fetch function with automatic authentication handling
 *
 * @param input - Request URL or RequestInit
 * @param init - RequestInit (optional)
 * @returns Promise<Response>
 */
export async function apiFetch<T = unknown>(
  input: string | URL | Request,
  init?: RequestInit
): Promise<ApiResponse<T>> {
  const url = typeof input === "string" ? input : input.toString();
  const method = init?.method || "GET";
  const isJson = init?.body && typeof init.body === "string";

  // Build headers with auth token
  const headers = new Headers(init?.headers || {});

  // Add auth token if available
  if (!isPublicPath(url)) {
    const token = getToken();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    // Add JSON content type if sending JSON
    if (isJson && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }
  }

  // Create request with enhanced headers
  const requestInit: RequestInit = {
    ...init,
    headers,
    credentials: "same-origin",
  };

  try {
    // Make the request
    const response = await fetch(url, requestInit);

    // Handle authentication errors
    if (AUTH_ERROR_CODES.includes(response.status)) {
      // Check if we should redirect to login
      if (shouldRedirectToLogin(url)) {
        // Try to refresh token
        if (!isRefreshing && isAuthenticated()) {
          isRefreshing = true;

          try {
            const refreshed = await doRefreshToken();
            isRefreshing = false;

            if (refreshed) {
              // Retry the request with new token
              const newToken = getToken();
              if (newToken) {
                headers.set("Authorization", `Bearer ${newToken}`);
                const retryResponse = await fetch(url, { ...requestInit, headers });

                if (retryResponse.ok) {
                  const data = await retryResponse.json();
                  return { success: true, ...data };
                }
              }
            }
          } catch {
            isRefreshing = false;
          }
        }

        // Redirect to login
        redirectToLogin();
        return { success: false, error: "Session expired, redirecting to login..." };
      }
    }

    // Handle other errors
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      return {
        success: false,
        error: getErrorMessage(errorData.message || errorData.error || `HTTP ${response.status}`),
      };
    }

    // Parse successful response
    const contentType = response.headers.get("content-type");
    if (contentType?.includes("application/json")) {
      const data = await response.json();
      return { success: true, ...data };
    }

    // Non-JSON response (like file download)
    return { success: true, data: response as T };
  } catch (error) {
    // Network errors
    console.error("API Request failed:", error);
    return {
      success: false,
      error: getErrorMessage(error) || "Network error, please check your connection",
    };
  }
}

// ==================== Convenience Methods ====================

export const api = {
  get: <T = unknown>(url: string) => apiFetch<T>(url),
  post: <T = unknown>(url: string, body?: Record<string, unknown>) =>
    apiFetch<T>(url, {
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
    }),
  put: <T = unknown>(url: string, body?: Record<string, unknown>) =>
    apiFetch<T>(url, {
      method: "PUT",
      body: body ? JSON.stringify(body) : undefined,
    }),
  patch: <T = unknown>(url: string, body?: Record<string, unknown>) =>
    apiFetch<T>(url, {
      method: "PATCH",
      body: body ? JSON.stringify(body) : undefined,
    }),
  delete: <T = unknown>(url: string) => apiFetch<T>(url, { method: "DELETE" }),
};

// ==================== Response Handler ====================

/**
 * Handle API response with consistent error handling
 *
 * @param response - API response
 * @param options - Handler options
 * @returns Data on success, or throws error on failure
 */
export async function handleApiResponse<T>(
  response: ApiResponse<T>,
  options?: {
    showError?: boolean;
    errorTitle?: string;
    onAuthError?: () => void;
  }
): Promise<T> {
  if (!response.success) {
    // Handle auth errors
    if (response.error?.includes("401") || response.error?.includes("Session expired")) {
      options?.onAuthError?.();
      redirectToLogin();
      throw new Error("Session expired");
    }

    // Show error toast if requested
    if (options?.showError !== false) {
      console.error(`[API Error] ${options?.errorTitle || "Request failed"}:`, response.error);
    }

    throw new Error(response.error || "Request failed");
  }

  if (!response.data) {
    throw new Error("No data returned");
  }

  return response.data;
}

// ==================== Error Boundary Integration ====================

/**
 * Check if error is from API
 */
export function isApiError(error: unknown): boolean {
  return (
    error instanceof Error &&
    (error.message.includes("API Error") ||
      error.message.includes("Session expired") ||
      error.message.includes("Network error"))
  );
}

/**
 * Get user-friendly error message from API error
 */
export function getApiErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    const message = error.message;
    if (message.includes("Session expired")) {
      return "Your session has expired. Please log in again.";
    }
    if (message.includes("401")) {
      return "You are not authorized to perform this action.";
    }
    if (message.includes("403")) {
      return "Access denied. You don't have permission to access this resource.";
    }
    if (message.includes("Network error")) {
      return "Unable to connect to the server. Please check your connection.";
    }
    return message;
  }
  return "An unexpected error occurred";
}

// ==================== Request Logging ====================

interface LogEntry {
  timestamp: string;
  method: string;
  url: string;
  status?: number;
  duration?: number;
  error?: string;
}

const requestLog: LogEntry[] = [];
const MAX_LOG_SIZE = 100;

export function logApiRequest(
  method: string,
  url: string,
  status?: number,
  duration?: number,
  error?: string
): void {
  const entry: LogEntry = {
    timestamp: new Date().toISOString(),
    method,
    url: url.replace(API_URL, ""), // Shorten URL for readability
    status,
    duration,
    error,
  };

  requestLog.unshift(entry);
  if (requestLog.length > MAX_LOG_SIZE) {
    requestLog.pop();
  }

  // Log in development
  if (process.env.NODE_ENV === "development") {
    const emoji = status && status >= 400 ? "❌" : status && status >= 300 ? "➡️" : "✅";
    console.log(
      `[API] ${emoji} ${method} ${entry.url} ${status ? `(${status})` : ""} ${duration ? `${duration}ms` : ""}`
    );
  }
}

// Get request log for debugging
export function getRequestLog(): LogEntry[] {
  return [...requestLog];
}

// Clear request log
export function clearRequestLog(): void {
  requestLog.length = 0;
}

// ==================== Query Client Integration ====================

/**
 * Create error handler for React Query
 */
export function createQueryErrorHandler(onAuthError?: () => void) {
  return (error: unknown) => {
    console.error("Query error:", error);

    if (
      error instanceof Error &&
      (error.message.includes("401") ||
        error.message.includes("Session expired") ||
        error.message.includes("Authentication failed"))
    ) {
      if (onAuthError) {
        onAuthError();
      } else {
        redirectToLogin();
      }
    }

    throw error;
  };
}

// ==================== Axios Interceptor (Optional) ====================

// If using axios instead of fetch, here's the interceptor setup:
//
// import axios from 'axios';
//
// const apiClient = axios.create({
//   baseURL: API_URL,
// });
//
// apiClient.interceptors.response.use(
//   (response) => response,
//   async (error) => {
//     const originalRequest = error.config;
//
//     if (error.response?.status === 401 && !originalRequest._retry) {
//       originalRequest._retry = true;
//
//       try {
//         await refreshLocalToken();
//         originalRequest.headers.Authorization = `Bearer ${getToken()}`;
//         return apiClient(originalRequest);
//       } catch {
//         redirectToLogin();
//       }
//     }
//
//     return Promise.reject(error);
//   }
// );
//
// export { apiClient };