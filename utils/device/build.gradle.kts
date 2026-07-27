plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    library(project = project, module = "utils.device")
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.ui)
        }
    }
}
