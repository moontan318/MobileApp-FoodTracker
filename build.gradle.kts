// All plugins are put on the root build classpath so that the Kotlin and Android
// Gradle plugins share one classloader.
buildscript {
    val coreOnly = providers.gradleProperty("coreOnly").isPresent
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        if (!coreOnly) {
            classpath("com.android.tools.build:gradle:8.7.3")
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21")
        }
    }
}
