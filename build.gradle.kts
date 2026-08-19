// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // Kotlin 2.0+ के लिए कंपोज कंपाइलर प्लगइन
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}

