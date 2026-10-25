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
        // AutoUpdater (CSAbhiOnline) só se publica en JitPack; limitado a ese grupo.
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.CSAbhiOnline") }
        }
    }
}

rootProject.name = "Notazas"
include(":app")
