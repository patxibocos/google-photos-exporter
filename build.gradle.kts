import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    idea
    kotlin("jvm") version "1.8.0"
    kotlin("plugin.serialization") version "1.8.0"
    id("com.diffplug.spotless") version "6.12.1"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "io.github.patxibocos"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.cli)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.logging)
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

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.mockk)
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
        attributes["Main-Class"] = "io.github.patxibocos.googlephotosexporter.MainKt"
        archiveFileName.set("${project.name}.jar")
    }
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
    minimize {
        exclude(dependency(libs.log4j.slf4j2.impl.get()))
        exclude(dependency(libs.log4j.core.get()))
    }
}

tasks.test {
    useJUnitPlatform()
}
