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

// Phase 0 family prototype (docs/specs/08, 11).
// :engine is a plain JVM module — the KMP-ready pure-Kotlin core (docs/specs/06):
// the Android Gradle plugin is not applied there, so android.* imports are
// impossible by construction. It graduates to :core:domain in Phase 1.
include(":engine")
include(":app")
