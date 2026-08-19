plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Kotlin 2.0 + lifecycle-lint: NullSafeMutableLiveData crashes lintVitalAnalyzeRelease.
subprojects {
    pluginManager.withPlugin("com.android.application") {
        extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
            lint {
                disable += "NullSafeMutableLiveData"
                checkReleaseBuilds = false
            }
        }
    }
    pluginManager.withPlugin("com.android.library") {
        extensions.configure<com.android.build.api.dsl.LibraryExtension>("android") {
            lint {
                disable += "NullSafeMutableLiveData"
                checkReleaseBuilds = false
            }
        }
    }
}
