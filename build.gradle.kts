import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

plugins {
    base
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.antlr.kotlin) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.kotlinx.kover)
}

// Single version for every module. The release workflow rewrites this line.
group = "io.zenwave360.dsl"
version = "1.11.0-SNAPSHOT"

val hasSigningCredentials = sequenceOf(
    "signingInMemoryKey",
    "signingKey",
    "signing.secretKeyRingFile",
).any { !providers.gradleProperty(it).orNull.isNullOrBlank() }

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    // Kotlin Multiplatform targets shared by all modules. Modules add their own target details.
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
            jvmToolchain(17)

            jvm {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
            js(IR) {
                nodejs()
                browser {
                    testTask {
                        useKarma {
                            useChromeHeadless()
                        }
                    }
                }
            }
        }
    }

    plugins.withId("com.vanniktech.maven.publish") {
        // Local staging repository used by the release workflow: the build job publishes
        // here with NO credentials (task: publishAllPublicationsToLocalStagingRepository),
        // and a separate privileged job signs and uploads the result to the Central
        // Portal without executing any Gradle code. See docs/release-security.md.
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "localStaging"
                    url = uri(layout.buildDirectory.dir("staging-deploy"))
                }
            }
        }

        // Each module sets its own pom name and description.
        extensions.configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)

            if (hasSigningCredentials) {
                signAllPublications()
            }

            pom {
                url.set("https://github.com/ZenWave360/dsl-kotlin")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("ivangsa")
                        name.set("Ivan Garcia Sainz-Aja")
                        email.set("ivangsa@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/ZenWave360/dsl-kotlin.git")
                    developerConnection.set("scm:git:ssh://github.com/ZenWave360/dsl-kotlin.git")
                    url.set("https://github.com/ZenWave360/dsl-kotlin")
                }
            }
        }
    }

    plugins.withId("org.jetbrains.kotlinx.kover") {
        extensions.configure<kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension> {
            useJacoco("0.8.14")

            reports {
                filters {
                    excludes {
                        // skip generated parser
                        packages("io.zenwave360.language.antlr")
                    }
                }
                verify {
                    rule {
                        minBound(70, CoverageUnit.BRANCH)
                    }
                }
            }
        }

        tasks.named("check") {
            dependsOn("koverVerify")
        }
    }

    // Karma launches Chrome through CHROME_BIN. When it is not set, fall back to a locally
    // installed Chromium-based browser (Chrome, Chromium or Microsoft Edge).
    tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>()
        .matching { it.name == "jsBrowserTest" }
        .configureEach {
            if (System.getenv("CHROME_BIN").isNullOrBlank()) {
                val candidates = listOf(
                    "C:/Program Files/Google/Chrome/Application/chrome.exe",
                    "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe",
                    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
                    "C:/Program Files/Microsoft/Edge/Application/msedge.exe",
                    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                    "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                    "/usr/bin/google-chrome",
                    "/usr/bin/chromium",
                    "/usr/bin/chromium-browser",
                    "/usr/bin/microsoft-edge",
                )
                candidates.firstOrNull { file(it).exists() }?.let { environment("CHROME_BIN", it) }
            }
        }
}

// Coverage of all modules, merged at the root so CI reads build/reports/kover/report.xml.
dependencies {
    kover(project(":dsl-kotlin"))
    kover(project(":dsl-views"))
    kover(project(":dsl-docs"))
}

kover {
    useJacoco("0.8.14")

    reports {
        filters {
            excludes {
                // skip generated parser
                packages("io.zenwave360.language.antlr")
            }
        }
        verify {
            rule {
                minBound(70, CoverageUnit.BRANCH)
            }
        }
    }
}

tasks.named("check") {
    dependsOn("koverVerify")
}
