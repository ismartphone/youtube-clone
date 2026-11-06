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
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // LiveKit Android (SDK du live partagé, ADR-031) dépend d'audioswitch, publié sur JitPack seulement.
        maven("https://jitpack.io") { content { includeGroup("com.github.davidliu") } }
    }
}

rootProject.name = "android"
include(":app")
// SDK du live partagé (ADR-031), compilé depuis le dépôt cloné (voir liveSdkDir dans gradle.properties).
include(":live-sdk-core", ":live-sdk-ui")
