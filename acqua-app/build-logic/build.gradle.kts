plugins {
    `kotlin-dsl`
}

dependencies {
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test"))
}

gradlePlugin {
    plugins {
        register("acquaAndroidApplicationConventions") {
            id = "acqua.android.application.conventions"
            implementationClass = "dev.qtremors.acqua.buildlogic.AcquaAndroidApplicationConventionsPlugin"
        }
    }
}
