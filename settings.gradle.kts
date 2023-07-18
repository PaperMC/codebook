pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "codebook"

include("codebook-cli")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
