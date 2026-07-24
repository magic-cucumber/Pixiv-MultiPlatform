import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/8/18 16:45
 * ================================================
 */

fun KotlinMultiplatformExtension.library(
    project: Project,
    module: String = "",
    android: KotlinMultiplatformAndroidLibraryTarget.() -> Unit = {},
    jvm: KotlinJvmTarget.() -> Unit = {},
    ios: KotlinNativeTarget.() -> Unit = {}
) {
    jvmToolchain(22)

    this.extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        namespace = "top.kagg886.pmf" + if (module.isEmpty()) "" else ".$module"
        compileSdk = project.android_compile_sdk
        minSdk = project.android_min_sdk

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        android()
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        jvm()
    }

    iosArm64(ios)
    iosSimulatorArm64(ios)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
        optIn.addAll("kotlin.time.ExperimentalTime")
    }
}
