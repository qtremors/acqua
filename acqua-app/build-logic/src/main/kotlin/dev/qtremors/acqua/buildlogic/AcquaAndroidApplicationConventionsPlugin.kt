package dev.qtremors.acqua.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyVersionCatalogFreshnessTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val versionCatalog: RegularFileProperty

    @TaskAction
    fun verify() {
        val catalog = versionCatalog.get().asFile
        require(catalog.exists()) {
            "Missing gradle/libs.versions.toml; dependency freshness checks need a version catalog."
        }
        val text = catalog.readText()
        require("[versions]" in text && "[libraries]" in text && "[plugins]" in text) {
            "gradle/libs.versions.toml must keep [versions], [libraries], and [plugins] sections."
        }
    }
}

abstract class VerifyReleaseVersionMetadataTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val appBuildFile: RegularFileProperty

    @TaskAction
    fun verify() {
        val buildFileText = appBuildFile.get().asFile.readText()
        require(Regex("""versionName\s*=\s*"[^"]+"""").containsMatchIn(buildFileText)) {
            "app/build.gradle.kts must declare versionName."
        }
        require(Regex("""versionCode\s*=\s*\d+""").containsMatchIn(buildFileText)) {
            "app/build.gradle.kts must declare versionCode."
        }
    }
}

abstract class CheckProductionStringsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val suspiciousPattern = Regex(
            """(Text\(\s*"[^"]*[A-Za-z][^"]*"|contentDescription\s*=\s*"[^"]*[A-Za-z][^"]*"|placeholder\s*=\s*"[^"]*[A-Za-z][^"]*"|title\s*=\s*"[^"]*[A-Za-z][^"]*"|Toast\.makeText\([^,]+,\s*"[^"]*[A-Za-z][^"]*"|createChooser\([^,]+,\s*"[^"]*[A-Za-z][^"]*"|fileOperationStatusMessage\s*=\s*"[^"]*[A-Za-z][^"]*"|setContentTitle\("([^"]*[A-Za-z][^"]*)"|setContentText\("([^"]*[A-Za-z][^"]*)"|addAction\([^"]*"[^"]*[A-Za-z][^"]*")"""
        )
        val allowedFragments = listOf(
            "android.os.Build.",
            "Text(\".\${",
            "Text(\"\${",
            "AppLogger.",
            "Regex(",
            "SimpleDateFormat(",
            "DateTimeFormatter",
            "ImageRequest.Builder",
            "mutableStateOf(\"\")",
            "SavedStateHandle",
            "MIME",
            "mimeType",
            "contentType =",
            "label =",
            "label = \"",
            "label = {",
            "cacheKey",
            "content://",
            "Acqua-$",
            "Acqua ",
            "AcquaError",
            "Sample video",
            "ClipData.newPlainText",
            "https://",
            "http://"
        )
        val baseDir = projectDirectory.get().asFile
        val offenders = sourceFiles.files.flatMap { sourceFile ->
            val relativePath = sourceFile.relativeTo(baseDir).invariantSeparatorsPath
            sourceFile.readLines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                if (suspiciousPattern.containsMatchIn(trimmed) &&
                    allowedFragments.none { trimmed.contains(it) } &&
                    !trimmed.contains("R.string.") &&
                    !trimmed.contains("R.plurals.") &&
                    !trimmed.contains("stringResource(") &&
                    !trimmed.contains("pluralStringResource(") &&
                    !trimmed.contains("getString(")
                ) {
                    "$relativePath:${index + 1}: $trimmed"
                } else {
                    null
                }
            }
        }
        if (offenders.isNotEmpty()) {
            throw GradleException(buildString {
                appendLine("Found hardcoded production UI strings:")
                offenders.forEach { appendLine(it) }
            })
        }
    }
}

class AcquaAndroidApplicationConventionsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val verifyVersionCatalogFreshness = tasks.register("verifyVersionCatalogFreshness", VerifyVersionCatalogFreshnessTask::class.java) {
                group = "verification"
                description = "Verifies that dependency freshness checks have an active version catalog to inspect."
                versionCatalog.set(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
            }

            val verifyReleaseVersionMetadata = tasks.register("verifyReleaseVersionMetadata", VerifyReleaseVersionMetadataTask::class.java) {
                group = "verification"
                description = "Checks that the app declares explicit versionName and versionCode metadata."
                appBuildFile.set(layout.projectDirectory.file("build.gradle.kts"))
            }

            if (rootProject.tasks.findByName("checkProductionStrings") == null) {
                rootProject.tasks.register("checkProductionStrings", CheckProductionStringsTask::class.java) {
                    group = "verification"
                    description = "Flags obvious hardcoded production UI strings across production Android sources."
                    projectDirectory.set(rootProject.layout.projectDirectory)
                    sourceFiles.from(
                        rootProject.fileTree(rootProject.projectDir) {
                            include("**/src/main/java/**/*.kt")
                            include("**/src/main/kotlin/**/*.kt")
                            exclude("**/build/**")
                            exclude("**/src/test/**")
                            exclude("**/src/androidTest/**")
                            exclude("**/ui/theme/**")
                        }
                    )
                }
            }

            tasks.register("verifyAcquaBuildConventions") {
                group = "verification"
                description = "Runs Acqua build convention checks used for release readiness."
                dependsOn(verifyVersionCatalogFreshness, verifyReleaseVersionMetadata)
            }
        }
    }
}
