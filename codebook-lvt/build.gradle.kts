plugins {
    `java-library`
    id("codebook")
}

dependencies {
    implementation(platform(libs.hypo.platform))
    implementation(projects.codebookReports)

    api(libs.checker)
    api(libs.bundles.hypo.base)
    api(libs.lorenz)

    implementation(libs.guice)
    implementation(libs.inject)
    implementation(libs.guava)

    implementation(libs.bundles.hypo.impl)
    implementation(libs.bundles.asm)
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.papermc.codebook.lvt")
    }
}

publishing {
    publications {
        codebook {
            pom {
                name.set("codebook-lvt")
                description.set("LVT naming tool for PaperMC")
            }
        }
    }
}
