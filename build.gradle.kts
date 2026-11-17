import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.google.services) apply false
}

// Shared configuration for the pure Kotlin/JVM modules. They target Java 17 bytecode so the
// Android modules can depend on them directly.
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
                freeCompilerArgs.add("-Xjdk-release=17")
                allWarningsAsErrors.set(true)
            }
        }
        tasks.withType<Test>().configureEach {
            useJUnit()
            // Live contract tests hit the real API; they only run when explicitly requested.
            systemProperty("criczy.liveApi", providers.gradleProperty("liveApi").getOrElse("false"))
            testLogging { events("failed", "skipped") }
        }
    }
}
