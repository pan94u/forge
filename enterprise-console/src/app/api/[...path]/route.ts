import { NextRequest, NextResponse } from "next/server";
import { auth } from "@/auth";

// Use auth(handler) wrapper instead of standalone await auth().
//
// In Next.js 15, cookies() from next/headers is ASYNC. Auth.js v5 beta's
// standalone auth() relies on it synchronously → gets a Promise → session is
// null → no Authorization header is forwarded → backend returns 401.
//
// The auth(handler) wrapper reads cookies directly from req.cookies (the
// NextRequest sync API), bypassing the next/headers cookies() issue entirely.
// This is the same pattern used in middleware.ts.
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const handler = auth(async (req: NextRequest & { auth: any }) => {
  const backendUrl = process.env.BACKEND_URL ?? "http://localhost:19000";

  // Extract path from URL: /api/admin/orgs → admin/orgs
  // (params not available via ctx in the auth wrapper; derive from URL instead)
  const segments = req.nextUrl.pathname.split("/").filter(Boolean);
  // segments[0] = "api", rest = actual backend path
  const path = segments.slice(1).join("/");
  const search = req.nextUrl.search;
  const target = `${backendUrl}/api/${path}${search}`;

  const headers: Record<string, string> = {};
  req.headers.forEach((value, key) => {
    // Skip host (would confuse backend) and cookie (next-auth session JWT makes
    // the Cookie header too large for Tomcat's default 8KB limit)
    if (key === "host" || key === "cookie") return;
    headers[key] = value;
  });

  // Forward access token to backend — req.auth is set by the auth() wrapper
  const accessToken = req.auth?.accessToken as string | undefined;
  if (accessToken) {
    headers["Authorization"] = `Bearer ${accessToken}`;
  }

  const body =
    req.method === "GET" || req.method === "HEAD"
      ? undefined
      : await req.arrayBuffer();

  try {
    const res = await fetch(target, {
      method: req.method,
      headers,
      body: body ?? undefined,
    });

    const respHeaders = new Headers();
    res.headers.forEach((value, key) => {
      // Node.js fetch auto-decompresses gzip, so drop encoding/length headers
      // to avoid ERR_CONTENT_DECODING_FAILED in the browser
      if (key === "content-encoding" || key === "content-length") return;
      respHeaders.set(key, value);
    });

    return new NextResponse(res.body, {
      status: res.status,
      headers: respHeaders,
    });
  } catch (e) {
    console.error("[proxy] Backend unreachable:", target, e);
    return NextResponse.json(
      { error: "Backend unavailable", target },
      { status: 502 }
    );
  }
});

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const GET = handler as any;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const POST = handler as any;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const PUT = handler as any;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const DELETE = handler as any;
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const PATCH = handler as any;
