package dev.qtremors.acqua.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VerifyVersionCatalogStructureTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val versionCatalog: RegularFileProperty

    @TaskAction
    fun verify() {
        val catalog = versionCatalog.get().asFile
        require(catalog.exists()) {
            "Missing gradle/libs.versions.toml; the build requires a version catalog."
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

    @get:Input
    abstract val expectedApplicationId: Property<String>

    @TaskAction
    fun verify() {
        val buildFileText = appBuildFile.get().asFile.readText()
        val versionName = Regex("""versionName\s*=\s*"([^"]+)"""")
            .find(buildFileText)
            ?.groupValues
            ?.get(1)
        require(versionName != null) {
            "app/build.gradle.kts must declare versionName."
        }
        require(Regex("""\d+\.\d+\.\d+""").matches(versionName)) {
            "versionName must use numeric major.minor.patch form; found '$versionName'."
        }
        val versionCode = Regex("""versionCode\s*=\s*(\d+)""")
            .find(buildFileText)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
        require(versionCode != null) {
            "app/build.gradle.kts must declare versionCode."
        }
        val derivedVersionCode = versionName.replace(".", "").toInt()
        require(versionCode == derivedVersionCode) {
            "versionCode must be versionName with dots removed: expected $derivedVersionCode, found $versionCode."
        }
        val applicationId = Regex("""applicationId\s*=\s*"([^"]+)"""")
            .find(buildFileText)
            ?.groupValues
            ?.get(1)
        require(applicationId == expectedApplicationId.get()) {
            "applicationId must be '${expectedApplicationId.get()}', found '${applicationId ?: "missing"}'."
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
        val suspiciousPatterns = listOf(
            Regex(
                """(Text\(\s*"[^"]*[A-Za-z][^"]*"|contentDescription\s*=\s*"[^"]*[A-Za-z][^"]*"|placeholder\s*=\s*"[^"]*[A-Za-z][^"]*"|title\s*=\s*"[^"]*[A-Za-z][^"]*"|Toast\.makeText\([^,]+,\s*"[^"]*[A-Za-z][^"]*"|createChooser\([^,]+,\s*"[^"]*[A-Za-z][^"]*"|fileOperationStatusMessage\s*=\s*"[^"]*[A-Za-z][^"]*"|setContentTitle\("([^"]*[A-Za-z][^"]*)"|setContentText\("([^"]*[A-Za-z][^"]*)"|addAction\([^"]*"[^"]*[A-Za-z][^"]*")"""
            ),
            Regex("""\b(?:message|errorMessage|statusMessage)\s*=\s*"[^"]*[A-Za-z][^"]*"""),
            Regex("""\b(?:showMessage|showSnackbar)\(\s*"[^"]*[A-Za-z][^"]*"""),
            Regex("""\b(?:Error|Failure)\(\s*"[^"]*[A-Za-z][^"]*"""),
            Regex("""\?:\s*"[^"]*[A-Za-z][^"]*"""),
            Regex("""\bsetProgress\([^\n]*"[^"]*[A-Za-z][^"]*"""")
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
            "download_",
            "?: \"jpg\"",
            "?: \"#",
            "Acqua-$",
            "Acqua ",
            "AcquaError",
            "Sample video",
            "Sample song",
            "ClipData.newPlainText",
            "https://",
            "http://"
        )
        val baseDir = projectDirectory.get().asFile
        val offenders = sourceFiles.files.flatMap { sourceFile ->
            val relativePath = sourceFile.relativeTo(baseDir).invariantSeparatorsPath
            sourceFile.readLines().mapIndexedNotNull { index, line ->
                val trimmed = line.trim()
                if (suspiciousPatterns.any { it.containsMatchIn(trimmed) } &&
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
            val verifyVersionCatalogStructure = tasks.register("verifyVersionCatalogStructure", VerifyVersionCatalogStructureTask::class.java) {
                group = "verification"
                description = "Verifies that the required version catalog sections are present."
                versionCatalog.set(rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"))
            }

            val verifyReleaseVersionMetadata = tasks.register("verifyReleaseVersionMetadata", VerifyReleaseVersionMetadataTask::class.java) {
                group = "verification"
                description = "Checks numeric version agreement and the official application ID."
                appBuildFile.set(layout.projectDirectory.file("build.gradle.kts"))
                expectedApplicationId.set("dev.qtremors.acqua")
            }

            val checkProductionStrings = if (rootProject.tasks.findByName("checkProductionStrings") == null) {
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
                            exclude("build-logic/**")
                            exclude("**/ui/theme/**")
                        }
                    )
                }
            } else rootProject.tasks.named("checkProductionStrings")

            val productionKotlin = rootProject.fileTree(rootProject.projectDir) {
                include("app/src/main/java/**/*.kt")
                include("core/**/src/main/kotlin/**/*.kt")
                include("feature/**/src/main/kotlin/**/*.kt")
                exclude("**/build/**")
            }
            val checkKotlinFormat = rootProject.tasks.register("checkKotlinFormat", CheckKotlinFormatTask::class.java) {
                group = "verification"
                description = "Checks deterministic Kotlin whitespace formatting."
                projectDirectory.set(rootProject.layout.projectDirectory)
                sourceFiles.from(productionKotlin)
            }
            rootProject.tasks.register("formatKotlinSources", FormatKotlinSourcesTask::class.java) {
                group = "formatting"
                description = "Normalizes Kotlin trailing whitespace, tabs, and final newlines."
                sourceFiles.from(productionKotlin)
            }
            val analyzeKotlinSources = rootProject.tasks.register("analyzeKotlinSources", AnalyzeKotlinSourcesTask::class.java) {
                group = "verification"
                description = "Enforces Kotlin naming, wildcard-import, catch, and complexity growth budgets."
                projectDirectory.set(rootProject.layout.projectDirectory)
                sourceFiles.from(productionKotlin)
                baselineFile.set(rootProject.layout.projectDirectory.file("config/kotlin-quality-baseline.properties"))
            }
            val verifyStaticSite = rootProject.tasks.register("verifyStaticSite", VerifyStaticSiteTask::class.java) {
                group = "verification"
                description = "Validates static HTML semantics, links, assets, CSS, and JavaScript structure."
                siteDirectory.set(rootProject.layout.projectDirectory.dir("../docs"))
            }

            val verifyAcquaBuildConventions = tasks.register("verifyAcquaBuildConventions") {
                group = "verification"
                description = "Runs Acqua build convention checks used for release readiness."
                dependsOn(
                    verifyVersionCatalogStructure,
                    verifyReleaseVersionMetadata,
                    checkProductionStrings,
                    checkKotlinFormat,
                    analyzeKotlinSources,
                    verifyStaticSite
                )
            }
            tasks.matching { it.name == "check" }.configureEach {
                dependsOn(verifyAcquaBuildConventions)
            }
        }
    }
}
