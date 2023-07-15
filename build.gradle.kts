import java.nio.file.Files
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.fileAttributesViewOrNull
import kotlin.io.path.setPosixFilePermissions

plugins {
    java
    alias(libs.plugins.spotless)
    alias(libs.plugins.licenser)
    alias(libs.plugins.shadow)
}

group = "io.papermc"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net") {
        name = "FabricMC"
        mavenContent {
            releasesOnly()
            includeGroupAndSubgroups("net.fabricmc")
        }
    }
}

dependencies {
    implementation(libs.checker)
    implementation(libs.picocli)
    implementation(libs.slf4j)
    implementation(libs.slf4j.jul)
    implementation(libs.logback)
    implementation(libs.sysoutOverSlf4j)

    implementation(libs.gson)
    implementation(libs.bytes)
    implementation(libs.lorenz)
    implementation(libs.lorenz.proguard)

    implementation(libs.lorenz.tiny)
    implementation(libs.unpick.format)
    implementation(libs.unpick.cli)

    implementation(platform(libs.hypo.platform))
    implementation(libs.bundles.hypo)
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "io.papermc.codebook.Main")
        attributes(
            "io/papermc/codebook/",
            "Specification-Title" to "codebook",
            "Specification-Version" to project.version,
            "Specification-Vendor" to "PaperMC",
        )
    }
}

val executableJar by tasks.registering {
    dependsOn(tasks.shadowJar)
    val outputFile = layout.buildDirectory.file("libs/${project.name}")
    outputs.file(outputFile)

    doLast {
        val out = outputFile.get().asFile

        out.outputStream().buffered().use { outputStream ->
            outputStream.write("#!/bin/sh\nexec java -jar \"\$0\" \"\$@\"\n".toByteArray(Charsets.UTF_8))
            tasks.shadowJar.get().outputs.files.singleFile.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        out.toPath().fileAttributesViewOrNull<PosixFileAttributeView>()
            ?.setPermissions(PosixFilePermissions.fromString("rwxr-xr-x"))
    }
}

spotless {
    java {
        palantirJavaFormat(libs.versions.palantir.get())
    }
}

license {
    header.set(resources.text.fromFile(layout.projectDirectory.file("header.txt")))
}

val format by tasks.registering {
    dependsOn(tasks.spotlessApply, tasks.licenseFormat)
    group = "format"
}
