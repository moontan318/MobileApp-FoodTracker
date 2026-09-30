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

rootProject.name = "MealMacros"

include(":core")

// `-PcoreOnly` builds just the pure-Kotlin nutrition engine, which is useful on
// machines without an Android SDK (e.g. `./gradlew -PcoreOnly :core:test`).
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":app")
}
