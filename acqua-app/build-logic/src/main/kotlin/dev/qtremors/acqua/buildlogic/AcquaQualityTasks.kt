package dev.qtremors.acqua.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties

abstract class CheckKotlinFormatTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val base = projectDirectory.get().asFile
        val violations = sourceFiles.files.sorted().flatMap { file ->
            val relative = file.relativeTo(base).invariantSeparatorsPath
            buildList {
                file.readLines().forEachIndexed { index, line ->
                    if (line != line.trimEnd()) add("$relative:${index + 1}: trailing whitespace")
                    if ('\t' in line) add("$relative:${index + 1}: tab indentation")
                }
                if (file.readBytes().isNotEmpty() && !file.readText().endsWith("\n")) {
                    add("$relative: missing final newline")
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Kotlin formatting violations:\n${violations.joinToString("\n")}\n" +
                    "Run formatKotlinSources to repair deterministic whitespace issues."
            )
        }
    }
}

abstract class FormatKotlinSourcesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @TaskAction
    fun format() {
        sourceFiles.files.sorted().forEach { file ->
            val original = file.readText()
            val formatted = original.lineSequence()
                .joinToString("\n") { line -> line.trimEnd().replace("\t", "    ") }
                .trimEnd('\n') + "\n"
            if (formatted != original) file.writeText(formatted)
        }
    }
}

abstract class AnalyzeKotlinSourcesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFile: RegularFileProperty

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @TaskAction
    fun analyze() {
        val base = projectDirectory.get().asFile
        val baseline = Properties().apply { baselineFile.get().asFile.inputStream().use(::load) }
        val wildcardImports = mutableListOf<String>()
        val broadCatches = mutableListOf<String>()
        val longFunctions = mutableListOf<String>()
        val crowdedFiles = mutableListOf<String>()
        val badNames = mutableListOf<String>()

        sourceFiles.files.sorted().forEach { file ->
            val relative = file.relativeTo(base).invariantSeparatorsPath
            val lines = file.readLines()
            if (!Regex("[A-Z][A-Za-z0-9]*\\.kt").matches(file.name)) {
                badNames += "$relative: production Kotlin filenames must use PascalCase"
            }
            lines.forEachIndexed { index, line ->
                if (Regex("^import\\s+.+\\*$").matches(line.trim())) {
                    wildcardImports += "$relative:${index + 1}: ${line.trim()}"
                }
                if (Regex("catch\\s*\\([^)]*:\\s*(Exception|Throwable)\\)").containsMatchIn(line)) {
                    broadCatches += "$relative:${index + 1}: ${line.trim()}"
                }
            }
            val functions = functionSpans(lines)
            functions.filter { it.lineCount > 120 }.forEach { function ->
                longFunctions += "$relative:${function.startLine}: ${function.name} is ${function.lineCount} lines"
            }
            if (functions.size > 30) {
                crowdedFiles += "$relative: ${functions.size} functions (limit 30)"
            }
        }

        val violations = buildList {
            addAll(badNames)
            compareToBaseline("wildcardImports", wildcardImports, baseline, this)
            compareToBaseline("broadCatches", broadCatches, baseline, this)
            compareToBaseline("longFunctions", longFunctions, baseline, this)
            compareToBaseline("crowdedFiles", crowdedFiles, baseline, this)
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Kotlin quality analysis failed:\n${violations.joinToString("\n")}")
        }
        logger.lifecycle(
            "Kotlin quality debt: wildcard imports={}, broad catches={}, long functions={}, crowded files={}",
            wildcardImports.size,
            broadCatches.size,
            longFunctions.size,
            crowdedFiles.size
        )
    }

    private fun compareToBaseline(
        key: String,
        findings: List<String>,
        baseline: Properties,
        violations: MutableList<String>
    ) {
        val allowed = baseline.getProperty(key)?.toIntOrNull()
            ?: throw GradleException("Kotlin quality baseline is missing '$key'.")
        if (findings.size > allowed) {
            violations += "$key grew from an allowed $allowed to ${findings.size}:"
            violations += findings.take(20)
        }
    }

    private fun functionSpans(lines: List<String>): List<FunctionSpan> {
        val spans = mutableListOf<FunctionSpan>()
        var pendingName: String? = null
        var pendingStart = -1
        var activeName: String? = null
        var activeStart = -1
        var depth = 0
        lines.forEachIndexed { index, line ->
            if (activeName == null && pendingName == null) {
                Regex("""\bfun\s+(?:<[^>]+>\s*)?([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
                    .find(line)
                    ?.let {
                        pendingName = it.groupValues[1]
                        pendingStart = index
                    }
            }
            if (activeName == null && pendingName != null && '=' in line && '{' !in line) {
                pendingName = null
                pendingStart = -1
            } else if (activeName == null && pendingName != null && '{' in line) {
                activeName = pendingName
                activeStart = pendingStart
                pendingName = null
                depth = braceDelta(line)
                if (depth <= 0) {
                    spans += FunctionSpan(activeName!!, activeStart + 1, index - activeStart + 1)
                    activeName = null
                }
            } else if (activeName != null) {
                depth += braceDelta(line)
                if (depth <= 0) {
                    spans += FunctionSpan(activeName!!, activeStart + 1, index - activeStart + 1)
                    activeName = null
                }
            }
        }
        return spans
    }

    private fun braceDelta(line: String): Int = line.count { it == '{' } - line.count { it == '}' }

    private data class FunctionSpan(val name: String, val startLine: Int, val lineCount: Int)
}

abstract class VerifyStaticSiteTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val siteDirectory: DirectoryProperty

    @TaskAction
    fun verify() {
        val directory = siteDirectory.get().asFile
        val index = directory.resolve("index.html")
        require(index.isFile) { "docs/index.html is missing." }
        val html = index.readText()
        val violations = mutableListOf<String>()
        if (!Regex("<html[^>]+lang=", RegexOption.IGNORE_CASE).containsMatchIn(html)) {
            violations += "index.html: <html> must declare a language"
        }
        if (Regex("<main(?:\\s|>)", RegexOption.IGNORE_CASE).findAll(html).count() != 1) {
            violations += "index.html: expected exactly one <main> landmark"
        }
        val ids = Regex("""\bid="([^"]+)""", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.groupValues[1] }.toList()
        ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            violations += "index.html: duplicate id '$it'"
        }
        Regex("""(?:href|src)="([^"]+)""", RegexOption.IGNORE_CASE).findAll(html).forEach { match ->
            val target = match.groupValues[1]
            when {
                target.startsWith("#") -> if (target.drop(1) !in ids) {
                    violations += "index.html: fragment '$target' has no matching id"
                }
                target.startsWith("https://") -> runCatching { java.net.URI(target) }.onFailure {
                    violations += "index.html: malformed external URL '$target'"
                }
                target.startsWith("http://") -> violations += "index.html: external URL must use HTTPS: '$target'"
                target.startsWith("mailto:") || target.startsWith("tel:") -> Unit
                else -> {
                    val path = target.substringBefore('#').substringBefore('?')
                    if (path.isNotBlank() && !directory.resolve(path).normalize().isFile) {
                        violations += "index.html: missing local asset '$path'"
                    }
                }
            }
        }
        Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).forEach { image ->
            val tag = image.value
            if (!Regex("\\balt=").containsMatchIn(tag)) violations += "index.html: image is missing alt text"
            if (!Regex("\\bwidth=").containsMatchIn(tag) || !Regex("\\bheight=").containsMatchIn(tag)) {
                violations += "index.html: image is missing intrinsic width/height"
            }
        }
        directory.walkTopDown().filter { it.isFile && it.extension == "css" }.forEach { css ->
            val text = css.readText()
            if (braceBalance(text) != 0) violations += "${css.name}: unbalanced CSS braces"
            Regex("(?m)^\\s*[a-zA-Z-]+\\s+[^:{}]+;").findAll(text).forEach { invalid ->
                violations += "${css.name}:${text.take(invalid.range.first).count { it == '\n' } + 1}: property is missing ':'"
            }
        }
        directory.walkTopDown().filter { it.isFile && it.extension == "js" }.forEach { script ->
            val text = script.readText()
            if (braceBalance(text) != 0) violations += "${script.name}: unbalanced JavaScript braces"
            if (Regex("\\b(?:eval|document\\.write)\\s*\\(").containsMatchIn(text)) {
                violations += "${script.name}: unsafe dynamic script execution is forbidden"
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Static site verification failed:\n${violations.joinToString("\n")}")
        }
    }

    private fun braceBalance(text: String): Int = text.count { it == '{' } - text.count { it == '}' }
}
