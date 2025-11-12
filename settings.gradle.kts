rootProject.name = "Pixiv-MultiPlatform"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        mavenLocal()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        maven("https://jogamp.org/deployment/maven")
    }
}

include(":composeApp")

//some API libraries was in here
//sometimes it can't publish
//other is fork from others
include(":lib:chip-text-field")
include(":lib:epub")
include(":lib:okio-enhancement-util")
include(":lib:gif")
include(":lib:compose-settings")
include(":lib:file-picker")
include(":lib:multiplatform-serializer-fix")
//include(":plugin:compose-desktop-build-windows")
