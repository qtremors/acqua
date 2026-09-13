package dev.qtremors.acqua

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ArchitectureBoundaryTest {
    @Test
    fun `domain remains platform neutral`() {
        noClasses()
            .that().resideInAPackage("dev.qtremors.acqua.domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("android..", "androidx..")
            .check(productionClasses)
    }

    @Test
    fun `data does not depend on presentation`() {
        noClasses()
            .that().resideInAPackage("dev.qtremors.acqua.data..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("dev.qtremors.acqua.ui..", "dev.qtremors.acqua.feature..")
            .check(productionClasses)
    }

    @Test
    fun `shared UI does not depend on features or data implementations`() {
        noClasses()
            .that().resideInAPackage("dev.qtremors.acqua.ui..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("dev.qtremors.acqua.feature..", "dev.qtremors.acqua.data..")
            .check(productionClasses)
    }

    @Test
    fun `source ownership and dependency rules stay enforced`() {
        val failures = mutableListOf<String>()
        productionSources.forEach { source ->
            val lines = source.readLines()
            val packageName = lines.firstOrNull { it.startsWith("package ") }
                ?.removePrefix("package ")
                ?.trim()
                .orEmpty()
            val expectedSuffix = packageName.replace('.', File.separatorChar)
            if (packageName.isBlank() || !requireNotNull(source.parentFile).path.endsWith(expectedSuffix)) {
                failures += "${source.relativeTo(projectRoot)}:1: package/path ownership mismatch"
            }

            val sourceFeature = featureOwner(packageName)
            lines.forEachIndexed { index, line ->
                val imported = IMPORT.find(line)?.groupValues?.get(1) ?: return@forEachIndexed
                val targetFeature = featureOwner(imported)
                if (sourceFeature != null && targetFeature != null && sourceFeature != targetFeature) {
                    failures += "${source.relativeTo(projectRoot)}:${index + 1}: feature '$sourceFeature' imports '$targetFeature'"
                }
                if (packageName.startsWith("dev.qtremors.acqua.data.") &&
                    (imported.startsWith("dev.qtremors.acqua.ui.") || imported.startsWith("dev.qtremors.acqua.feature."))
                ) {
                    failures += "${source.relativeTo(projectRoot)}:${index + 1}: data imports presentation"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `production source avoids responsibility bucket names`() {
        val invalid = productionSources.filter { source ->
            GENERIC_FILE.matches(source.nameWithoutExtension)
        }
        assertTrue(
            invalid.joinToString("\n") { it.relativeTo(projectRoot).path },
            invalid.isEmpty()
        )
    }

    @Test
    fun `production source stays within responsibility budgets`() {
        val failures = productionSources.mapNotNull { source ->
            val lines = source.readLines().size
            val limit = if (source.nameWithoutExtension.endsWith("ViewModel")) 500 else 700
            if (lines > limit) {
                "${source.relativeTo(projectRoot)}:1: $lines lines exceeds the $limit-line budget"
            } else null
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `public composables expose focused contracts`() {
        val failures = productionSources.flatMap { source ->
            PUBLIC_COMPOSABLE.findAll(source.readText()).mapNotNull { match ->
                val count = parameterCount(match.groupValues[2])
                if (count > MAX_PUBLIC_COMPOSABLE_PARAMETERS) {
                    "${source.relativeTo(projectRoot)}: ${match.groupValues[1]} exposes $count parameters"
                } else null
            }.toList()
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `composables do not construct infrastructure`() {
        val failures = productionSources.flatMap { source ->
            val text = source.readText()
            if ("@Composable" !in text) return@flatMap emptyList()
            text.lineSequence().mapIndexedNotNull { index, line ->
                if (INFRASTRUCTURE_CONSTRUCTION.containsMatchIn(line) &&
                    source.name !in COMPOSABLE_CONSTRUCTION_BASELINE
                ) {
                    "${source.relativeTo(projectRoot)}:${index + 1}: infrastructure construction in Compose"
                } else {
                    null
                }
            }.toList()
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    companion object {
        private lateinit var projectRoot: File
        private lateinit var productionSources: List<File>
        private lateinit var productionClasses: JavaClasses

        private val IMPORT = Regex("^import\\s+(dev\\.qtremors\\.acqua\\.[A-Za-z0-9_.]+)")
        private val GENERIC_FILE = Regex(".*(?:Models|Utils|Helpers|Contracts|StateSlices)")
        private val PUBLIC_COMPOSABLE = Regex(
            "@Composable\\s+(?:@[A-Za-z0-9_.() :,]+\\s+)*fun\\s+([A-Za-z0-9_]+)\\s*\\((.*?)\\)\\s*\\{",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        private val INFRASTRUCTURE_CONSTRUCTION = Regex(
            "\\b(?:[A-Z][A-Za-z0-9]*(?:Repository|Manager|Updater|Engine|Storage)|HttpMediaClient)\\s*\\("
        )
        private val COMPOSABLE_CONSTRUCTION_BASELINE = emptySet<String>()
        private const val MAX_PUBLIC_COMPOSABLE_PARAMETERS = 15

        @JvmStatic
        @BeforeClass
        fun loadArchitecture() {
            projectRoot = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
                .first { File(it, "settings.gradle.kts").isFile }
            productionSources = listOf("app", "core")
                .flatMap { moduleRoot ->
                    File(projectRoot, moduleRoot).walkTopDown().filter { source ->
                        source.isFile && source.extension == "kt" &&
                            source.invariantSeparatorsPath.contains("/src/main/")
                    }.toList()
                }
            val productionClassDirectories = listOf(
                File(projectRoot, "app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
                File(projectRoot, "app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
                File(projectRoot, "core/domain/build/classes/kotlin/main"),
                File(projectRoot, "core/domain/build/classes/java/main")
            ).filter(File::isDirectory)
            productionClasses = ClassFileImporter().importPaths(
                productionClassDirectories.map { it.toPath() }
            )
        }

        private fun featureOwner(packageName: String): String? {
            val marker = "dev.qtremors.acqua.feature."
            if (!packageName.startsWith(marker)) return null
            val relativeName = packageName.removePrefix(marker)
            if ('.' !in relativeName) return null
            return relativeName.substringBefore('.')
        }

        private fun parameterCount(parameters: String): Int {
            if (parameters.isBlank()) return 0
            var parentheses = 0
            var angles = 0
            var braces = 0
            var count = 1
            parameters.forEach { character ->
                when (character) {
                    '(' -> parentheses++
                    ')' -> parentheses--
                    '<' -> angles++
                    '>' -> angles--
                    '{' -> braces++
                    '}' -> braces--
                    ',' -> if (parentheses == 0 && angles == 0 && braces == 0) count++
                }
            }
            return count
        }
    }
}
