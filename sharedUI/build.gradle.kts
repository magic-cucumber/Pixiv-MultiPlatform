plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.buildConfig)
}

val platform_wvbridge = when {
    System.getProperty("os.name").startsWith("Windows") -> libs.wvbridge.platform.windows
    System.getProperty("os.name") == "Linux" -> libs.wvbridge.platform.linux
    System.getProperty("os.name") == "Mac OS X" -> libs.wvbridge.platform.macos
    else -> error("wvbridge does not support ${System.getProperty("os.name")} on JVM")
}

kotlin {
    library(
        project = project,
        module = "sharedUI",
        android = {
            androidResources.enable = true
        },
        ios = {
            binaries.framework {
                linkerOpts += "-lsqlite3"
            }
        }
    )
    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        }

        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.resources)
            api(libs.compose.ui.tooling.preview)
            api(libs.compose.material3)
            implementation(libs.compose.material3.icons)
            implementation(libs.kermit)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.serialization)
            implementation(libs.ktor.serialization.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)
            implementation(libs.compose.nav3)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coil)
            implementation(libs.coil.network.ktor)
            implementation(libs.kotlinx.datetime)
            implementation(libs.androidx.sqlite.async)
            implementation(libs.room.runtime)
            implementation(libs.room.paging)
            implementation(libs.materialKolor)
            implementation(libs.orbit.core)
            implementation(libs.orbit.viewmodel)
            implementation(libs.orbit.compose)
            implementation(libs.paging.common)
            implementation(libs.paging.compose)
            implementation(libs.store)
            implementation(libs.pixko)
            implementation(libs.wvbridge.core)

            api(project(":sharedUI:i18n"))
            api(project(":plugins:logger:api"))

            api(project(":utils:datastore"))
            api(project(":utils:device"))
            api(project(":utils:navigate3"))
            api(project(":utils:io"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.compose.ui.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.orbit.test)
            implementation(libs.paging.testing)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.sqlite.bundled)
            runtimeOnly(platform_wvbridge)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}

buildConfig {
    // BuildConfig configuration here.
    // https://github.com/gmazzo/gradle-buildconfig-plugin#usage-in-kts
    packageName("top.kagg886.pmf")
    buildConfigField("APP_NAME","Pixiv-MultiPlatform")
    buildConfigField("APP_VERSION_NAME",application_version_name)
    buildConfigField("APP_VERSION_CODE",application_version_code)
    buildConfigField("APP_DATABASE_VERSION",application_database_version)
}

compose.resources {
    publicResClass = true
    packageOfResClass = "top.kagg886.pmf.res"
    generateResClass = auto
}


room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    with(libs.room.compiler) {
        add("kspAndroid", this)
        add("kspJvm", this)
        add("kspIosArm64", this)
        add("kspIosSimulatorArm64", this)
    }
    with(project(":plugins:room-database-generateor")) {
        add("kspAndroid", this)
        add("kspJvm", this)
        add("kspIosArm64", this)
        add("kspIosSimulatorArm64", this)
    }
    add("kspCommonMainMetadata", project(":plugins:navigation-serializer-module-creator"))
    add("kspCommonMainMetadata", project(":plugins:room-database-generateor"))
    add("kspCommonMainMetadata", project(":plugins:logger:processor"))
}

tasks.configureEach {
    if (name.startsWith("compileKotlin") || name.startsWith("kspKotlin")) {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}
