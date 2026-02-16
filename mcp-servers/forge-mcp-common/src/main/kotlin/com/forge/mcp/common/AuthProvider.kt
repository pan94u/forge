package com.forge.mcp.common

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Result of an authentication/authorization check.
 */
data class AuthResult(
    val valid: Boolean,
    val userId: String,
    val roles: List<String>
) {
    companion object {
        fun unauthorized(): AuthResult = AuthResult(
            valid = false,
            userId = "",
            roles = emptyList()
        )
    }
}

/**
 * Interface for providers that validate Bearer tokens.
 */
interface AuthProvider {
    /**
     * Validates the given Bearer token and returns an [AuthResult].
     * Implementations must not throw; they should return [AuthResult.unauthorized] on failure.
     */
    suspend fun validateToken(token: String): AuthResult
}

/**
 * OAuth2 token introspection response as defined in RFC 7662.
 */
@Serializable
private data class IntrospectionResponse(
    val active: Boolean,
    val sub: String? = null,
    @SerialName("username")
    val username: String? = null,
    val scope: String? = null,
    @SerialName("client_id")
    val clientId: String? = null,
    @SerialName("realm_access")
    val realmAccess: RealmAccess? = null
)

@Serializable
private data class RealmAccess(
    val roles: List<String> = emptyList()
)

/**
 * Configuration for the OAuth2 introspection-based auth provider.
 * Values are sourced from environment variables with sensible defaults
 * for local development.
 */
data class OAuthConfig(
    val introspectionUrl: String,
    val clientId: String,
    val clientSecret: String
) {
    companion object {
        /**
         * Reads OAuth configuration from environment variables.
         *
         * Required environment variables:
         * - FORGE_OAUTH_INTROSPECTION_URL
         * - FORGE_OAUTH_CLIENT_ID
         * - FORGE_OAUTH_CLIENT_SECRET
         */
        fun fromEnvironment(): OAuthConfig {
            val introspectionUrl = System.getenv("FORGE_OAUTH_INTROSPECTION_URL")
                ?: error(
                    "FORGE_OAUTH_INTROSPECTION_URL environment variable is required. " +
                        "Set it to your OAuth2 token introspection endpoint (e.g. https://auth.example.com/oauth2/introspect)."
                )
            val clientId = System.getenv("FORGE_OAUTH_CLIENT_ID")
                ?: error("FORGE_OAUTH_CLIENT_ID environment variable is required.")
            val clientSecret = System.getenv("FORGE_OAUTH_CLIENT_SECRET")
                ?: error("FORGE_OAUTH_CLIENT_SECRET environment variable is required.")

            return OAuthConfig(
                introspectionUrl = introspectionUrl,
                clientId = clientId,
                clientSecret = clientSecret
            )
        }
    }
}

/**
 * Validates Bearer tokens against an OAuth2 introspection endpoint (RFC 7662).
 *
 * This provider sends the token to the configured introspection endpoint along
 * with client credentials. The introspection endpoint returns whether the token
 * is active and associated user/role information.
 */
class OAuthAuthProvider(
    private val config: OAuthConfig,
    private val httpClient: HttpClient = createDefaultClient()
) : AuthProvider {

    private val logger = LoggerFactory.getLogger(OAuthAuthProvider::class.java)

    override suspend fun validateToken(token: String): AuthResult {
        return try {
            val response: IntrospectionResponse = httpClient.submitForm(
                url = config.introspectionUrl,
                formParameters = parameters {
                    append("token", token)
                    append("token_type_hint", "access_token")
                }
            ) {
                basicAuth(config.clientId, config.clientSecret)
            }.body()

            if (!response.active) {
                logger.debug("Token introspection returned inactive for token (redacted)")
                return AuthResult.unauthorized()
            }

            val userId = response.sub
                ?: response.username
                ?: response.clientId
                ?: "unknown"

            val roles = response.realmAccess?.roles
                ?: parseRolesFromScope(response.scope)

            AuthResult(
                valid = true,
                userId = userId,
                roles = roles
            )
        } catch (e: Exception) {
            logger.error("Token introspection failed: {}", e.message, e)
            AuthResult.unauthorized()
        }
    }

    /**
     * Parses space-delimited scope string into a list of role names.
     */
    private fun parseRolesFromScope(scope: String?): List<String> {
        if (scope.isNullOrBlank()) return emptyList()
        return scope.split(" ").filter { it.isNotBlank() }
    }

    companion object {
        private fun createDefaultClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            engine {
                requestTimeout = 5_000
            }
        }
    }
}
