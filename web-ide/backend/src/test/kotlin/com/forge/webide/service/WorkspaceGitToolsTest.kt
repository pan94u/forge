package com.forge.webide.service

import com.forge.webide.model.McpContent
import com.forge.webide.model.McpToolCallResponse
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.nio.file.Paths

class WorkspaceGitToolsTest {

    private lateinit var workspaceService: WorkspaceService
    private lateinit var runtimeService: WorkspaceRuntimeService
    private lateinit var gitService: GitService
    private lateinit var handler: WorkspaceToolHandler

    private val workspaceId = "ws-test-123"
    private val workspaceDir: Path = Paths.get("/data/workspaces/$workspaceId")

    @BeforeEach
    fun setUp() {
        workspaceService = mockk(relaxed = true)
        runtimeService = mockk(relaxed = true)
        gitService = mockk(relaxed = true)

        every { workspaceService.getWorkspaceDir(workspaceId) } returns workspaceDir
        every { workspaceService.getWorkspaceAuthUrl(any()) } returns null

        val gitConfirmService = mockk<GitConfirmService>(relaxed = true)
        handler = WorkspaceToolHandler(workspaceService, runtimeService, gitService, gitConfirmService)
    }

    @Nested
    inner class GitStatus {

        @Test
        fun `returns current branch and clean status`() {
            every { gitService.status(workspaceDir) } returns GitStatus(
                branch = "feature/user-login",
                clean = true,
                modifiedFiles = emptyList()
            )

            val result = handler.handle("workspace_git_status", emptyMap(), workspaceId)

            assertThat(result.isError).isFalse()
            assertThat(result.content.first().text).contains("feature/user-login")
            assertThat(result.content.first().text).contains("clean")
        }

        @Test
        fun `returns modified files when workspace has changes`() {
            every { gitService.status(workspaceDir) } returns GitStatus(
                branch = "main",
                clean = false,
                modifiedFiles = listOf("src/Main.kt", "README.md")
            )

            val result = handler.handle("workspace_git_status", emptyMap(), workspaceId)

            assertThat(result.isError).isFalse()
            assertThat(result.content.first().text).contains("src/Main.kt")
            assertThat(result.content.first().text).contains("README.md")
        }

        @Test
        fun `returns error when git fails`() {
            every { gitService.status(workspaceDir) } throws GitOperationException("not a git repo")

            val result = handler.handle("workspace_git_status", emptyMap(), workspaceId)

            assertThat(result.isError).isTrue()
        }
    }

    @Nested
    inner class GitDiff {

        @Test
        fun `returns diff output`() {
            val diffOutput = "diff --git a/src/Main.kt b/src/Main.kt\n+fun main() {}"
            every { gitService.diff(workspaceDir) } returns diffOutput

            val result = handler.handle("workspace_git_diff", emptyMap(), workspaceId)

            assertThat(result.isError).isFalse()
            assertThat(result.content.first().text).isEqualTo(diffOutput)
        }

        @Test
        fun `returns no changes message when diff is empty`() {
            every { gitService.diff(workspaceDir) } returns "(no changes)"

            val result = handler.handle("workspace_git_diff", emptyMap(), workspaceId)

            assertThat(result.isError).isFalse()
            assertThat(result.content.first().text).contains("no changes")
        }
    }

    @Nested
    inner class GitAdd {

        @Test
        fun `stages specific paths`() {
            val paths = listOf("src/Main.kt", "README.md")
            every { gitService.add(workspaceDir, paths) } returns "Staged: src/Main.kt, README.md"

            val result = handler.handle(
                "workspace_git_add",
                mapOf("paths" to paths),
                workspaceId
            )

            assertThat(result.isError).isFalse()
            verify { gitService.add(workspaceDir, paths) }
        }

        @Test
        fun `stages all changes when all=true`() {
            every { gitService.add(workspaceDir, emptyList()) } returns "Staged: all changes"

            val result = handler.handle(
                "workspace_git_add",
                mapOf("all" to true),
                workspaceId
            )

            assertThat(result.isError).isFalse()
            verify { gitService.add(workspaceDir, emptyList()) }
        }
    }

    @Nested
    inner class GitCommit {

        @Test
        fun `commits with message`() {
            val message = "feat: add user login"
            every { gitService.commit(workspaceDir, message) } returns "[feature/login abc1234] feat: add user login [Forge-Agent]"

            val result = handler.handle(
                "workspace_git_commit",
                mapOf("message" to message),
                workspaceId
            )

            assertThat(result.isError).isFalse()
            verify { gitService.commit(workspaceDir, message) }
        }

        @Test
        fun `returns error when message is missing`() {
            val result = handler.handle(
                "workspace_git_commit",
                emptyMap(),
                workspaceId
            )

            assertThat(result.isError).isTrue()
            assertThat(result.content.first().text).contains("'message' parameter is required")
        }
    }

    @Nested
    inner class GitPush {

        @Test
        fun `returns warning instead of pushing to main`() {
            every { gitService.push(workspaceDir, "origin", null, null) } returns
                "⚠️ 安全警告：当前在 main 分支上。直接推送到 main 存在风险。"

            val result = handler.handle(
                "workspace_git_push",
                emptyMap(),
                workspaceId
            )

            assertThat(result.isError).isFalse()
            assertThat(result.content.first().text).contains("安全警告")
        }

        @Test
        fun `pushes feature branch successfully`() {
            every { gitService.push(workspaceDir, "origin", null, null) } returns "Pushed feature/login to origin"

            val result = handler.handle(
                "workspace_git_push",
                mapOf("remote" to "origin"),
                workspaceId
            )

            assertThat(result.isError).isFalse()
        }
    }

    @Nested
    inner class GitPull {

        @Test
        fun `pulls with rebase by default`() {
            every { gitService.pull(workspaceDir, "origin", true, null) } returns "Already up to date."

            val result = handler.handle(
                "workspace_git_pull",
                emptyMap(),
                workspaceId
            )

            assertThat(result.isError).isFalse()
            verify { gitService.pull(workspaceDir, "origin", true, null) }
        }

        @Test
        fun `pulls with merge when rebase=false`() {
            every { gitService.pull(workspaceDir, "origin", false, null) } returns "1 file changed"

            val result = handler.handle(
                "workspace_git_pull",
                mapOf("rebase" to false),
                workspaceId
            )

            assertThat(result.isError).isFalse()
            verify { gitService.pull(workspaceDir, "origin", false, null) }
        }
    }

    @Nested
    inner class GitBranch {

        @Test
        fun `creates new branch`() {
            every { gitService.createBranch(workspaceDir, "feature/new-feature") } returns
                "Created and switched to branch: feature/new-feature"

            val result = handler.handle(
                "workspace_git_branch",
                mapOf("name" to "feature/new-feature"),
                workspaceId
            )

            assertThat(result.isError).isFalse()
            assertThat(result.content.first().text).contains("feature/new-feature")
        }

        @Test
        fun `lists all branches when list=true`() {
            every { gitService.listBranches(workspaceDir) } returns "* main\n  feature/login\n  feature/dashboard"

            val result = handler.handle(
                "workspace_git_branch",
                mapOf("list" to true),
                workspaceId
            )

            assertThat(result.isError).isFalse()
            assertThat(result.content.first().text).contains("main")
        }
    }

    @Nested
    inner class RequiresWorkspaceId {

        @Test
        fun `git status fails without workspaceId`() {
            val result = handler.handle("workspace_git_status", emptyMap(), null)

            assertThat(result.isError).isTrue()
            assertThat(result.content.first().text).contains("workspaceId")
        }
    }
}
