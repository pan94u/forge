package com.forge.webide.service

import com.forge.webide.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages workspace lifecycle including creation, activation (starting
 * code-server pods), suspension, and deletion.
 *
 * In a production deployment, this service would interact with Kubernetes
 * to manage workspace pods. For now, it uses in-memory storage.
 */
@Service
class WorkspaceService {

    private val logger = LoggerFactory.getLogger(WorkspaceService::class.java)

    private val workspaces = ConcurrentHashMap<String, Workspace>()
    private val fileTrees = ConcurrentHashMap<String, List<FileNode>>()
    private val fileContents = ConcurrentHashMap<String, MutableMap<String, String>>()

    fun createWorkspace(request: CreateWorkspaceRequest, userId: String): Workspace {
        val workspace = Workspace(
            name = request.name,
            description = request.description ?: "",
            status = WorkspaceStatus.ACTIVE,
            owner = userId,
            repository = request.repository,
            branch = request.branch ?: "main"
        )

        workspaces[workspace.id] = workspace
        initializeDefaultFiles(workspace.id)

        logger.info("Created workspace '${workspace.name}' (${workspace.id}) for user $userId")
        return workspace
    }

    fun listWorkspaces(userId: String): List<Workspace> {
        return workspaces.values
            .filter { it.owner == userId || it.owner.isEmpty() || it.owner == "anonymous" }
            .sortedByDescending { it.updatedAt }
    }

    fun getWorkspace(id: String): Workspace? {
        return workspaces[id]
    }

    fun deleteWorkspace(id: String, userId: String) {
        val workspace = workspaces[id] ?: return
        if (workspace.owner != userId && workspace.owner.isNotEmpty() && workspace.owner != "anonymous") {
            throw IllegalAccessException("User $userId cannot delete workspace $id")
        }

        // In production: tear down K8s pod
        workspaces.remove(id)
        fileTrees.remove(id)
        fileContents.remove(id)

        logger.info("Deleted workspace $id")
    }

    fun activateWorkspace(id: String): Workspace? {
        val workspace = workspaces[id] ?: return null

        // In production: create/start K8s pod for code-server
        val activated = workspace.copy(
            status = WorkspaceStatus.ACTIVE,
            updatedAt = Instant.now()
        )
        workspaces[id] = activated

        logger.info("Activated workspace $id")
        return activated
    }

    fun suspendWorkspace(id: String): Workspace? {
        val workspace = workspaces[id] ?: return null

        // In production: scale down K8s pod
        val suspended = workspace.copy(
            status = WorkspaceStatus.SUSPENDED,
            updatedAt = Instant.now()
        )
        workspaces[id] = suspended

        logger.info("Suspended workspace $id")
        return suspended
    }

    fun getFileTree(workspaceId: String): List<FileNode> {
        return fileTrees[workspaceId] ?: emptyList()
    }

    fun getFileContent(workspaceId: String, path: String): String? {
        return fileContents[workspaceId]?.get(path)
    }

    fun saveFileContent(workspaceId: String, path: String, content: String) {
        fileContents.getOrPut(workspaceId) { mutableMapOf() }[path] = content

        // Update the workspace timestamp
        workspaces[workspaceId]?.let {
            workspaces[workspaceId] = it.copy(updatedAt = Instant.now())
        }
    }

    fun createFile(workspaceId: String, path: String, content: String) {
        fileContents.getOrPut(workspaceId) { mutableMapOf() }[path] = content
        rebuildFileTree(workspaceId)
    }

    fun deleteFile(workspaceId: String, path: String) {
        fileContents[workspaceId]?.remove(path)
        rebuildFileTree(workspaceId)
    }

    private fun initializeDefaultFiles(workspaceId: String) {
        val files = mutableMapOf<String, String>()

        files["src/index.ts"] = """
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
        """.trimMargin()

        files["src/app.ts"] = """
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
        """.trimMargin()

        files["package.json"] = """
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
        """.trimMargin()

        files["tsconfig.json"] = """
            |{
            |    "compilerOptions": {
            |        "target": "ES2022",
            |        "module": "commonjs",
            |        "outDir": "./dist",
            |        "strict": true
            |    },
            |    "include": ["src/**/*"]
            |}
        """.trimMargin()

        files["README.md"] = """
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
        """.trimMargin()

        fileContents[workspaceId] = files
        rebuildFileTree(workspaceId)
    }

    private fun rebuildFileTree(workspaceId: String) {
        val files = fileContents[workspaceId] ?: return

        // Build tree structure from flat paths
        val rootNodes = mutableMapOf<String, FileNode>()

        for (path in files.keys.sorted()) {
            val parts = path.split("/")
            if (parts.size == 1) {
                rootNodes[path] = FileNode(
                    name = parts[0],
                    path = path,
                    type = FileType.FILE,
                    size = files[path]?.length?.toLong()
                )
            } else {
                // Ensure parent directories exist
                var currentPath = ""
                for (i in 0 until parts.size - 1) {
                    val dirName = parts[i]
                    currentPath = if (currentPath.isEmpty()) dirName else "$currentPath/$dirName"

                    if (!rootNodes.containsKey(currentPath) && i == 0) {
                        rootNodes[currentPath] = FileNode(
                            name = dirName,
                            path = currentPath,
                            type = FileType.DIRECTORY,
                            children = mutableListOf()
                        )
                    }
                }
            }
        }

        // Simplified tree: just build two levels
        val tree = mutableListOf<FileNode>()
        val dirMap = mutableMapOf<String, MutableList<FileNode>>()

        for (path in files.keys.sorted()) {
            val parts = path.split("/")
            if (parts.size == 1) {
                tree.add(FileNode(name = parts[0], path = path, type = FileType.FILE, size = files[path]?.length?.toLong()))
            } else {
                val dirName = parts[0]
                val children = dirMap.getOrPut(dirName) { mutableListOf() }
                children.add(FileNode(
                    name = parts.drop(1).joinToString("/"),
                    path = path,
                    type = FileType.FILE,
                    size = files[path]?.length?.toLong()
                ))
            }
        }

        for ((dirName, children) in dirMap) {
            tree.add(FileNode(
                name = dirName,
                path = dirName,
                type = FileType.DIRECTORY,
                children = children
            ))
        }

        fileTrees[workspaceId] = tree
    }
}
