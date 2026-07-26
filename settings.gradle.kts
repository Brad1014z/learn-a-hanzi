pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Lets Gradle auto-provision the pinned build JDK (gradle-daemon-jvm.properties)
    // on machines that don't have it — no more JAVA_HOME juggling.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "learn-a-hanzi"

// Family prototype era (docs/specs/08, 11).
// :engine is a plain JVM module — the KMP-ready pure-Kotlin core (docs/specs/06):
// the Android Gradle plugin is not applied there, so android.* imports are
// impossible by construction. It graduates to :core:domain when the module
// split earns its keep (M2 — docs/specs/08).
include(":engine")
include(":app")
// JVM ingest tool (docs/specs/02) — builds the bundled dataset; never shipped in the APK.
include(":data-ingest")
