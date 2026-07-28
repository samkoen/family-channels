pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "FamilyChannels"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:ui")
include(":feature:join")
include(":feature:home")
include(":feature:videos")
include(":feature:player")
include(":feature:quota")
