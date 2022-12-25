import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    idea
    kotlin("jvm") version "1.7.21"
    kotlin("plugin.serialization") version "1.7.21"
    id("com.diffplug.spotless") version "6.12.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "io.github.patxibocos"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.google.api.client)
    implementation(libs.google.oauth2.http)
    implementation(libs.google.photos.library.client)
    implementation(libs.kotlin.cli)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization.json)
    implementation(libs.log4j.api.kotlin)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j2.impl)
    implementation(libs.zip4j)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("$buildDir/**/*.kt", "bin/**/*.kt")
        ktlint("0.47.1")
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint("0.47.1")
    }
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "io.github.patxibocos.googlephotosgithubexporter.MainKt"
        archiveFileName.set("${project.name}.jar")
    }
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
}
