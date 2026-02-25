package com.forge.webide.service

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages running services (processes) within workspaces.
 * Tracks port → process mappings and provides start/stop/query operations.
 */
@Service
class WorkspaceRuntimeService(
    private val workspaceService: WorkspaceService
) {

    private val logger = LoggerFactory.getLogger(WorkspaceRuntimeService::class.java)

    private val runningServices = ConcurrentHashMap<String, MutableList<RunningService>>()

    @PostConstruct
    fun init() {
        // Wire back-reference to break circular dependency
        workspaceService.runtimeService = this
        logger.info("WorkspaceRuntimeService initialized")
    }

    data class RunningService(
        val workspaceId: String,
        val port: Int,
        val command: String,
        val process: Process,
        val startTime: Instant = Instant.now()
    )

    /**
     * Start a service by executing a command in the workspace directory.
     */
    companion object {
        // Ports reserved by the platform (Tomcat, MCP servers)
        private val RESERVED_PORTS = setOf(8080, 8081, 8082)
    }

    fun startService(workspaceId: String, command: String, port: Int): RunningService {
        if (port < 3000 || port > 9999) {
            throw IllegalArgumentException("Port must be between 3000 and 9999")
        }
        if (port in RESERVED_PORTS) {
            throw IllegalArgumentException("Port $port is reserved by the platform")
        }

        // Check if port is already in use in this workspace
        if (isPortInUse(workspaceId, port)) {
            throw IllegalStateException("Port $port is already in use in workspace $workspaceId")
        }

        val wsDir = workspaceService.getWorkspaceDir(workspaceId)
        val processBuilder = ProcessBuilder("/bin/sh", "-c", command)
            .directory(wsDir.toFile())
            .redirectErrorStream(true)

        val process = processBuilder.start()
        val service = RunningService(
            workspaceId = workspaceId,
            port = port,
            command = command,
            process = process
        )

        runningServices.getOrPut(workspaceId) { mutableListOf() }.add(service)
        logger.info("Started service: workspace={}, port={}, command={}", workspaceId, port, command)
        return service
    }

    /**
     * Register an externally started process as a running service.
     */
    fun registerService(workspaceId: String, port: Int, command: String, process: Process) {
        if (port < 3000 || port > 9999) return
        if (port in RESERVED_PORTS) return
        if (isPortInUse(workspaceId, port)) return

        val service = RunningService(
            workspaceId = workspaceId,
            port = port,
            command = command,
            process = process
        )
        runningServices.getOrPut(workspaceId) { mutableListOf() }.add(service)
        logger.info("Registered service: workspace={}, port={}, command={}", workspaceId, port, command)
    }

    /**
     * Get all running services for a workspace, cleaning up dead ones.
     */
    fun getServices(workspaceId: String): List<RunningService> {
        val services = runningServices[workspaceId] ?: return emptyList()
        // Clean up dead processes
        services.removeAll { !it.process.isAlive }
        return services.toList()
    }

    /**
     * Stop a specific service by port.
     */
    fun stopService(workspaceId: String, port: Int): Boolean {
        val services = runningServices[workspaceId] ?: return false
        val service = services.find { it.port == port } ?: return false

        service.process.destroyForcibly()
        services.remove(service)
        logger.info("Stopped service: workspace={}, port={}", workspaceId, port)
        return true
    }

    /**
     * Stop all services for a workspace.
     */
    fun stopAllServices(workspaceId: String) {
        val services = runningServices.remove(workspaceId) ?: return
        services.forEach { service ->
            try {
                service.process.destroyForcibly()
                logger.info("Stopped service: workspace={}, port={}", workspaceId, service.port)
            } catch (e: Exception) {
                logger.warn("Failed to stop service on port {}: {}", service.port, e.message)
            }
        }
    }

    /**
     * Check if a port is in use in a workspace.
     */
    fun isPortInUse(workspaceId: String, port: Int): Boolean {
        val services = runningServices[workspaceId] ?: return false
        return services.any { it.port == port && it.process.isAlive }
    }
}
