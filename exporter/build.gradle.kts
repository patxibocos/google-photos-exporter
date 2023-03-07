plugins {
    idea
    `maven-publish`
    signing
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "io.github.patxibocos"
version = "0.0.5"

dependencies {
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.auth)
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.serialization.json)
    implementation(libs.zip4j)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.mockk)
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

java {
    withJavadocJar()
    withSourcesJar()
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "io.github.patxibocos"
                artifactId = "google-photos-exporter"
                version = project.version.toString()
                description = "Google Photos Exporter"

                from(components["java"])

                pom {
                    name.set("Google Photos Exporter")
                    description.set("Google Photos Exporter")
                    url.set("https://github.com/patxibocos/google-photos-exporter")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/mit-license.php")
                        }
                    }
                    developers {
                        developer {
                            id.set("patxibocos")
                            name.set("Patxi Bocos")
                            email.set("patxi.bocos.vidal@gmail.com")
                            url.set("https://twitter.com/patxibocos")
                            timezone.set("Europe/Madrid")
                        }
                    }
                    scm {
                        connection.set("scm:git:github.com/patxibocos/google-photos-exporter.git")
                        developerConnection.set("scm:git:ssh://github.com/patxibocos/google-photos-exporter.git")
                        url.set("https://github.com/patxibocos/google-photos-exporter/tree/main")
                    }
                }
            }
        }
    }

    signing {
        val signingKeyId: String? by project
        val signingSecretKey: String? by project
        val signingPassword: String? by project
        useInMemoryPgpKeys(signingKeyId, signingSecretKey, signingPassword)
        sign(publishing.publications)
    }
}
