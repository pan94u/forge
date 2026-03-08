import { NextResponse } from "next/server";
import type { NextRequest } from "next/server";
import { auth } from "@/auth";

const locales = ["zh", "en"];
const defaultLocale = "zh";

// Build-time variable inlined by Next.js: "/console" in production, "" in dev.
const BASE_PATH = process.env.NEXT_PUBLIC_BASE_PATH || "";

export default auth((req: NextRequest & { auth: unknown }) => {
  // req.nextUrl.pathname in middleware INCLUDES basePath (Next.js behavior).
  // Strip it so route matching works consistently.
  const rawPathname = req.nextUrl.pathname;
  const pathname =
    BASE_PATH && rawPathname.startsWith(BASE_PATH)
      ? rawPathname.slice(BASE_PATH.length) || "/"
      : rawPathname;

  // API routes: skip auth check (handled server-side / auth.js route handler)
  if (pathname.startsWith("/api/")) {
    return NextResponse.next();
  }

  // Redirect unauthenticated users to sign-in.
  const session = req.auth as { error?: string } | null;
  if (!session || session.error === "RefreshAccessTokenError") {
    const signInUrl = new URL(`${BASE_PATH}/api/auth/signin`, req.url);
    signInUrl.searchParams.set("callbackUrl", req.url);
    return NextResponse.redirect(signInUrl);
  }

  // Add locale prefix via redirect (NOT rewrite — avoids Next.js internal proxy loop)
  const hasLocale = locales.some(
    (l) => pathname === `/${l}` || pathname.startsWith(`/${l}/`)
  );
  if (!hasLocale) {
    const url = new URL(
      `${BASE_PATH}/${defaultLocale}${pathname}`,
      req.url
    );
    return NextResponse.redirect(url);
  }

  return NextResponse.next();
});

export const config = {
  matcher: ["/((?!_next|.*\\...*).*)"],
};
