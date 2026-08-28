plugins {
    `kotlin-dsl`
    `maven-publish`
    signing
}

group = "io.github.hammadbawara"
version = providers.gradleProperty("version").getOrElse("1.0.0")

java {
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    compileOnly(libs.android.gradlePlugin)

    testImplementation(libs.android.gradlePlugin)
    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

gradlePlugin {
    website = "https://github.com/hammadbawara/android-release-signing"
    vcsUrl = "https://github.com/hammadbawara/android-release-signing.git"
    plugins {
        register("releaseSigning") {
            id = "io.github.hammadbawara.android.release-signing"
            implementationClass = "com.hammadbawara.android.releasesigning.ReleaseSigningPlugin"
            displayName = "Android Release Signing Convention Plugin"
            description = "Convention plugin that lazily configures Android release signing from local.properties"
            tags = listOf("android", "signing", "release", "convention-plugin", "keystore")
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Android Release Signing Plugin")
            description.set("A lightweight Gradle convention plugin for Android that centralizes and automates release signing using credentials stored in local.properties")
            url.set("https://github.com/hammadbawara/android-release-signing")
            licenses {
                license {
                    name.set("The Apache License, Version 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                }
            }
            developers {
                developer {
                    id.set("hammadbawara")
                    name.set("Hammad Bawara")
                    url.set("https://github.com/hammadbawara")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/hammadbawara/android-release-signing.git")
                developerConnection.set("scm:git:ssh://github.com/hammadbawara/android-release-signing.git")
                url.set("https://github.com/hammadbawara/android-release-signing")
            }
        }
    }
    repositories {
        maven {
            name = "staging"
            url = uri(layout.buildDirectory.dir("staging-repo"))
        }
    }
}

signing {
    val signingKey = providers.environmentVariable("GPG_SIGNING_KEY")
        .orElse(providers.gradleProperty("signingKey"))
        .orNull
    val signingPassword = providers.environmentVariable("GPG_SIGNING_PASSWORD")
        .orElse(providers.gradleProperty("signingPassword"))
        .orNull

    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardError")
        showStandardStreams = true
    }
}
