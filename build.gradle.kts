import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    idea
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.nexus.publish)
    alias(libs.plugins.spotless) apply false
}

group = "io.github.patxibocos"

allprojects {
    repositories {
        mavenCentral()
    }

    apply(plugin = rootProject.libs.plugins.spotless.get().pluginId)

    configure<SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            targetExclude("$buildDir/**/*.kt", "bin/**/*.kt")
            ktlint(libs.versions.ktlint.get())
        }

        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versions.ktlint.get())
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            val sonatypeStagingProfileId: String? by project
            val sonatypeUsername: String? by project
            val sonatypePassword: String? by project
            stagingProfileId.set(sonatypeStagingProfileId)
            username.set(sonatypeUsername)
            password.set(sonatypePassword)
            // only for users registered in Sonatype after 24 Feb 2021
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}
