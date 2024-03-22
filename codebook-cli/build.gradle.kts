import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.fileAttributesViewOrNull
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    java
    alias(libs.plugins.shadow)
    id("codebook")
}

dependencies {
    implementation(projects.codebook)

    implementation(libs.picocli)
    implementation(libs.slf4j)
    implementation(libs.slf4j.jul)
    implementation(libs.logback)
    implementation(libs.sysoutOverSlf4j)
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "io.papermc.codebook.cli.Main")
        attributes(
            "io/papermc/codebook/cli/",
            "Specification-Title" to "codebook-cli",
            "Specification-Version" to project.version,
            "Specification-Vendor" to "PaperMC",
        )
    }
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
if (os.isLinux || os.isMacOsX) {
    tasks.register("executableJar") {
        group = "build"
        dependsOn(tasks.shadowJar)
        val outputFile = layout.buildDirectory.file("libs/codebook")
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
}

publishing {
    publications {
        codebook {
            pom {
                name.set("codebook-cli")
                description.set("CLI for the remapper tool for PaperMC")
            }
        }
    }
}
