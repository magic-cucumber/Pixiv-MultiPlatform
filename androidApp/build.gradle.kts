import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
}

android {
    namespace = "top.kagg886.pmf.androidApp"
    compileSdk = android_compile_sdk

    defaultConfig {
        minSdk = android_min_sdk
        targetSdk = android_target_sdk

        applicationId = "top.kagg886.pmf.androidApp"
        versionCode = application_version_code
        versionName = application_version_name
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":sharedUI"))
    implementation(libs.androidx.activityCompose)
}
