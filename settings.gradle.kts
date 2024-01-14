pluginManagement {
    includeBuild("build-logic")
}

rootProject.name = "codebook"

include("codebook-cli")
include("codebook-lvt")
include("codebook-reports")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
