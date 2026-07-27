plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlinx.atomicfu)
}

kotlin {
    library(project = project)
    explicitApi()

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.datastore.preferences.core)
            api(libs.okio)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
