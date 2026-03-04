import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { auth } from "@/auth";

// In Next.js 15 standalone mode, NextResponse.rewrite() with an absolute URL
// triggers an internal HTTP proxy request. When next-intl's createIntlMiddleware
// does this rewrite to inject locale context, it proxies to itself → ECONNRESET loop.
// Fix: handle locale routing with NextResponse.redirect() (browser-side) instead.
// next-intl's getRequestConfig reads locale from requestLocale (URL params), so
// the intl middleware rewrite is not needed for i18n to work in App Router.

const locales = ["zh", "en"];
const defaultLocale = "zh";

// Auth basePath: /console/api/auth in production, /api/auth in trial (empty basePath)
const authBasePath = process.env.NEXTAUTH_BASE_PATH || "/api/auth";

export default auth((req: NextRequest & { auth: unknown }) => {
  const { pathname } = req.nextUrl;

  // API routes: skip auth check (handled server-side)
  // Also skip auth API routes (they handle auth themselves)
  if (pathname.startsWith("/api/")) {
    return NextResponse.next();
  }

  // Redirect unauthenticated users to sign-in.
  // Also redirect when the refresh token has expired (RefreshAccessTokenError) —
  // the session cookie is still present but the access token can no longer be
  // renewed, so the user needs to log in again.
  const session = req.auth as { error?: string } | null;
  if (!session || session.error === "RefreshAccessTokenError") {
    return NextResponse.redirect(new URL(`${authBasePath}/signin`, req.url));
  }

  // Add locale prefix via redirect (NOT rewrite — avoids Next.js internal proxy loop)
  const hasLocale = locales.some(
    (l) => pathname === `/${l}` || pathname.startsWith(`/${l}/`)
  );
  if (!hasLocale) {
    const url = req.nextUrl.clone();
    url.pathname = `/${defaultLocale}${pathname}`;
    return NextResponse.redirect(url);
  }

  return NextResponse.next();
});

export const config = {
  matcher: ["/((?!_next|.*\\...*).*)"],
};
