pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "pavilion"

// Pure Kotlin/JVM core: no Android dependencies, so it builds and tests anywhere.
include(":core:model")
include(":core:network")
include(":core:analytics")
