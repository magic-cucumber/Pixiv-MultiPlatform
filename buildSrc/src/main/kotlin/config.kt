import org.gradle.api.Project

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2025/9/16 13:28
 * ================================================
 */

private const val APPLICATION_VERSION_NAME = "application.version.name"
private const val APPLICATION_VERSION_CODE = "application.version.code"
private const val APPLICATION_ANDROID_COMPILE_SDK = "application.android.compile_sdk"
private const val APPLICATION_ANDROID_MIN_SDK = "application.android.min_sdk"
private const val APPLICATION_ANDROID_TARGET_SDK = "application.android.target_sdk"

private fun Project.application(name: String): String? {
    check(name.startsWith("application.")) { "Application properties must start with application.: $name" }

    val environmentName = name.replace('.', '_').uppercase()
    return System.getenv(environmentName)
        ?: System.getProperty(name)
        ?: findProperty(name)?.toString()
}

private fun Project.applicationInt(name: String, defaultValue: Int): Int =
    application(name)?.toIntOrNull() ?: defaultValue

private fun Project.applicationBoolean(name: String, defaultValue: Boolean = false): Boolean =
    application(name)?.toBooleanStrictOrNull() ?: defaultValue

/** `application.version.name` or `APPLICATION_VERSION_NAME`. */
val Project.application_version_name: String
    get() = application(APPLICATION_VERSION_NAME) ?: "1.0.0"

/** `application.version.code` or `APPLICATION_VERSION_CODE`. */
val Project.application_version_code: Int
    get() = applicationInt(APPLICATION_VERSION_CODE, 1)

/** `application.android.compile_sdk` or `APPLICATION_ANDROID_COMPILE_SDK`. */
val Project.android_compile_sdk: Int
    get() = applicationInt(APPLICATION_ANDROID_COMPILE_SDK, 37)

/** `application.android.min_sdk` or `APPLICATION_ANDROID_MIN_SDK`. */
val Project.android_min_sdk: Int
    get() = applicationInt(APPLICATION_ANDROID_MIN_SDK, 23)

/** `application.android.target_sdk` or `APPLICATION_ANDROID_TARGET_SDK`. */
val Project.android_target_sdk: Int
    get() = applicationInt(APPLICATION_ANDROID_TARGET_SDK, 37)
