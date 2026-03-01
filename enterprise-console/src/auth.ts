import NextAuth from "next-auth";
import Keycloak from "next-auth/providers/keycloak";

// In Docker, the container cannot reach localhost:8180 (host machine).
// KEYCLOAK_INTERNAL_URL (e.g. http://host.docker.internal:8180/realms/forge) is used
// for server-side calls (token exchange, JWKS, userinfo).
// KEYCLOAK_ISSUER (localhost:8180) is kept for JWT iss claim validation.
const keycloakIssuer = process.env.KEYCLOAK_ISSUER!;
const keycloakInternal = process.env.KEYCLOAK_INTERNAL_URL ?? keycloakIssuer;
const useInternalUrl = keycloakInternal !== keycloakIssuer;

export const { handlers, signIn, signOut, auth } = NextAuth({
  providers: [
    Keycloak({
      clientId: process.env.KEYCLOAK_CLIENT_ID!,
      clientSecret: process.env.KEYCLOAK_CLIENT_SECRET!,
      issuer: keycloakIssuer,
      // Override server-side endpoints to use internal URL when running in Docker
      ...(useInternalUrl && {
        wellKnown: `${keycloakInternal}/.well-known/openid-configuration`,
        token: `${keycloakInternal}/protocol/openid-connect/token`,
        userinfo: `${keycloakInternal}/protocol/openid-connect/userinfo`,
        jwks_endpoint: `${keycloakInternal}/protocol/openid-connect/certs`,
      }),
    }),
  ],
  callbacks: {
    jwt({ token, account, profile }) {
      if (account) {
        token.accessToken = account.access_token;
        token.realmRoles =
          (profile as Record<string, unknown> | undefined)
            ?.realm_access &&
          typeof (profile as Record<string, unknown>).realm_access === "object"
            ? (
                (profile as Record<string, unknown>).realm_access as Record<
                  string,
                  unknown
                >
              )["roles"] ?? []
            : [];
      }
      return token;
    },
    session({ session, token }) {
      session.accessToken = token.accessToken as string;
      (session.user as { realmRoles?: string[] }).realmRoles =
        token.realmRoles as string[];
      return session;
    },
  },
});
