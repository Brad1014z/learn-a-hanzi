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
