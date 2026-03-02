"use client";

import React, { useState, useEffect } from "react";
import { NextIntlClientProvider } from "next-intl";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Header } from "@/components/common/Header";
import { Sidebar } from "@/components/common/Sidebar";
import { getToken, refreshToken, initTokenRefresh, clearTokens } from "@/lib/auth";

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

// Pages that don't require authentication
const publicPaths = ["/login", "/auth/callback"];

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
      // Restore the proactive refresh schedule after page reload
      initTokenRefresh();

      const checkAuth = async (retried = false): Promise<void> => {
        const token = getToken();
        try {
          const res = await fetch("/api/auth/me", {
            headers: token ? { Authorization: `Bearer ${token}` } : {},
          });

          if (res.status === 401 && !retried) {
            // Token may be expired — try to refresh once before giving up
            const refreshed = await refreshToken();
            if (refreshed) {
              return checkAuth(true);
            }
            clearTokens();
            window.location.href = "/login";
            return;
          }

          if (res.status === 401 || !res.ok) {
            clearTokens();
            window.location.href = "/login";
            return;
          }

          const data = await res.json();
          if (!data.authenticated) {
            clearTokens();
            window.location.href = "/login";
          } else {
            setAuthChecked(true);
          }
        } catch {
          window.location.href = "/login";
        }
      };

      checkAuth();
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
