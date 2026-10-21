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
        // sherpa-onnx Android AAR (Apache-2.0) fetched from GitHub Releases by tools/sherpa/fetch_sherpa_aar.py;
        // the binary is git-ignored, only the POM is committed.
        maven { url = uri("third_party/m2") }
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
include(":tts:neural")

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
