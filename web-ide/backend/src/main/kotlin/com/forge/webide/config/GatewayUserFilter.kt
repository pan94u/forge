package com.forge.webide.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Instant

/**
 * Reads X-User-* headers injected by synapse/gateway and creates
 * a synthetic Jwt-compatible Authentication in SecurityContext.
 *
 * This allows existing RbacHelper (which expects Jwt) to work unchanged.
 * When no headers present (local dev, no gateway), filter is a no-op.
 */
class GatewayUserFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val userId = request.getHeader("X-User-Id")

        if (userId != null && userId.isNotBlank() && SecurityContextHolder.getContext().authentication == null) {
            val userName = request.getHeader("X-User-Name") ?: ""
            val userRoles = request.getHeader("X-User-Roles")?.split(",") ?: emptyList()
            val userOrg = request.getHeader("X-User-Org") ?: ""

            // Build a synthetic JWT that RbacHelper can read
            val claims = mapOf<String, Any>(
                "sub" to userId,
                "preferred_username" to userName,
                "name" to userName,
                "realm_roles" to userRoles,
                "org" to userOrg,
            )
            val headers = mapOf("alg" to "none")
            val jwt = Jwt("gateway-token", Instant.now(), Instant.now().plusSeconds(3600), headers, claims)

            val authorities = userRoles.map { SimpleGrantedAuthority("ROLE_$it") }
            val auth = GatewayAuthentication(jwt, authorities)
            SecurityContextHolder.getContext().authentication = auth
        }

        filterChain.doFilter(request, response)
    }
}

class GatewayAuthentication(
    private val jwt: Jwt,
    authorities: Collection<SimpleGrantedAuthority>
) : AbstractAuthenticationToken(authorities) {
    init { isAuthenticated = true }
    override fun getCredentials(): Any = ""
    override fun getPrincipal(): Any = jwt
    override fun getName(): String = jwt.subject
}
