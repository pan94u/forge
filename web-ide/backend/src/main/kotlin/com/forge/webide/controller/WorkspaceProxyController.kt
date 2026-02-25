package com.forge.webide.controller

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.HttpURLConnection
import java.net.URI

/**
 * Reverse proxy controller that forwards HTTP requests to services running
 * inside the workspace container.
 *
 * URL pattern: /api/workspaces/{id}/proxy/{port}/path
 * forwards to localhost:{port}/path
 *
 * Security: only ports 3000-9999 are allowed.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/proxy/{port}")
class WorkspaceProxyController {

    private val logger = LoggerFactory.getLogger(WorkspaceProxyController::class.java)

    companion object {
        private const val MIN_PORT = 3000
        private const val MAX_PORT = 9999
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 30000
    }

    @RequestMapping("/**")
    fun proxyRequest(
        @PathVariable workspaceId: String,
        @PathVariable port: Int,
        request: HttpServletRequest
    ): ResponseEntity<ByteArray> {
        // Validate port range
        if (port < MIN_PORT || port > MAX_PORT) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"error":"Port must be between $MIN_PORT and $MAX_PORT"}""".toByteArray())
        }

        // Extract the remaining path after /proxy/{port}/
        val prefix = "/api/workspaces/$workspaceId/proxy/$port"
        val remainingPath = request.requestURI.removePrefix(prefix).let {
            if (it.isEmpty() || it == "/") "/" else it
        }
        val queryString = request.queryString?.let { "?$it" } ?: ""
        val targetUrl = "http://localhost:$port$remainingPath$queryString"

        logger.debug("Proxying {} {} → {}", request.method, request.requestURI, targetUrl)

        return try {
            val connection = URI(targetUrl).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = request.method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false

            // Forward relevant request headers
            val headerNames = request.headerNames
            while (headerNames.hasMoreElements()) {
                val name = headerNames.nextElement()
                if (name.equals("host", ignoreCase = true)) continue
                if (name.equals("connection", ignoreCase = true)) continue
                connection.setRequestProperty(name, request.getHeader(name))
            }

            // Forward request body for POST/PUT/PATCH
            if (request.method in listOf("POST", "PUT", "PATCH")) {
                connection.doOutput = true
                request.inputStream.use { input ->
                    connection.outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
            }

            val responseCode = connection.responseCode
            val responseBody = try {
                connection.inputStream.use { it.readBytes() }
            } catch (_: Exception) {
                connection.errorStream?.use { it.readBytes() } ?: ByteArray(0)
            }

            // Build response with forwarded headers
            val responseHeaders = HttpHeaders()
            var i = 1
            while (true) {
                val key = connection.getHeaderFieldKey(i) ?: break
                val value = connection.getHeaderField(i) ?: break
                if (!key.equals("transfer-encoding", ignoreCase = true)) {
                    responseHeaders.add(key, value)
                }
                i++
            }

            ResponseEntity.status(responseCode)
                .headers(responseHeaders)
                .body(responseBody)
        } catch (e: java.net.ConnectException) {
            logger.warn("Proxy connection refused: port={}, workspace={}", port, workspaceId)
            ResponseEntity.status(502)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"error":"No service running on port $port"}""".toByteArray())
        } catch (e: Exception) {
            logger.error("Proxy error: {}", e.message)
            ResponseEntity.status(502)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""{"error":"Proxy error: ${e.message}"}""".toByteArray())
        }
    }
}
