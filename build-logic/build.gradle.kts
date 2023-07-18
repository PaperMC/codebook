plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.gradle.spotless)
    implementation(libs.gradle.licenser)
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
