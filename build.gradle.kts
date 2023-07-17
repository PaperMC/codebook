import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.fileAttributesViewOrNull

plugins {
    java
    `maven-publish`
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
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/") {
        name = "Sonatype"
        mavenContent {
            snapshotsOnly()
            includeGroupAndSubgroups("dev.denwav.hypo")
        }
    }
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

val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

publishing {
    publications {
        register<MavenPublication>("maven") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
            withoutBuildIdentifier()

            pom {
                val repoUrl = "https://github.com/PaperMC/codebook"

                name.set("codebook")
                description.set("Remapper tool for PaperMC")
                url.set(repoUrl)
                inceptionYear.set("2023")
                packaging = "jar"

                licenses {
                    license {
                        name.set("LGPL-3.0-only")
                        url.set("$repoUrl/blob/main/license.txt")
                        distribution.set("repo")
                    }
                }

                issueManagement {
                    system.set("GitHub")
                    url.set("$repoUrl/issues")
                }

                developers {
                    developer {
                        id.set("DenWav")
                        name.set("Kyle Wood")
                        email.set("kyle@denwav.dev")
                        url.set("https://github.com/DenWav")
                    }
                }

                scm {
                    url.set(repoUrl)
                    connection.set("scm:git:$repoUrl.git")
                    developerConnection.set(connection)
                }
            }
        }
    }

    repositories {
        val url = if (isSnapshot) {
            "https://repo.denwav.dev/repository/maven-snapshots/"
        } else {
            "https://repo.denwav.dev/repository/maven-releases/"
        }
        maven(url) {
            name = "denwav"
            credentials(PasswordCredentials::class)
        }
    }
}

tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}
