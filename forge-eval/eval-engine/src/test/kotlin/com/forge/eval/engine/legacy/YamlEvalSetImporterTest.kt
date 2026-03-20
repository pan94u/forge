package com.forge.eval.engine.legacy

import com.forge.eval.protocol.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.UUID

class YamlEvalSetImporterTest {

    @TempDir
    lateinit var tempDir: File

    private fun writeEvalYaml(profileDir: String, fileName: String, content: String): File {
        val dir = File(tempDir, profileDir)
        dir.mkdirs()
        val file = File(dir, fileName)
        file.writeText(content)
        return file
    }

    @Test
    fun `importAll - imports single profile with single eval`() {
        writeEvalYaml("development-profile", "eval-001-test.yaml", """
            id: eval-001-test
            name: "Test Eval"
            description: "A test evaluation"
            tags: [development, test]
            baseline_pass_rate: 0.85

            prompt: |
              Write a hello world function

            context:
              language: kotlin
              framework: spring-boot

            assertions:
              - type: contains
                expected: "fun "
                description: "Contains function definition"
              - type: not_contains
                expected: "TODO"
                description: "No TODOs"

            quality_criteria:
              completeness: 0.9
              correctness: 0.85
        """.trimIndent())

        val importer = YamlEvalSetImporter(tempDir)
        val result = importer.importAll()

        assertThat(result.suites).hasSize(1)
        assertThat(result.suites[0].name).contains("development")
        assertThat(result.suites[0].platform).isEqualTo(Platform.FORGE)
        assertThat(result.totalTasks).isEqualTo(1)
        assertThat(result.errors).isEmpty()

        val tasks = result.tasksBySuite[result.suites[0].id]!!
        assertThat(tasks).hasSize(1)
        assertThat(tasks[0].name).isEqualTo("Test Eval")
        assertThat(tasks[0].prompt).contains("hello world")
        assertThat(tasks[0].baselinePassRate).isEqualTo(0.85)
        assertThat(tasks[0].graderConfigs).hasSize(1)
        assertThat(tasks[0].graderConfigs[0].assertions).hasSize(2)
        assertThat(tasks[0].graderConfigs[0].assertions[0].type).isEqualTo("contains")
        assertThat(tasks[0].graderConfigs[0].assertions[1].type).isEqualTo("not_contains")
    }

    @Test
    fun `importAll - imports multiple profiles`() {
        writeEvalYaml("development-profile", "eval-001.yaml", """
            id: eval-001
            name: "Dev Eval"
            prompt: "Write code"
            assertions:
              - type: contains
                expected: "function"
                description: "has function"
        """.trimIndent())

        writeEvalYaml("testing-profile", "eval-001.yaml", """
            id: eval-001-test
            name: "Test Eval"
            prompt: "Write tests"
            assertions:
              - type: contains
                expected: "@Test"
                description: "has test"
        """.trimIndent())

        val importer = YamlEvalSetImporter(tempDir)
        val result = importer.importAll()

        assertThat(result.suites).hasSize(2)
        assertThat(result.totalTasks).isEqualTo(2)
    }

    @Test
    fun `importAll - handles case_insensitive flag`() {
        writeEvalYaml("dev-profile", "eval-001.yaml", """
            id: eval-001
            name: "CI Test"
            prompt: "do something"
            assertions:
              - type: contains
                expected: "JsonValue"
                case_insensitive: true
                description: "has JsonValue"
        """.trimIndent())

        val importer = YamlEvalSetImporter(tempDir)
        val result = importer.importAll()
        val assertion = result.tasksBySuite.values.first().first().graderConfigs[0].assertions[0]
        assertThat(assertion.caseSensitive).isFalse()
    }

    @Test
    fun `importAll - handles tool_used assertion type`() {
        writeEvalYaml("dev-profile", "eval-001.yaml", """
            id: eval-001
            name: "Tool Test"
            prompt: "fix bug"
            assertions:
              - type: tool_used
                expected: "workspace_write_file"
                description: "must write file"
        """.trimIndent())

        val importer = YamlEvalSetImporter(tempDir)
        val result = importer.importAll()
        val assertion = result.tasksBySuite.values.first().first().graderConfigs[0].assertions[0]
        assertThat(assertion.type).isEqualTo("tool_used")
        assertThat(assertion.expected).isEqualTo("workspace_write_file")
    }

    @Test
    fun `importAll - empty directory returns empty result`() {
        val emptyDir = File(tempDir, "empty")
        emptyDir.mkdirs()
        val importer = YamlEvalSetImporter(emptyDir)
        val result = importer.importAll()
        assertThat(result.suites).isEmpty()
        assertThat(result.totalTasks).isEqualTo(0)
    }

    @Test
    fun `importAll - nonexistent directory reports error`() {
        val importer = YamlEvalSetImporter(File("/nonexistent/path"))
        val result = importer.importAll()
        assertThat(result.errors).isNotEmpty()
    }

    @Test
    fun `importAll - malformed YAML reports error`() {
        writeEvalYaml("bad-profile", "eval-bad.yaml", "{{invalid yaml: [")
        val importer = YamlEvalSetImporter(tempDir)
        val result = importer.importAll()
        assertThat(result.errors).isNotEmpty()
    }

    @Test
    fun `parseYamlToTask - infers difficulty from quality criteria`() {
        writeEvalYaml("hard-profile", "eval-001.yaml", """
            id: eval-hard
            name: "Hard Task"
            prompt: "do hard thing"
            assertions: []
            quality_criteria:
              completeness: 0.95
              correctness: 0.95
        """.trimIndent())

        val importer = YamlEvalSetImporter(tempDir)
        val result = importer.importAll()
        val task = result.tasksBySuite.values.first().first()
        assertThat(task.difficulty).isEqualTo(Difficulty.HARD)
    }

    @Test
    fun `parseYamlToTask - adds profile tag`() {
        writeEvalYaml("planning-profile", "eval-001.yaml", """
            id: eval-plan
            name: "Plan Task"
            prompt: "plan something"
            tags: [planning, mvp]
            assertions: []
        """.trimIndent())

        val importer = YamlEvalSetImporter(tempDir)
        val result = importer.importAll()
        val task = result.tasksBySuite.values.first().first()
        assertThat(task.tags).contains("planning")
    }
}
