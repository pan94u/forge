"use client";

import { useState } from "react";
import {
  loginWithLocal,
  loginWithKeycloak,
  loginWithGithub,
  loginAsGuest,
  registerLocal,
  isAuthenticated,
  getRedirectAfterLogin,
} from "@/lib/sso-client";

export function LoginForm() {
  const [mode, setMode] = useState<"main" | "login" | "register" | "guest">("main");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");
  const [guestEmail, setGuestEmail] = useState("");
  const [guestName, setGuestName] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      let result;
      if (mode === "login") {
        result = await loginWithLocal(username, password);
      } else {
        result = await registerLocal(username, password, email || undefined);
      }

      if (result.success && result.data) {
        window.location.href = getRedirectAfterLogin();
      } else {
        setError(result.error || "Authentication failed");
      }
    } catch (err) {
      setError("An unexpected error occurred");
    } finally {
      setLoading(false);
    }
  };

  const handleGuestLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      const result = await loginAsGuest(guestEmail || undefined, undefined, guestName || undefined);

      if (result.success && result.data) {
        window.location.href = getRedirectAfterLogin();
      } else {
        setError(result.error || "Guest login failed");
      }
    } catch (err) {
      setError("An unexpected error occurred");
    } finally {
      setLoading(false);
    }
  };

  const handleKeycloakLogin = () => {
    loginWithKeycloak();
  };

  const handleGithubLogin = () => {
    loginWithGithub();
  };

  if (isAuthenticated()) {
    window.location.href = getRedirectAfterLogin();
    return null;
  }

  // Guest Login Form - Dark Theme
  if (mode === "guest") {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[hsl(222,47%,11%)]">
        <div className="max-w-md w-full space-y-8 p-8 bg-[hsl(217,33%,17%)] rounded-xl border border-[hsl(215,27%,25%)] shadow-xl">
          <div className="text-center">
            <h2 className="text-2xl font-bold text-white">
              Quick Guest Access
            </h2>
            <p className="mt-2 text-sm text-gray-400">
              Enter your email to continue as a guest. If the account doesn&apos;t exist, it will be created automatically.
            </p>
          </div>

          <form className="mt-8 space-y-6" onSubmit={handleGuestLogin}>
            <div>
              <label htmlFor="guestEmail" className="block text-sm font-medium text-gray-300">
                Email (optional)
              </label>
              <input
                id="guestEmail"
                name="guestEmail"
                type="email"
                autoComplete="email"
                value={guestEmail}
                onChange={(e) => setGuestEmail(e.target.value)}
                className="mt-1 block w-full px-3 py-2 bg-[hsl(215,27%,25%)] border border-gray-600 rounded-md text-white placeholder-gray-500 focus:ring-blue-500 focus:border-blue-500"
                placeholder="your@email.com"
              />
            </div>

            <div>
              <label htmlFor="guestName" className="block text-sm font-medium text-gray-300">
                Display Name (optional)
              </label>
              <input
                id="guestName"
                name="guestName"
                type="text"
                value={guestName}
                onChange={(e) => setGuestName(e.target.value)}
                className="mt-1 block w-full px-3 py-2 bg-[hsl(215,27%,25%)] border border-gray-600 rounded-md text-white placeholder-gray-500 focus:ring-blue-500 focus:border-blue-500"
                placeholder="Your name"
              />
            </div>

            {error && (
              <div className="text-red-400 text-sm text-center">{error}</div>
            )}

            <div className="flex space-x-4">
              <button
                type="button"
                onClick={() => {
                  setMode("main");
                  setError("");
                }}
                className="flex-1 flex justify-center py-3 px-4 border border-gray-600 rounded-md text-sm font-medium text-gray-300 bg-[hsl(215,27%,25%)] hover:bg-[hsl(217,33%,20%)] focus:outline-none"
              >
                Back
              </button>
              <button
                type="submit"
                disabled={loading}
                className="flex-1 flex justify-center py-3 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
              >
                {loading ? "Please wait..." : "Continue as Guest"}
              </button>
            </div>
          </form>

          <div className="text-center">
            <p className="text-xs text-gray-500">
              Guest accounts have limited access and will be identified by your email.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // Main Login/Register Form - Dark Theme
  return (
    <div className="min-h-screen flex items-center justify-center bg-[hsl(222,47%,11%)]">
      <div className="max-w-md w-full space-y-8 p-8 bg-[hsl(217,33%,17%)] rounded-xl border border-[hsl(215,27%,25%)] shadow-xl">
        <div className="text-center">
          <h2 className="text-3xl font-bold text-white">
            {mode === "login" ? "Sign in to Forge" : "Create your account"}
          </h2>
          <p className="mt-2 text-sm text-gray-400">
            {mode === "login"
              ? "Choose your preferred login method"
              : "Get started with Forge Platform"}
          </p>
        </div>

        {/* SSO Buttons */}
        <div className="space-y-3">
          <button
            onClick={handleKeycloakLogin}
            className="w-full flex items-center justify-center px-4 py-3 border border-gray-600 rounded-lg shadow-sm bg-[hsl(215,27%,25%)] text-sm font-medium text-gray-200 hover:bg-[hsl(217,33%,20%)] transition-colors"
          >
            <svg className="w-5 h-5 mr-3" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z" />
              <circle cx="12" cy="12" r="5" />
            </svg>
            Continue with SSO (Keycloak)
          </button>

          <button
            onClick={handleGithubLogin}
            className="w-full flex items-center justify-center px-4 py-3 border border-gray-600 rounded-lg shadow-sm bg-[hsl(215,27%,25%)] text-sm font-medium text-white hover:bg-[hsl(217,33%,20%)] transition-colors"
          >
            <svg className="w-5 h-5 mr-3" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z" />
            </svg>
            Continue with GitHub
          </button>
        </div>

        {/* Divider */}
        <div className="relative">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-gray-600" />
          </div>
          <div className="relative flex justify-center text-sm">
            <span className="px-2 bg-[hsl(217,33%,17%)] text-gray-400">Or</span>
          </div>
        </div>

        {/* Guest Login Button */}
        <button
          type="button"
          onClick={() => setMode("guest")}
          className="w-full flex items-center justify-center px-4 py-3 border border-green-600 rounded-lg shadow-sm bg-[hsl(145,63%,20%)] text-sm font-medium text-green-300 hover:bg-[hsl(145,63%,25%)] transition-colors"
        >
          <svg className="w-5 h-5 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
          </svg>
          Continue as Guest
        </button>

        {/* Another Divider */}
        <div className="relative">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-gray-600" />
          </div>
          <div className="relative flex justify-center text-sm">
            <span className="px-2 bg-[hsl(217,33%,17%)] text-gray-400">Or with email</span>
          </div>
        </div>

        {/* Email/Password Form */}
        <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
          {mode === "register" && (
            <div>
              <label htmlFor="email" className="block text-sm font-medium text-gray-300">
                Email (optional)
              </label>
              <input
                id="email"
                name="email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-1 block w-full px-3 py-2 bg-[hsl(215,27%,25%)] border border-gray-600 rounded-md text-white placeholder-gray-500 focus:ring-blue-500 focus:border-blue-500"
                placeholder="your@email.com"
              />
            </div>
          )}

          <div>
            <label htmlFor="username" className="block text-sm font-medium text-gray-300">
              Username
            </label>
            <input
              id="username"
              name="username"
              type="text"
              autoComplete="username"
              required={mode !== "guest"}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="mt-1 block w-full px-3 py-2 bg-[hsl(215,27%,25%)] border border-gray-600 rounded-md text-white placeholder-gray-500 focus:ring-blue-500 focus:border-blue-500"
              placeholder="your_username"
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-300">
              Password
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete={mode === "login" ? "current-password" : "new-password"}
              required={mode !== "guest"}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 block w-full px-3 py-2 bg-[hsl(215,27%,25%)] border border-gray-600 rounded-md text-white placeholder-gray-500 focus:ring-blue-500 focus:border-blue-500"
              placeholder="********"
            />
          </div>

          {error && (
            <div className="text-red-400 text-sm text-center">{error}</div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full flex justify-center py-3 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50"
          >
            {loading
              ? "Please wait..."
              : mode === "login"
              ? "Sign in"
              : "Create account"}
          </button>
        </form>

        {/* Toggle Login/Register */}
        <div className="text-center">
          <button
            type="button"
            onClick={() => {
              setMode(mode === "login" ? "register" : "login");
              setError("");
            }}
            className="text-sm text-blue-400 hover:text-blue-300"
          >
            {mode === "login"
              ? "Don't have an account? Sign up"
              : "Already have an account? Sign in"}
          </button>
        </div>
      </div>
    </div>
  );
}