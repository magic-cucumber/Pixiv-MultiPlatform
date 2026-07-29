rootProject.name = "Pixiv-MultiPlatform"

pluginManagement {
    repositories {
        google {
            content {
              	includeGroupByRegex("com\\.android.*")
              	includeGroupByRegex("com\\.google.*")
              	includeGroupByRegex("androidx.*")
              	includeGroupByRegex("android.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google {
            content {
              	includeGroupByRegex("com\\.android.*")
              	includeGroupByRegex("com\\.google.*")
              	includeGroupByRegex("androidx.*")
              	includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
    }
}
include(":sharedUI")
include(":sharedUI:i18n")

include(":androidApp")
include(":desktopApp")
include(":iosApp")

include(":plugins:navigation-serializer-module-creator")


include(":utils:navigate3")
include(":utils:datastore")
include(":utils:device")
include(":utils:io")
