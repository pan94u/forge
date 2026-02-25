package com.forge.webide.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.forge.webide.service.WorkspaceRuntimeService
import com.forge.webide.service.WorkspaceService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * WebSocket handler for terminal sessions.
 *
 * Provides a WebSocket-based terminal that connects to workspace pods
 * for executing commands. In development mode, it runs commands locally.
 * In production, it would connect to the code-server pod via K8s exec.
 */
@Component
class TerminalWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val workspaceService: WorkspaceService,
    private val runtimeService: WorkspaceRuntimeService
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(TerminalWebSocketHandler::class.java)
    private val executor = Executors.newCachedThreadPool()
    private val processMap = ConcurrentHashMap<String, Process>()
    private val sessionWorkspaceMap = ConcurrentHashMap<String, String>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val workspaceId = extractWorkspaceId(session)
        sessionWorkspaceMap[session.id] = workspaceId

        logger.info("Terminal WebSocket connected: ws=${session.id}, workspace=$workspaceId")

        sendMessage(session, mapOf(
            "type" to "system",
            "content" to "Terminal session started for workspace $workspaceId"
        ))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val payload = objectMapper.readValue(message.payload, Map::class.java)
            val type = payload["type"] as? String
            val content = payload["content"] as? String ?: return

            when (type) {
                "command" -> executeCommand(session, content)
                "input" -> sendInputToProcess(session, content)
                "resize" -> {
                    // Terminal resize event (rows, cols)
                    logger.debug("Terminal resize: ${payload["rows"]}x${payload["cols"]}")
                }
                else -> executeCommand(session, content)
            }
        } catch (e: Exception) {
            logger.error("Error handling terminal message: ${e.message}", e)
            sendMessage(session, mapOf(
                "type" to "error",
                "content" to "Error: ${e.message}"
            ))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val workspaceId = sessionWorkspaceMap.remove(session.id)
        val process = processMap.remove(session.id)
        process?.destroyForcibly()

        logger.info("Terminal WebSocket disconnected: ws=${session.id}, workspace=$workspaceId")
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error("Terminal WebSocket error: ws=${session.id}, error=${exception.message}")
        processMap.remove(session.id)?.destroyForcibly()
        sessionWorkspaceMap.remove(session.id)
    }

    private fun executeCommand(session: WebSocketSession, command: String) {
        executor.submit {
            try {
                // In production, this would exec into the workspace's K8s pod
                // For development, we run commands locally in a restricted manner
                val sanitizedCommand = sanitizeCommand(command)

                val processBuilder = ProcessBuilder("/bin/sh", "-c", sanitizedCommand)
                    .redirectErrorStream(true)

                // Set working directory to workspace directory
                val workspaceId = sessionWorkspaceMap[session.id]
                if (workspaceId != null) {
                    val wsDir = workspaceService.getWorkspaceDir(workspaceId)
                    if (Files.exists(wsDir)) {
                        processBuilder.directory(wsDir.toFile())
                    }
                }

                val process = processBuilder.start()
                processMap[session.id] = process

                // Detect port from command and register as a service
                val detectedPort = detectPort(sanitizedCommand)
                if (detectedPort != null && workspaceId != null) {
                    runtimeService.registerService(workspaceId, detectedPort, sanitizedCommand, process)
                    sendMessage(session, mapOf(
                        "type" to "system",
                        "content" to "Service detected on port $detectedPort"
                    ))
                }

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val outputBuilder = StringBuilder()

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    outputBuilder.appendLine(line)
                    sendMessage(session, mapOf(
                        "type" to "output",
                        "content" to (line ?: "")
                    ))
                }

                val exitCode = process.waitFor()
                processMap.remove(session.id)

                if (exitCode != 0) {
                    sendMessage(session, mapOf(
                        "type" to "error",
                        "content" to "Process exited with code $exitCode"
                    ))
                }
            } catch (e: Exception) {
                logger.error("Command execution failed: ${e.message}", e)
                sendMessage(session, mapOf(
                    "type" to "error",
                    "content" to "Execution failed: ${e.message}"
                ))
            }
        }
    }

    private fun sendInputToProcess(session: WebSocketSession, input: String) {
        val process = processMap[session.id]
        if (process != null && process.isAlive) {
            try {
                val writer = OutputStreamWriter(process.outputStream)
                writer.write(input)
                writer.write("\n")
                writer.flush()
            } catch (e: Exception) {
                sendMessage(session, mapOf(
                    "type" to "error",
                    "content" to "Failed to send input: ${e.message}"
                ))
            }
        } else {
            sendMessage(session, mapOf(
                "type" to "error",
                "content" to "No active process to send input to"
            ))
        }
    }

    private fun sanitizeCommand(command: String): String {
        // Basic command sanitization for security
        // In production, commands would run inside isolated K8s pods
        val blockedPatterns = listOf(
            "rm -rf /",
            "mkfs",
            "dd if=/dev/",
            "> /dev/sda",
            ":(){ :|:& };:",
        )

        val commandLower = command.lowercase()
        for (pattern in blockedPatterns) {
            if (commandLower.contains(pattern)) {
                throw SecurityException("Command blocked for safety: $pattern")
            }
        }

        return command
    }

    /**
     * Detect a port number from common server-start commands.
     */
    private fun detectPort(command: String): Int? {
        val cmd = command.trim()

        // python3 -m http.server 8888
        val httpServerMatch = Regex("""http\.server\s+(\d+)""").find(cmd)
        if (httpServerMatch != null) return httpServerMatch.groupValues[1].toIntOrNull()

        // Explicit port flags: -p 8080, --port 8080, --port=8080, :8080
        val portFlagMatch = Regex("""(?:-p|--port)[=\s]+(\d+)""").find(cmd)
        if (portFlagMatch != null) return portFlagMatch.groupValues[1].toIntOrNull()

        // PORT=3001 node server.js
        val envPortMatch = Regex("""PORT=(\d+)""").find(cmd)
        if (envPortMatch != null) return envPortMatch.groupValues[1].toIntOrNull()

        // npm start / npm run dev / npm run serve → default 3000
        if (cmd.startsWith("npm start") || cmd.startsWith("npm run dev") || cmd.startsWith("npm run serve")) {
            return 3000
        }

        // node server.js / node app.js → default 3000 (exclude flags like --version)
        if (Regex("""^node\s+(?!-)\S+\.\w+""").containsMatchIn(cmd)) {
            return 3000
        }

        // python3 -m http.server (no port) → default 8000
        if (cmd.contains("http.server")) {
            return 8000
        }

        return null
    }

    private fun sendMessage(session: WebSocketSession, data: Any) {
        try {
            val json = objectMapper.writeValueAsString(data)
            synchronized(session) {
                if (session.isOpen) {
                    session.sendMessage(TextMessage(json))
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to send terminal message: ${e.message}")
        }
    }

    private fun extractWorkspaceId(session: WebSocketSession): String {
        val path = session.uri?.path ?: ""
        val segments = path.split("/").filter { it.isNotEmpty() }
        return segments.lastOrNull() ?: "unknown"
    }
}
