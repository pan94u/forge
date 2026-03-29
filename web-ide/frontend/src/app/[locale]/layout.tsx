"use client";

import React, { useState, useEffect } from "react";
import { NextIntlClientProvider } from "next-intl";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Header } from "@/components/common/Header";
import { Sidebar } from "@/components/common/Sidebar";
// Auth is handled by gateway (production) or disabled (dev). See lib/auth.ts.

function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 60 * 1000,
        retry: 1,
      },
    },
  });
}

let browserQueryClient: QueryClient | undefined;

function getQueryClient(): QueryClient {
  if (typeof window === "undefined") {
    return makeQueryClient();
  }
  if (!browserQueryClient) {
    browserQueryClient = makeQueryClient();
  }
  return browserQueryClient;
}

// Pages that skip auth check
const publicPaths = ["/login"];

function isPublicPath(path: string): boolean {
  return publicPaths.some((p) => path === p || path.endsWith(p));
}

export default function LocaleLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ locale: string }>;
}) {
  const queryClient = getQueryClient();
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [role, setRole] = useState<"developer" | "product">("developer");
  const [authChecked, setAuthChecked] = useState(false);
  const [isPublic, setIsPublic] = useState(false);
  const [messages, setMessages] = useState<Record<string, unknown> | null>(null);
  const [locale, setLocale] = useState<string>("zh");

  useEffect(() => {
    params.then(({ locale: l }) => {
      setLocale(l);
      import(`../../../messages/${l}.json`).then((mod) => {
        setMessages(mod.default);
      });
    });
  }, [params]);

  useEffect(() => {
    const path = window.location.pathname;
    const pub = isPublicPath(path);
    setIsPublic(pub);

    if (!pub) {
      // Auth check: call /api/auth/me
      // - Dev mode: returns {authenticated: true, username: "dev"} -> pass
      // - Production: gateway ensures user is logged in before request reaches here
      fetch("/api/auth/me")
        .then((res) => res.json())
        .then((data) => {
          if (data.authenticated) {
            setAuthChecked(true);
          } else {
            // Production: gateway should have redirected, but fallback
            window.location.href = "/login";
          }
        })
        .catch(() => setAuthChecked(true)); // Network error in dev -> allow
    } else {
      setAuthChecked(true);
    }
  }, []);

  if (!messages) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    );
  }

  // Public pages (login, callback) render without shell
  if (isPublic) {
    return (
      <NextIntlClientProvider locale={locale} messages={messages}>
        <QueryClientProvider client={queryClient}>
          {children}
        </QueryClientProvider>
      </NextIntlClientProvider>
    );
  }

  // Wait for auth check
  if (!authChecked) {
    return (
      <NextIntlClientProvider locale={locale} messages={messages}>
        <div className="flex h-screen items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      </NextIntlClientProvider>
    );
  }

  return (
    <NextIntlClientProvider locale={locale} messages={messages}>
      <QueryClientProvider client={queryClient}>
        <div className="flex h-screen flex-col overflow-hidden">
          <Header role={role} onRoleChange={setRole} />
          <div className="flex flex-1 overflow-hidden">
            <Sidebar
              collapsed={sidebarCollapsed}
              onToggleCollapse={() => setSidebarCollapsed(!sidebarCollapsed)}
              role={role}
            />
            <main className="flex-1 overflow-auto">{children}</main>
          </div>
        </div>
      </QueryClientProvider>
    </NextIntlClientProvider>
  );
}
