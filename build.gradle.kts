// Root-level build.gradle.kts
plugins {
    // AGP version from your versions file: agp = "8.7.3"
    id("com.android.application") version "8.7.3" apply false

    // Kotlin plugin version from your versions file: kotlin = "2.0.21"
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false

    // Compose Compiler Gradle plugin (required for Kotlin 2.x + Compose)
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
