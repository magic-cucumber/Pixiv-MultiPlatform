plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.kotlin.multiplatform.library")
}

val i18nSources = objects.sourceDirectorySet("i18n", "i18n YAML sources").apply {
    srcDir("src/i18n")
}
val generatedComposeResources = layout.buildDirectory.dir("generated/i18n/composeResources")

kotlin {
    library(
        project = project,
        module = "sharedUI.i18n",
        android = {
            androidResources.enable = true
        },
    )

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.resources)
        }
    }
}

val generateI18nComposeResources = tasks.register<GenerateI18nComposeResourcesTask>("generateI18nComposeResources") {
    group = "build"
    description = "Generates Compose string resources from i18n YAML files."
    yamlFiles.from(i18nSources.asFileTree.matching { include("**/*.yaml", "**/*.yml") })
    outputDirectory.set(generatedComposeResources)
}

tasks.matching {
    it.name != generateI18nComposeResources.name && (
        it.name.contains("ResourcesFor", ignoreCase = true) ||
            it.name.contains("ComposeResourcesTaskFor", ignoreCase = true)
        )
}.configureEach {
    dependsOn(generateI18nComposeResources)
}

compose.resources {
    customDirectory(
        sourceSetName = "commonMain",
        directoryProvider = generatedComposeResources,
    )
    publicResClass = true
    packageOfResClass = "top.kagg886.pmf.i18n"
    nameOfResClass = "Lang"
    generateResClass = auto
}
