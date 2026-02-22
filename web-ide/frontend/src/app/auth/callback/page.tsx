"use client";

import React, { useEffect, useState } from "react";
import {
  handleKeycloakCallback,
  handleGithubCallback,
  getRedirectAfterLogin,
} from "@/lib/sso-client";

export default function AuthCallbackPage() {
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState("Processing authentication...");

  useEffect(() => {
    async function processCallback() {
      const params = new URLSearchParams(window.location.search);
      const code = params.get("code");
      const state = params.get("state");
      const errorParam = params.get("error");

      if (errorParam) {
        setError(`Authentication error: ${errorParam}`);
        return;
      }

      if (!code) {
        setError("No authorization code received");
        return;
      }

      // Check if this is a GitHub callback
      const url = window.location.href;
      if (url.includes("/auth/callback/github")) {
        // GitHub OAuth callback
        setStatus("Completing GitHub sign in...");
        if (!state) {
          setError("No state parameter received");
          return;
        }
        const result = await handleGithubCallback(code, state);
        if (result.success) {
          window.location.href = getRedirectAfterLogin();
        } else {
          setError(result.error || "GitHub authentication failed");
        }
      } else {
        // Keycloak OAuth callback
        setStatus("Completing SSO sign in...");
        const result = await handleKeycloakCallback(code);
        if (result.success) {
          window.location.href = getRedirectAfterLogin();
        } else {
          setError(result.error || "SSO authentication failed");
        }
      }
    }

    processCallback();
  }, []);

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-background">
        <div className="text-center space-y-4">
          <p className="text-destructive">{error}</p>
          <a
            href="/login"
            className="text-sm text-primary hover:underline"
          >
            Back to login
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-background">
      <div className="text-center space-y-2">
        <div className="h-8 w-8 mx-auto animate-spin rounded-full border-2 border-primary border-t-transparent" />
        <p className="text-sm text-muted-foreground">{status}</p>
      </div>
    </div>
  );
}
