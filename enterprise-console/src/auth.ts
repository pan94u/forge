import NextAuth from "next-auth";
import type { OAuthConfig } from "next-auth/providers";
import type { JWT } from "next-auth/jwt";

// Auth.js OIDC discovery fetches the Keycloak discovery document, which returns
// mixed URLs: token/userinfo/jwks use host.docker.internal (reachable inside Docker),
// but end_session_endpoint and revocation_endpoint use localhost:8180 (unreachable
// inside Docker). Fix: use type:"oauth" with explicit endpoints to bypass discovery.
const keycloakIssuer = process.env.KEYCLOAK_ISSUER!;
const keycloakInternal = process.env.KEYCLOAK_INTERNAL_URL ?? keycloakIssuer;

interface KeycloakProfile {
  sub: string;
  name?: string;
  preferred_username?: string;
  email?: string;
  picture?: string;
}

const KeycloakProvider: OAuthConfig<KeycloakProfile> = {
  id: "keycloak",
  name: "Keycloak",
  type: "oauth",
  clientId: process.env.KEYCLOAK_CLIENT_ID!,
  clientSecret: process.env.KEYCLOAK_CLIENT_SECRET!,
  issuer: keycloakIssuer,
  authorization: {
    url: `${keycloakIssuer}/protocol/openid-connect/auth`,
    params: { scope: "openid email profile" },
  },
  token: `${keycloakInternal}/protocol/openid-connect/token`,
  userinfo: `${keycloakInternal}/protocol/openid-connect/userinfo`,
  checks: ["pkce", "state"],
  profile(profile) {
    return {
      id: profile.sub,
      name: profile.name ?? profile.preferred_username ?? profile.sub,
      email: profile.email ?? "",
      image: profile.picture,
    };
  },
};

/** Decode a Keycloak JWT and extract realm roles. */
function extractRoles(jwtStr: string): string[] {
  try {
    const b64 = jwtStr.split(".")[1];
    const padded =
      b64.replace(/-/g, "+").replace(/_/g, "/") +
      "=".repeat((4 - (b64.length % 4)) % 4);
    const payload = JSON.parse(
      Buffer.from(padded, "base64").toString("utf8")
    ) as { realm_roles?: string[]; realm_access?: { roles?: string[] } };
    // Keycloak mapper outputs realm_roles (top-level); fall back to realm_access.roles
    return payload.realm_roles ?? payload.realm_access?.roles ?? [];
  } catch {
    return [];
  }
}

/** Use the refresh_token to obtain a new access_token from Keycloak. */
async function refreshAccessToken(token: JWT): Promise<JWT> {
  try {
    const res = await fetch(
      `${keycloakInternal}/protocol/openid-connect/token`,
      {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          client_id: process.env.KEYCLOAK_CLIENT_ID!,
          client_secret: process.env.KEYCLOAK_CLIENT_SECRET!,
          grant_type: "refresh_token",
          refresh_token: token.refreshToken as string,
        }),
      }
    );
    const refreshed = (await res.json()) as {
      access_token?: string;
      refresh_token?: string;
      expires_in?: number;
      error?: string;
    };
    if (!res.ok || !refreshed.access_token) throw refreshed;

    return {
      ...token,
      accessToken: refreshed.access_token,
      refreshToken: refreshed.refresh_token ?? token.refreshToken,
      accessTokenExpires:
        Date.now() + (refreshed.expires_in ?? 300) * 1000,
      realmRoles: extractRoles(refreshed.access_token),
      error: undefined,
    };
  } catch (err) {
    console.error("[auth] Token refresh failed:", err);
    return { ...token, error: "RefreshAccessTokenError" };
  }
}

export const { handlers, signIn, signOut, auth } = NextAuth({
  basePath: process.env.NEXTAUTH_BASE_PATH || "/api/auth",
  providers: [KeycloakProvider],
  session: {
    // Explicit 30-day session lifetime so the cookie never expires during normal use.
    // Without this, Auth.js v5 beta may use a shorter default in some environments.
    maxAge: 30 * 24 * 60 * 60,
  },
  callbacks: {
    redirect({ url, baseUrl }) {
      // 同域 URL 直接使用（保留 callbackUrl 传入的原始路径）
      if (url.startsWith(baseUrl) || url.startsWith("/")) {
        return url;
      }
      // 跨域 URL 回退到 basePath 首页
      const basePath = process.env.NEXT_PUBLIC_BASE_PATH || "";
      return `${baseUrl}${basePath}`;
    },
    jwt({ token, account }) {
      // Initial sign-in: store tokens and metadata
      if (account) {
        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          accessTokenExpires:
            Date.now() + ((account.expires_in as number) ?? 300) * 1000,
          realmRoles: extractRoles(account.access_token as string),
          error: undefined,
        };
      }

      // Token still valid (30s buffer before expiry)
      if (Date.now() < (token.accessTokenExpires as number) - 30_000) {
        return token;
      }

      // If a previous refresh already failed, don't keep hammering Keycloak —
      // let the middleware redirect to sign-in instead.
      if (token.error === "RefreshAccessTokenError") {
        return token;
      }

      // Token expired — refresh
      return refreshAccessToken(token);
    },
    session({ session, token }) {
      session.accessToken = token.accessToken as string;
      (session.user as { realmRoles?: string[] }).realmRoles =
        token.realmRoles as string[];
      if (token.error) {
        (session as unknown as Record<string, unknown>).error = token.error;
      }
      return session;
    },
  },
});
