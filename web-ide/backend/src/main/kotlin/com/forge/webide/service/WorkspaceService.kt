package com.forge.webide.service

import com.forge.webide.entity.WorkspaceEntity
import com.forge.webide.model.*
import com.forge.webide.repository.WorkspaceRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Manages workspace lifecycle with DB persistence (metadata) and disk storage (files).
 *
 * - Workspace metadata → H2/PostgreSQL via WorkspaceEntity
 * - Workspace files → disk at {dataDir}/workspaces/{workspaceId}/
 * - File tree → rebuilt from disk on demand, cached in memory
 */
@Service
class WorkspaceService(
    private val workspaceRepository: WorkspaceRepository,
    private val gitService: GitService,
    private val knowledgeTagService: KnowledgeTagService,
    @Value("\${forge.workspaces.data-dir:./data/workspaces}") private val dataDir: String
) {

    // Late-initialized to break circular dependency (RuntimeService depends on WorkspaceService)
    internal var runtimeService: WorkspaceRuntimeService? = null

    private val logger = LoggerFactory.getLogger(WorkspaceService::class.java)

    // Hot cache for file trees (rebuilt from disk, invalidated on writes)
    private val fileTreeCache = ConcurrentHashMap<String, List<FileNode>>()

    // Thread pool for async git clone operations
    private val cloneExecutor = Executors.newFixedThreadPool(2)

    private val basePath: Path by lazy { Paths.get(dataDir).toAbsolutePath() }

    @PostConstruct
    fun init() {
        Files.createDirectories(basePath)
        val count = workspaceRepository.count()
        logger.info("Workspace service initialized. dataDir={}, existingWorkspaces={}", basePath, count)
    }

    // =========================================================================
    // Workspace CRUD
    // =========================================================================

    fun createWorkspace(request: CreateWorkspaceRequest, userId: String): Workspace {
        val needsClone = !request.repository.isNullOrBlank()

        val entity = WorkspaceEntity(
            name = request.name,
            description = request.description ?: "",
            status = if (needsClone) WorkspaceStatus.CREATING else WorkspaceStatus.ACTIVE,
            owner = userId,
            repository = request.repository,
            branch = request.branch ?: if (needsClone) "main" else null,
            accessToken = request.accessToken?.takeIf { it.isNotBlank() }
        )

        val wsDir = basePath.resolve(entity.id)
        Files.createDirectories(wsDir)
        entity.localPath = wsDir.toString()

        if (needsClone) {
            // Save with CREATING status, start async clone, return immediately
            workspaceRepository.save(entity)
            logger.info("Created workspace '{}' ({}) — starting async git clone", entity.name, entity.id)
            cloneAsync(entity.id, request.repository!!, entity.branch, wsDir, request.accessToken)
        } else {
            // Empty workspace — create default files synchronously
            initializeDefaultFiles(wsDir)
            workspaceRepository.save(entity)
            logger.info("Created workspace '{}' ({}) for user {}", entity.name, entity.id, userId)
        }

        return entity.toModel()
    }

    /**
     * Async git clone: runs in background thread, updates workspace status on completion.
     * Uses accessToken (if provided) to construct an authenticated URL for private repos.
     * The clean repository URL (without token) is stored in DB; only the auth URL is passed to git.
     */
    private fun cloneAsync(workspaceId: String, repository: String, branch: String?, wsDir: Path, accessToken: String? = null) {
        cloneExecutor.submit {
            try {
                val cloneUrl = buildAuthUrl(repository, accessToken)
                logger.info("Async clone started: workspace={}, repo={}", workspaceId, repository)
                gitService.cloneRepository(cloneUrl, branch, wsDir)

                // Clone succeeded → ACTIVE
                workspaceRepository.findById(workspaceId).ifPresent { entity ->
                    entity.status = WorkspaceStatus.ACTIVE
                    entity.updatedAt = Instant.now()
                    workspaceRepository.save(entity)
                    logger.info("Async clone completed: workspace={}", workspaceId)
                }
            } catch (e: Exception) {
                // Clone failed → ERROR with message
                logger.error("Async clone failed: workspace={}, error={}", workspaceId, e.message)
                workspaceRepository.findById(workspaceId).ifPresent { entity ->
                    entity.status = WorkspaceStatus.ERROR
                    entity.errorMessage = e.message?.take(1000) ?: "Git clone failed"
                    entity.updatedAt = Instant.now()
                    workspaceRepository.save(entity)
                }
            }
        }
    }

    fun listWorkspaces(userId: String): List<Workspace> {
        val entities = workspaceRepository.findByOwnerOrOwnerIn(userId, listOf("", "anonymous"))
        return entities
            .map { it.toModel() }
            .sortedByDescending { it.updatedAt }
    }

    fun getWorkspace(id: String): Workspace? {
        return workspaceRepository.findById(id).orElse(null)?.toModel()
    }

    fun deleteWorkspace(id: String, userId: String) {
        val entity = workspaceRepository.findById(id).orElse(null) ?: return
        if (entity.owner != userId && entity.owner.isNotEmpty() && entity.owner != "anonymous") {
            throw IllegalAccessException("User $userId cannot delete workspace $id")
        }

        // Stop all running services before deleting
        runtimeService?.stopAllServices(id)

        // Clean up workspace knowledge tags
        knowledgeTagService.deleteWorkspaceTags(id)

        // Remove files from disk
        val wsDir = getWorkspaceDir(id)
        if (Files.exists(wsDir)) {
            deleteDirectoryRecursively(wsDir)
        }

        // Remove from DB
        workspaceRepository.deleteById(id)
        fileTreeCache.remove(id)

        logger.info("Deleted workspace {}", id)
    }

    fun activateWorkspace(id: String): Workspace? {
        val entity = workspaceRepository.findById(id).orElse(null) ?: return null
        entity.status = WorkspaceStatus.ACTIVE
        entity.updatedAt = Instant.now()
        workspaceRepository.save(entity)
        logger.info("Activated workspace {}", id)
        return entity.toModel()
    }

    fun suspendWorkspace(id: String): Workspace? {
        val entity = workspaceRepository.findById(id).orElse(null) ?: return null

        // Stop all running services before suspending
        runtimeService?.stopAllServices(id)

        entity.status = WorkspaceStatus.SUSPENDED
        entity.updatedAt = Instant.now()
        workspaceRepository.save(entity)
        logger.info("Suspended workspace {}", id)
        return entity.toModel()
    }

    // =========================================================================
    // File Operations (disk-based)
    // =========================================================================

    fun getFileTree(workspaceId: String): List<FileNode> {
        return fileTreeCache.getOrPut(workspaceId) {
            buildFileTreeFromDisk(workspaceId)
        }
    }

    fun getFileContent(workspaceId: String, path: String): String? {
        validatePath(path)
        val filePath = getWorkspaceDir(workspaceId).resolve(path)
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) return null
        return Files.readString(filePath)
    }

    fun getFileBytes(workspaceId: String, path: String): ByteArray? {
        validatePath(path)
        val filePath = getWorkspaceDir(workspaceId).resolve(path)
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) return null
        return Files.readAllBytes(filePath)
    }

    fun saveFileContent(workspaceId: String, path: String, content: String) {
        validatePath(path)
        val filePath = getWorkspaceDir(workspaceId).resolve(path)
        if (!Files.exists(filePath)) {
            // Create parent dirs if they don't exist, then create file
            Files.createDirectories(filePath.parent)
        }
        Files.writeString(filePath, content)
        touchWorkspace(workspaceId)
        // File tree may not change (existing file), but invalidate cache to be safe
        fileTreeCache.remove(workspaceId)
    }

    fun createFile(workspaceId: String, path: String, content: String) {
        validatePath(path)
        val filePath = getWorkspaceDir(workspaceId).resolve(path)
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, content)
        fileTreeCache.remove(workspaceId)
        touchWorkspace(workspaceId)
    }

    fun deleteFile(workspaceId: String, path: String) {
        validatePath(path)
        val filePath = getWorkspaceDir(workspaceId).resolve(path)
        if (Files.isDirectory(filePath)) {
            deleteDirectoryRecursively(filePath)
        } else if (Files.exists(filePath)) {
            Files.delete(filePath)
        }
        fileTreeCache.remove(workspaceId)
        touchWorkspace(workspaceId)
    }

    // =========================================================================
    // Git operations
    // =========================================================================

    fun pullWorkspace(workspaceId: String): String {
        val entity = workspaceRepository.findById(workspaceId).orElse(null)
        val wsDir = getWorkspaceDir(workspaceId)
        val authUrl = if (entity?.accessToken != null && entity.repository != null) {
            buildAuthUrl(entity.repository, entity.accessToken)
        } else null
        val result = gitService.pull(wsDir, rebase = false, remoteUrl = authUrl)
        fileTreeCache.remove(workspaceId)
        touchWorkspace(workspaceId)
        return result
    }

    /**
     * Returns the authenticated URL for a workspace's git repository, or null if no token is configured.
     * Used by WorkspaceToolHandler for push/pull operations.
     */
    fun getWorkspaceAuthUrl(workspaceId: String): String? {
        val entity = workspaceRepository.findById(workspaceId).orElse(null) ?: return null
        val repo = entity.repository ?: return null
        val token = entity.accessToken?.takeIf { it.isNotBlank() } ?: return null
        return buildAuthUrl(repo, token)
    }

    /**
     * Injects oauth2:<token>@ into a repository URL for authenticated git operations.
     * The clean URL (without token) should always be stored in DB.
     * Example: https://gitlab.com/org/repo.git → https://oauth2:token@gitlab.com/org/repo.git
     */
    fun buildAuthUrl(repository: String, accessToken: String?): String {
        if (accessToken.isNullOrBlank()) return repository
        return try {
            val uri = URI(repository)
            URI(uri.scheme, "oauth2:$accessToken", uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
        } catch (e: Exception) {
            logger.warn("Failed to parse repository URL for auth injection: {}", e.message)
            repository
        }
    }

    fun getGitStatus(workspaceId: String): GitStatus {
        return gitService.status(getWorkspaceDir(workspaceId))
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    internal fun getWorkspaceDir(workspaceId: String): Path {
        return basePath.resolve(workspaceId)
    }

    private fun validatePath(path: String) {
        if (path.contains("..")) {
            throw IllegalArgumentException("Path traversal not allowed: $path")
        }
    }

    private fun touchWorkspace(workspaceId: String) {
        workspaceRepository.findById(workspaceId).ifPresent { entity ->
            entity.updatedAt = Instant.now()
            workspaceRepository.save(entity)
        }
    }

    private fun buildFileTreeFromDisk(workspaceId: String): List<FileNode> {
        val wsDir = getWorkspaceDir(workspaceId)
        if (!Files.exists(wsDir)) return emptyList()
        return buildTreeRecursive(wsDir, wsDir)
    }

    private fun buildTreeRecursive(root: Path, current: Path): List<FileNode> {
        val entries = try {
            Files.list(current).use { stream ->
                stream.sorted(compareBy<Path> { !Files.isDirectory(it) }.thenBy { it.fileName.toString() })
                    .toList()
            }
        } catch (e: Exception) {
            return emptyList()
        }

        return entries
            .filter { !isHiddenOrIgnored(it) }
            .map { entry ->
                val relativePath = root.relativize(entry).toString().replace("\\", "/")
                if (Files.isDirectory(entry)) {
                    FileNode(
                        name = entry.fileName.toString(),
                        path = relativePath,
                        type = FileType.DIRECTORY,
                        children = buildTreeRecursive(root, entry)
                    )
                } else {
                    FileNode(
                        name = entry.fileName.toString(),
                        path = relativePath,
                        type = FileType.FILE,
                        size = try { Files.size(entry) } catch (_: Exception) { null }
                    )
                }
            }
    }

    private fun isHiddenOrIgnored(path: Path): Boolean {
        val name = path.fileName.toString()
        // Hide .git directory but show other dotfiles (like .gitignore)
        return name == ".git" || name == "node_modules" || name == ".DS_Store"
    }

    private fun initializeDefaultFiles(wsDir: Path) {
        writeDefaultFile(wsDir, "src/index.ts", """
            |// Forge Workspace - Entry Point
            |
            |import { Application } from './app';
            |
            |async function main() {
            |    const app = new Application();
            |    await app.start();
            |    console.log('Application started successfully');
            |}
            |
            |main().catch(console.error);
        """.trimMargin())

        writeDefaultFile(wsDir, "src/app.ts", """
            |export class Application {
            |    private name: string;
            |
            |    constructor() {
            |        this.name = 'forge-app';
            |    }
            |
            |    async start(): Promise<void> {
            |        console.log(`Starting ${'$'}{this.name}...`);
            |    }
            |}
        """.trimMargin())

        writeDefaultFile(wsDir, "package.json", """
            |{
            |    "name": "forge-workspace",
            |    "version": "1.0.0",
            |    "main": "dist/index.js",
            |    "scripts": {
            |        "build": "tsc",
            |        "start": "node dist/index.js",
            |        "dev": "ts-node src/index.ts"
            |    }
            |}
        """.trimMargin())

        writeDefaultFile(wsDir, "tsconfig.json", """
            |{
            |    "compilerOptions": {
            |        "target": "ES2022",
            |        "module": "commonjs",
            |        "outDir": "./dist",
            |        "strict": true
            |    },
            |    "include": ["src/**/*"]
            |}
        """.trimMargin())

        writeDefaultFile(wsDir, "README.md", """
            |# Forge Workspace
            |
            |This is a new Forge workspace.
            |
            |## Getting Started
            |
            |```bash
            |npm install
            |npm run dev
            |```
        """.trimMargin())
    }

    private fun writeDefaultFile(wsDir: Path, relativePath: String, content: String) {
        val filePath = wsDir.resolve(relativePath)
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, content)
    }

    private fun deleteDirectoryRecursively(dir: Path) {
        if (!Files.exists(dir)) return
        Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }
            override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    // =========================================================================
    // Entity → Model mapping
    // =========================================================================

    private fun WorkspaceEntity.toModel() = Workspace(
        id = id,
        name = name,
        description = description,
        status = status,
        owner = owner,
        repository = repository,
        branch = branch,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
