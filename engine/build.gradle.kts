// The KMP-ready pure-Kotlin core (docs/specs/06): plain JVM module, no Android
// plugin, so `android.*` imports are impossible by construction. Graduates to
// :core:domain when the module split earns its keep (M2 — docs/specs/08).
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
    // Flow appears in repository interfaces (ProgressRepository), so consumers see it.
    api(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
}
