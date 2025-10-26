plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.spotless)
    alias(libs.plugins.licenser)
    id("codebook")
}

dependencies {
    api(libs.checker)

    implementation(projects.codebookLvt)
    api(projects.codebookReports)

    implementation(libs.guice)
    implementation(libs.inject)
    implementation(libs.guava)

    implementation(libs.gson)
    implementation(libs.bytes)
    implementation(libs.bundles.asm)

    implementation(libs.unpick.format)
    implementation(libs.unpick)

    implementation(platform(libs.hypo.platform))
    implementation(libs.bundles.hypo.full)

    implementation(libs.lorenz)

    implementation(libs.feather.core)
    implementation(libs.feather.gson)

    annotationProcessor(libs.recordBuilder.processor)
    compileOnly(libs.recordBuilder.core)

    testImplementation(libs.junit.api)
    testImplementation(libs.junit.params)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.launcher)

    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit)
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.papermc.codebook")
    }
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

tasks.test {
    useJUnitPlatform()
}

tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}
