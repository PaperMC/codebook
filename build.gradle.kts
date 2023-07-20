plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.spotless)
    alias(libs.plugins.licenser)
    id("codebook")
}

dependencies {
    api(libs.checker)

    implementation(libs.guice)
    implementation(libs.inject)

    implementation(libs.gson)
    implementation(libs.bytes)
    implementation(libs.bundles.asm)

    implementation(libs.lorenz)
    implementation(libs.lorenz.proguard)

    implementation(libs.lorenz.tiny)
    implementation(libs.unpick.format)
    implementation(libs.unpick.cli)

    implementation(platform(libs.hypo.platform))
    implementation(libs.bundles.hypo)

    annotationProcessor(libs.recordBuilder.processor)
    compileOnly(libs.recordBuilder.core)
}

publishing {
    publications {
        codebook {
            pom {
                name.set("codebook")
                description.set("Remapper tool for PaperMC")
            }
        }
    }
}

tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}
