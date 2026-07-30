plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    library(project = project, module = "plugins.logger.api")
    explicitApi()
}
