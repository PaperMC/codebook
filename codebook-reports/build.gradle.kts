plugins {
    `java-library`
    id("codebook")
}

dependencies {
    implementation(platform(libs.hypo.platform))

    api(libs.checker)
    api(libs.lorenz)

    implementation(libs.bundles.hypo.impl)
    implementation(libs.bundles.asm)

    implementation(libs.guice)
    implementation(libs.inject)
    implementation(libs.guava)
}

publishing {
    publications {
        codebook {
            pom {
                name.set("codebook-reports")
                description.set("Codebook reports for PaperMC")
            }
        }
    }
}
