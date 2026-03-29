package com.forge.webide.controller

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController {

    @Value("\${forge.security.enabled:false}")
    private var securityEnabled: Boolean = false

    /**
     * SSO configuration for the frontend.
     * When security is disabled (local dev), returns enabled=false so frontend skips auth.
     * When security is enabled (production), returns Console login URL.
     */
    @GetMapping("/sso-config")
    fun ssoConfig(): Map<String, Any> {
        if (!securityEnabled) {
            return mapOf("enabled" to false)
        }
        return mapOf(
            "enabled" to true,
            "loginUrl" to "https://auth.synapse.gold/login",
            "provider" to "synapse-console"
        )
    }

    /**
     * Current user info — reads from X-User-* headers (injected by Gateway).
     * When no headers present (local dev), returns anonymous.
     */
    @GetMapping("/me")
    fun me(request: HttpServletRequest): Map<String, Any?> {
        val userId = request.getHeader("X-User-Id")

        if (userId.isNullOrBlank()) {
            return mapOf(
                "authenticated" to !securityEnabled,  // dev mode = treat as authenticated
                "username" to "dev",
                "email" to null,
                "roles" to listOf("admin"),
                "org" to "dev"
            )
        }

        return mapOf(
            "authenticated" to true,
            "username" to (request.getHeader("X-User-Name") ?: userId),
            "email" to request.getHeader("X-User-Email"),
            "roles" to (request.getHeader("X-User-Roles")?.split(",") ?: emptyList()),
            "org" to request.getHeader("X-User-Org"),
            "sub" to userId
        )
    }
}
