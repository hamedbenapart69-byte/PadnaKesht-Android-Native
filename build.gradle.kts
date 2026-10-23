plugins {
    // Versions verified against official release pages on 2026-10-22:
    // AGP 9.4.0 (stable, needs Gradle 9.6.0 / JDK 17), Kotlin 2.4.21 (stable, 2026-10-08),
    // KSP 2.3.12 (latest on Maven Central; version scheme no longer tied to Kotlin).
    // AGP 9 has built-in Kotlin, so org.jetbrains.kotlin.android is intentionally not applied.
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
