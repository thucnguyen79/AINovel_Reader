pluginManagement {
    includeBuild("build-logic")
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
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "AINovelReader"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// core
include(":core:common")
include(":core:designsystem")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:storage")

// domain
include(":domain")

// data
include(":data:source")
include(":data:library")
include(":data:ebook")
include(":data:translate")

// tts
include(":tts:api")
include(":tts:system")

// feature
include(":feature:home")
include(":feature:browser")
include(":feature:reader")
include(":feature:player")
include(":feature:library")
include(":feature:translate")
include(":feature:settings")

// service
include(":service:playback")
include(":service:download")
