"use client";

import React, { useEffect } from "react";
import { Anvil } from "lucide-react";
import {
  loginWithKeycloak,
  loginWithGithub,
  loginWithLocal,
  registerLocal,
  isAuthenticated,
  getRedirectAfterLogin,
  getAuthHeaders,
} from "@/lib/sso-client";
import { LoginForm } from "@/components/auth/LoginForm";

export default function LoginPage() {
  useEffect(() => {
    // If already authenticated, redirect to home
    if (isAuthenticated()) {
      window.location.href = getRedirectAfterLogin();
    }
  }, []);

  return (
    <LoginForm />
  );
}
