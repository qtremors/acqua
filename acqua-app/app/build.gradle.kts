import com.android.build.api.variant.FilterConfiguration
import java.util.Properties
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class GenerateLegalAssetsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun generate() {
        fileSystemOperations.sync {
            from(sourceFiles)
            into(outputDirectory)
        }
    }
}

abstract class VerifyReleaseArtifactsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val artifactsDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseIdentityFile: RegularFileProperty

    @get:Input
    abstract val expectedArtifactNames: ListProperty<String>

    @get:Input
    abstract val requireOfficialSignature: org.gradle.api.provider.Property<Boolean>

    @get:Input
    abstract val rootDirectoryPath: org.gradle.api.provider.Property<String>

    @TaskAction
    fun verify() {
        val identity = Properties().apply {
            releaseIdentityFile.get().asFile.inputStream().use(::load)
        }
        val expectedApplicationId = identity.getProperty("applicationId")
            ?.takeIf(String::isNotBlank)
            ?: throw GradleException("release-identity.properties must define applicationId.")
        val expectedCertificate = identity.getProperty("certificateSha256")
            ?.filter(Char::isLetterOrDigit)
            ?.lowercase()
            ?.takeIf { it.length == 64 }
            ?: throw GradleException("release-identity.properties must define a 64-character certificateSha256.")

        val apks = artifactsDirectory.get().asFile.walkTopDown()
            .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
            .toList()
        val actualNames = apks.map { it.name }.sorted()
        val expectedNames = expectedArtifactNames.get().sorted()
        if (actualNames != expectedNames) {
            throw GradleException(
                "Release APK set mismatch. Expected ${expectedNames.joinToString()}, " +
                    "found ${actualNames.joinToString().ifBlank { "none" }}."
            )
        }
        apks.forEach { apk ->
            require(apk.length() > 0L) { "Release APK is empty: ${apk.name}" }
        }

        val sdkDirectory = locateAndroidSdk(File(rootDirectoryPath.get()))
        val buildTools = sdkDirectory.resolve("build-tools").listFiles()
            ?.filter(File::isDirectory)
            ?.maxByOrNull { directory -> versionKey(directory.name) }
            ?: throw GradleException("No Android SDK build-tools directory found under $sdkDirectory.")
        val executableSuffix = if (System.getProperty("os.name").startsWith("Windows", true)) ".exe" else ""
        val aapt2 = buildTools.resolve("aapt2$executableSuffix")
        val apksigner = buildTools.resolve(if (executableSuffix.isEmpty()) "apksigner" else "apksigner.bat")
        require(aapt2.isFile) { "Missing aapt2 in $buildTools." }

        apks.forEach { apk ->
            val badging = runTool(aapt2, "dump", "badging", apk.absolutePath)
            val packageName = Regex("""package: name='([^']+)'""")
                .find(badging)
                ?.groupValues
                ?.get(1)
            require(packageName == expectedApplicationId) {
                "${apk.name} has application ID '$packageName', expected '$expectedApplicationId'."
            }

            if (requireOfficialSignature.get()) {
                require(apksigner.isFile) { "Missing apksigner in $buildTools." }
                val certificates = runTool(
                    apksigner,
                    "verify",
                    "--verbose",
                    "--print-certs",
                    apk.absolutePath
                )
                val certificate = Regex(
                    """Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)"""
                ).find(certificates)?.groupValues?.get(1)?.filter(Char::isLetterOrDigit)?.lowercase()
                require(certificate == expectedCertificate) {
                    "${apk.name} is not signed by the official Acqua certificate."
                }
            }
        }
    }

    private fun locateAndroidSdk(rootDirectory: File): File {
        val candidates = listOfNotNull(
            System.getenv("ANDROID_SDK_ROOT"),
            System.getenv("ANDROID_HOME"),
            Properties().run {
                val localProperties = rootDirectory.resolve("local.properties")
                if (localProperties.isFile) {
                    localProperties.inputStream().use(::load)
                    getProperty("sdk.dir")
                } else {
                    null
                }
            }
        ).map(::File)
        return candidates.firstOrNull(File::isDirectory)
            ?: throw GradleException("Android SDK not found. Set ANDROID_SDK_ROOT or sdk.dir.")
    }

    private fun versionKey(value: String): String =
        value.split('.', '-', '_').joinToString(".") { part ->
            (part.toIntOrNull() ?: -1).toString().padStart(8, '0')
        }

    private fun runTool(tool: File, vararg arguments: String): String {
        val process = ProcessBuilder(listOf(tool.absolutePath) + arguments)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw GradleException("${tool.name} failed (${exitCode}): ${output.trim()}")
        }
        return output
    }
}

abstract class RequireReleaseSigningTask : DefaultTask() {
    @get:Input
    abstract val configured: org.gradle.api.provider.Property<Boolean>

    @TaskAction
    fun verify() {
        if (!configured.get()) {
            throw GradleException(
                "Official release signing is not configured. Add all signing.* values to signing.properties; " +
                    "use verifyReleaseShrink for an unsigned local shrinker build."
            )
        }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("acqua.android.application.conventions")
}

val keystoreProperties = Properties()
var keystorePropertiesFile = rootProject.file("signing.properties")
if (!keystorePropertiesFile.exists()) {
    keystorePropertiesFile = rootProject.file("local.properties")
}
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

val signingStoreFile = keystoreProperties["signing.storeFile"]?.toString()
val signingStorePassword = keystoreProperties["signing.storePassword"]?.toString()
val signingKeyAlias = keystoreProperties["signing.keyAlias"]?.toString()
val signingKeyPassword = keystoreProperties["signing.keyPassword"]?.toString()
val hasReleaseSigning = listOf(
    signingStoreFile,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "dev.qtremors.acqua"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.qtremors.acqua"
        minSdk = 24
        targetSdk = 37
        versionCode = 25
        versionName = "0.2.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
                isUniversalApk = true
            }
        }
    }

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "application_label", "Acqua Debug")
            enableUnitTestCoverage = true
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            resValue("string", "application_label", "Acqua")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    packaging {
        jniLibs.useLegacyPackaging = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = true
        // Dependency freshness is reviewed separately. Keeping it out of lint makes the
        // release gate deterministic and leaves every source/resource warning actionable.
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable"
        )
    }
}

val generatedLegalAssets = layout.buildDirectory.dir("generated/legal-assets")
val generateLegalAssets = tasks.register<GenerateLegalAssetsTask>("generateLegalAssets") {
    sourceFiles.from(
        rootProject.file("../LICENSE.md"),
        rootProject.file("../THIRD_PARTY_NOTICES.md"),
        rootProject.file("../LICENSES/Apache-2.0.txt")
    )
    outputDirectory.set(generatedLegalAssets)
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            generateLegalAssets,
            GenerateLegalAssetsTask::outputDirectory
        )
        variant.outputs.forEach { output ->
            val version = (output.versionName.get() ?: "0.0.0").removeSuffix("-debug")
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            val abiSuffix = abi?.let { "-$it" }.orEmpty()
            val appName = if (variant.buildType == "debug") "Acqua-Debug" else "Acqua"
            output.outputFileName.set("$appName-$version$abiSuffix.apk")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler/reports")
    metricsDestination = layout.buildDirectory.dir("compose_compiler/metrics")
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material.kolor)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)

    implementation(libs.okhttp)
    implementation(libs.youtubedl.android.library)
    implementation(libs.youtubedl.android.ffmpeg)
    testImplementation(libs.junit)
    testImplementation(libs.archunit.junit4)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val releaseVersion = android.defaultConfig.versionName ?: error("versionName is required")
val releaseApkNames = listOf(
    "Acqua-$releaseVersion.apk",
    "Acqua-$releaseVersion-arm64-v8a.apk",
    "Acqua-$releaseVersion-armeabi-v7a.apk",
    "Acqua-$releaseVersion-x86.apk",
    "Acqua-$releaseVersion-x86_64.apk"
)
val releaseArtifactsDirectory = layout.buildDirectory.dir("outputs/apk/release")

val verifyReleaseShrink = tasks.register<VerifyReleaseArtifactsTask>("verifyReleaseShrink") {
    group = "verification"
    description = "Builds every minified release APK and verifies its package identity and ABI artifact set."
    dependsOn("assembleRelease")
    artifactsDirectory.set(releaseArtifactsDirectory)
    releaseIdentityFile.set(rootProject.layout.projectDirectory.file("release-identity.properties"))
    expectedArtifactNames.set(releaseApkNames)
    requireOfficialSignature.set(false)
    rootDirectoryPath.set(rootProject.projectDir.absolutePath)
}

val requireReleaseSigning = tasks.register<RequireReleaseSigningTask>("requireReleaseSigning") {
    group = "publishing"
    description = "Fails unless all official release signing credentials are configured."
    configured.set(hasReleaseSigning)
}

val verifySignedReleaseArtifacts = tasks.register<VerifyReleaseArtifactsTask>("verifySignedReleaseArtifacts") {
    group = "publishing"
    description = "Verifies that every release APK has the official package ID and signing certificate."
    dependsOn(requireReleaseSigning, "assembleRelease")
    mustRunAfter(requireReleaseSigning)
    artifactsDirectory.set(releaseArtifactsDirectory)
    releaseIdentityFile.set(rootProject.layout.projectDirectory.file("release-identity.properties"))
    expectedArtifactNames.set(releaseApkNames)
    requireOfficialSignature.set(true)
    rootDirectoryPath.set(rootProject.projectDir.absolutePath)
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    mustRunAfter(requireReleaseSigning)
}

tasks.register("publishRelease") {
    group = "publishing"
    description = "Builds and verifies publishable APKs signed with the official Acqua identity."
    dependsOn(verifySignedReleaseArtifacts)
}
