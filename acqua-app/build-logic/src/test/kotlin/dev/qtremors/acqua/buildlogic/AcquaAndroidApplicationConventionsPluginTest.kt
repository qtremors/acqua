package dev.qtremors.acqua.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcquaAndroidApplicationConventionsPluginTest {
    @Test
    fun `release metadata accepts matching numeric version and application identity`() {
        val projectDir = testProject(
            appMetadata = """
                val applicationId = "dev.qtremors.acqua"
                val versionCode = 24
                val versionName = "0.2.4"
            """.trimIndent()
        )

        val result = runner(
            projectDir,
            "verifyReleaseVersionMetadata",
            "verifyVersionCatalogStructure"
        ).build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyReleaseVersionMetadata")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyVersionCatalogStructure")?.outcome)
    }

    @Test
    fun `release metadata rejects malformed version`() {
        val projectDir = testProject(
            appMetadata = """
                val applicationId = "dev.qtremors.acqua"
                val versionCode = 24
                val versionName = "0.2.beta"
            """.trimIndent()
        )

        val result = runner(projectDir, "verifyReleaseVersionMetadata").buildAndFail()

        assertTrue(result.output.contains("numeric major.minor.patch"))
    }

    @Test
    fun `release metadata rejects mismatched version code`() {
        val projectDir = testProject(
            appMetadata = """
                val applicationId = "dev.qtremors.acqua"
                val versionCode = 25
                val versionName = "0.2.4"
            """.trimIndent()
        )

        val result = runner(projectDir, "verifyReleaseVersionMetadata").buildAndFail()

        assertTrue(result.output.contains("expected 24, found 25"))
    }

    @Test
    fun `release metadata rejects wrong application identity`() {
        val projectDir = testProject(
            appMetadata = """
                val applicationId = "example.invalid"
                val versionCode = 24
                val versionName = "0.2.4"
            """.trimIndent()
        )

        val result = runner(projectDir, "verifyReleaseVersionMetadata").buildAndFail()

        assertTrue(result.output.contains("applicationId must be 'dev.qtremors.acqua'"))
    }

    @Test
    fun `catalog structure rejects a missing required section`() {
        val projectDir = testProject(
            appMetadata = """
                val applicationId = "dev.qtremors.acqua"
                val versionCode = 24
                val versionName = "0.2.4"
            """.trimIndent(),
            catalogText = """
                [versions]
                sample = "1.0"

                [libraries]
                sample = { module = "example:sample", version.ref = "sample" }
            """.trimIndent()
        )

        val result = runner(projectDir, "verifyVersionCatalogStructure").buildAndFail()

        assertTrue(result.output.contains("must keep [versions], [libraries], and [plugins] sections"))
    }

    @Test
    fun `production string check fails for hardcoded feature ui string`() {
        val projectDir = testProject(
            appMetadata = """
                val applicationId = "dev.qtremors.acqua"
                val versionCode = 24
                val versionName = "0.2.4"
            """.trimIndent()
        )
        val source = projectDir
            .resolve("feature/sample/src/main/java/dev/qtremors/acqua/feature/sample/SampleScreen.kt")
        source.parent.createDirectories()
        source.writeText(
            """
            package dev.qtremors.acqua.feature.sample

            fun SampleScreen() {
                Text("Hardcoded production string")
            }
            """.trimIndent()
        )

        val result = runner(projectDir, "checkProductionStrings").buildAndFail()

        assertEquals(TaskOutcome.FAILED, result.task(":checkProductionStrings")?.outcome)
        assertTrue(result.output.contains("Found hardcoded production UI strings:"))
        assertTrue(result.output.contains("feature/sample/src/main/java/dev/qtremors/acqua/feature/sample/SampleScreen.kt:4"))
    }

    @Test
    fun `Kotlin analysis rejects wildcard import growth`() {
        val projectDir = testProject(validMetadata)
        val source = projectDir.resolve("app/src/main/java/sample/Sample.kt")
        source.parent.createDirectories()
        source.writeText(
            """
            package sample

            import example.*

            fun sample() = Unit
            """.trimIndent()
        )

        val result = runner(projectDir, "analyzeKotlinSources").buildAndFail()

        assertTrue(result.output.contains("wildcardImports grew from an allowed 0 to 1"))
        assertTrue(result.output.contains("Sample.kt:3"))
    }

    @Test
    fun `Kotlin formatter repairs deterministic whitespace`() {
        val projectDir = testProject(validMetadata)
        val source = projectDir.resolve("app/src/main/java/sample/Sample.kt")
        source.parent.createDirectories()
        source.writeText("package sample  \n\tfun sample() = Unit")

        val failed = runner(projectDir, "checkKotlinFormat").buildAndFail()
        assertTrue(failed.output.contains("trailing whitespace"))
        assertTrue(failed.output.contains("tab indentation"))

        runner(projectDir, "formatKotlinSources").build()
        val passed = runner(projectDir, "checkKotlinFormat").build()
        assertEquals(TaskOutcome.SUCCESS, passed.task(":checkKotlinFormat")?.outcome)
    }

    @Test
    fun `production quality checks cover both source roots in every module`() {
        val projectDir = testProject(validMetadata)
        val paths = listOf("app", "core/sample", "feature/sample").flatMap { module ->
            listOf("java", "kotlin").map { root -> "$module/src/main/$root/sample/Sample.kt" }
        }
        val excluded = listOf(
            "feature/sample/src/test/java/sample/Test.kt",
            "core/sample/src/androidTest/kotlin/sample/Test.kt",
            "core/sample/build/generated/src/main/java/sample/Generated.kt"
        )
        (paths + excluded).forEach { path ->
            projectDir.resolve(path).also { source ->
                source.parent.createDirectories()
                source.writeText("package sample  \nimport example.*\n\tfun sample() = Unit")
            }
        }
        projectDir.resolve("build.gradle.kts").toFile().appendText(
            """

            tasks.register("verifyProductionInventory") {
                doLast {
                    val expected = setOf(${paths.joinToString { "\"$it\"" }})
                    listOf("checkKotlinFormat", "formatKotlinSources", "analyzeKotlinSources").forEach { name ->
                        val actual = tasks.getByName(name).inputs.files.files.filter { it.extension == "kt" }
                            .map { it.relativeTo(projectDir).invariantSeparatorsPath }.toSet()
                        check(actual == expected) { "Source inventory mismatch for ${'$'}name: ${'$'}actual" }
                    }
                }
            }
            """.trimIndent()
        )

        val failed = runner(projectDir, "verifyProductionInventory", "checkKotlinFormat", "analyzeKotlinSources", "--continue")
            .buildAndFail()
        assertEquals(TaskOutcome.SUCCESS, failed.task(":verifyProductionInventory")?.outcome)
        assertTrue(failed.output.contains("wildcardImports grew from an allowed 0 to 6"))
        paths.forEach { assertTrue(failed.output.contains("$it:1: trailing whitespace")) }

        runner(projectDir, "formatKotlinSources").build()
        paths.forEach { path ->
            val source = projectDir.resolve(path).toFile()
            assertEquals("package sample\nimport example.*\n    fun sample() = Unit\n", source.readText())
            source.writeText(source.readText().replace("import example.*\n", ""))
        }
        excluded.forEach { assertTrue(projectDir.resolve(it).toFile().readText().contains('\t')) }
        val passed = runner(projectDir, "checkKotlinFormat", "analyzeKotlinSources").build()
        assertEquals(TaskOutcome.SUCCESS, passed.task(":analyzeKotlinSources")?.outcome)
    }

    @Test
    fun `static site verification rejects broken assets and invalid CSS`() {
        val projectDir = testProject(validMetadata)
        val docs = projectDir.parent.resolve("docs").createDirectories()
        docs.resolve("index.html").writeText(
            """
            <!doctype html>
            <html lang="en"><body><main id="main">
            <img src="missing.svg" alt="Sample" width="10" height="10">
            </main></body></html>
            """.trimIndent()
        )
        docs.resolve("styles.css").writeText(".sample {\n  color red;\n}\n")

        val result = runner(projectDir, "verifyStaticSite").buildAndFail()

        assertTrue(result.output.contains("missing local asset 'missing.svg'"))
        assertTrue(result.output.contains("property is missing ':'"))
    }

    private fun testProject(
        appMetadata: String,
        catalogText: String = """
            [versions]
            sample = "1.0"

            [libraries]
            sample = { module = "example:sample", version.ref = "sample" }

            [plugins]
            sample = { id = "example.sample", version.ref = "sample" }
        """.trimIndent()
    ) = Files.createTempDirectory("acqua-build-logic-test")
        .resolve("acqua-app")
        .createDirectories()
        .also { projectDir ->
        projectDir.resolve("settings.gradle.kts").writeText("")
        projectDir.resolve("gradle/libs.versions.toml").also { catalog ->
            catalog.parent.createDirectories()
            catalog.writeText(catalogText)
        }
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("acqua.android.application.conventions")
            }

            $appMetadata
            """.trimIndent()
        )
        projectDir.resolve("config/kotlin-quality-baseline.properties").also { baseline ->
            baseline.parent.createDirectories()
            baseline.writeText(
                """
                wildcardImports=0
                broadCatches=0
                longFunctions=0
                crowdedFiles=0
                """.trimIndent()
            )
        }
    }

    private fun runner(projectDir: java.nio.file.Path, vararg arguments: String) =
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withArguments(*arguments)

    private companion object {
        val validMetadata = """
            val applicationId = "dev.qtremors.acqua"
            val versionCode = 24
            val versionName = "0.2.4"
        """.trimIndent()
    }
}
