pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "codebook"

include("codebook-cli")
include("codebook-lvt")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
