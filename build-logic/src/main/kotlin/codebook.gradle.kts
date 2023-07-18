import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    java
    `maven-publish`
    id("com.diffplug.spotless")
    id("org.cadixdev.licenser")
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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
}

val libs = the<LibrariesForLibs>()

spotless {
    java {
        palantirJavaFormat(libs.versions.palantir.get())
    }
}

license {
    header.set(resources.text.fromFile(rootProject.layout.projectDirectory.file("header.txt")))
}

val format by tasks.registering {
    dependsOn(tasks.spotlessApply, tasks.licenseFormat)
    group = "format"
}

val isSnapshot = project.version.toString().endsWith("-SNAPSHOT")

publishing {
    publications {
        register<MavenPublication>("codebook") {
            groupId = project.group.toString()
            artifactId = project.name
            version = project.version.toString()

            from(components["java"])
            withoutBuildIdentifier()

            pom {
                val repoUrl = "https://github.com/PaperMC/codebook"

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
