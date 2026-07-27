plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    library(project = project)
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(libs.okio)
        }
    }
}
