// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

subprojects {
    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        jvmArgs("-Xshare:off")
    }
}

tasks.register("verifyLocalRelease") {
    group = "verification"
    description = "Runs the reproducible local release gate without requiring private signing credentials."
    dependsOn(
        ":app:testDebugUnitTest",
        ":core:data:testDebugUnitTest",
        ":core:domain:test",
        ":core:ui:testDebugUnitTest",
        ":feature:browser:testDebugUnitTest",
        ":feature:downloads:testDebugUnitTest",
        ":feature:onboarding:testDebugUnitTest",
        ":feature:settings:testDebugUnitTest",
        ":feature:updates:testDebugUnitTest",
        ":app:lintDebug",
        ":app:lintRelease",
        ":app:verifyReleaseShrink",
        ":app:verifyAcquaBuildConventions",
        "verifyStaticSite"
    )
}
