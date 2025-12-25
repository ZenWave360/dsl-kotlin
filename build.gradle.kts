plugins {
    kotlin("multiplatform") version "2.0.21"
    id("com.strumenta.antlr-kotlin") version "1.0.3"
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
}

group = "io.zenwave360.sdk"
version = "1.5.0-SNAPSHOT"

val generateKotlinGrammarSource = tasks.register<com.strumenta.antlrkotlin.gradle.AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")

    source = fileTree(layout.projectDirectory.dir("src/commonMain/antlr")) {
        include("**/*.g4")
    }

    val pkgName = "io.zenwave360.zdl.antlr"
    packageName = pkgName
    arguments = listOf("-visitor")

    val outDir = "generatedAntlr/${pkgName.replace(".", "/")}"
    outputDirectory = layout.buildDirectory.dir(outDir).get().asFile
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)

    // Only sign if credentials are available (for CI/CD)
    val signingKey = System.getenv("SIGN_KEY")
    val signingPassword = System.getenv("SIGN_KEY_PASS")
    if (signingKey != null && signingPassword != null) {
        signAllPublications()
    }

    pom {
        name.set("ZDL Kotlin Multiplatform")
        description.set("ZenWave Domain Language (ZDL) parser for Kotlin Multiplatform (JVM and JS)")
        url.set("https://github.com/ZenWave360/zdl-kotlin")

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
            connection.set("scm:git:git://github.com/ZenWave360/zdl-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/ZenWave360/zdl-kotlin.git")
            url.set("https://github.com/ZenWave360/zdl-kotlin")
        }
    }
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js(IR) {
        nodejs()
        binaries.executable()

        // Generate ES modules instead of CommonJS
        useEsModules()

    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.strumenta:antlr-kotlin-runtime:1.0.3")
            }
            kotlin.srcDir(generateKotlinGrammarSource)
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            }
        }
        val jvmMain by getting {
            dependencies {
                // implementation("com.jayway.jsonpath:json-path:2.9.0")
            }
        }
        val jvmTest by getting
        val jsMain by getting
        val jsTest by getting {
            dependencies {
                implementation(npm("fs", "0.0.1-security"))
                implementation("org.jetbrains.kotlin-wrappers:kotlin-node:18.16.12-pre.610")
            }
        }
    }
}


// Node.js integration tests
val nodeIntegrationTest = tasks.register<Exec>("nodeIntegrationTest") {
    group = "verification"
    description = "Run Node.js integration tests for the published NPM package"

    dependsOn("jsProductionExecutableCompileSync", "kotlinNodeJsSetup")

    workingDir = file("nodejs-test-project")

    // Detect OS and use appropriate command
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val npmCmd = if (isWindows) "npm.cmd" else "npm"

    // Use npm test command
    commandLine(npmCmd, "test")
}

// Make check task depend on Node.js integration tests
tasks.named("check") {
    dependsOn("nodeIntegrationTest")
}


// Kover configuration for code coverage
kover {
    reports {
        filters {
            excludes {
                // Exclude generated ANTLR code from coverage
                packages("io.zenwave360.zdl.antlr")
            }
        }
    }
}
