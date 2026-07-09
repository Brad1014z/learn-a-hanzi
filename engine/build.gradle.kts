// The KMP-ready pure-Kotlin core (docs/specs/06): plain JVM module, no Android
// plugin, so `android.*` imports are impossible by construction. Graduates to
// :core:domain in Phase 1.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
