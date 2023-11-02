import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    idea
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(project(":exporter"))
    implementation(libs.kotlin.cli)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.log4j.api.kotlin)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j2.impl)
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "io.github.patxibocos.googlephotosexporter.MainKt"
        archiveFileName.set("exporter-cli.jar")
    }
}

tasks.withType<ShadowJar> {
    minimize {
        exclude(dependency(libs.log4j.slf4j2.impl.get()))
        exclude(dependency(libs.log4j.core.get()))
    }
}
