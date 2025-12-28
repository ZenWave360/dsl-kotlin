plugins {
    kotlin("multiplatform") version "2.0.21"
    id("com.strumenta.antlr-kotlin") version "1.0.3"
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}

group = "io.zenwave360.sdk"
version = "1.5.0-SNAPSHOT"

val generateKotlinGrammarSource = tasks.register<com.strumenta.antlrkotlin.gradle.AntlrKotlinTask>("generateKotlinGrammarSource") {
    dependsOn("cleanGenerateKotlinGrammarSource")

    source = fileTree(layout.projectDirectory.dir("src/commonMain/antlr")) {
        include("**/*.g4")
    }

    packageName = "io.zenwave360.language.antlr"

    val outDir = "generatedAntlr/${packageName?.replace(".", "/")}"
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
            connection.set("scm:git:git://github.com/ZenWave360/zdl-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/ZenWave360/zdl-kotlin.git")
            url.set("https://github.com/ZenWave360/dsl-kotlin")
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

        // Set the NPM package name to use scoped naming for main compilation only
        compilations["main"].packageJson {
            customField("name", "@zenwave360/dsl")
            customField("description", "ZenWave Domain Model Language for JavaScript/TypeScript")
            customField("keywords", listOf("zdl", "domain-driven-design", "event-storming"))
            customField("homepage", "https://github.com/ZenWave360/dsl-kotlin")
            customField("repository", mapOf(
                "type" to "git",
                "url" to "https://github.com/ZenWave360/dsl-kotlin"
            ))
            customField("license", "MIT")
        }
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


// Node.js integration tests - Install dependencies
val nodeIntegrationTestInstall = tasks.register<Exec>("nodeIntegrationTestInstall") {
    group = "verification"
    description = "Install dependencies for Node.js integration tests"

    // Ensure the JS package is fully assembled before installing dependencies
    dependsOn("jsProductionExecutableCompileSync", "jsPackageJson", "kotlinNodeJsSetup")

    workingDir = file("nodejs-test-project")

    // Detect OS and use appropriate command
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val npmCmd = if (isWindows) "npm.cmd" else "npm"

    // Install npm dependencies
    commandLine(npmCmd, "install")
}

// Node.js integration tests
val nodeIntegrationTest = tasks.register<Exec>("nodeIntegrationTest") {
    group = "verification"
    description = "Run Node.js integration tests for the published NPM package"

    dependsOn("nodeIntegrationTestInstall")

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
                // skip generated parser
                packages("io.zenwave360.language.antlr")
            }
        }
    }
}

// Task to print both line and branch coverage
tasks.register("koverPrintCoverageDetailed") {
    group = "verification"
    description = "Prints line and branch coverage percentages"
    dependsOn("koverXmlReport")

    doLast {
        val reportFile = layout.buildDirectory.file("reports/kover/report.xml").get().asFile
        if (reportFile.exists()) {
            val xml = groovy.xml.XmlParser().parse(reportFile)
            val counters = (xml as groovy.util.Node).get("counter") as groovy.util.NodeList

            var lineCovered = 0.0
            var lineMissed = 0.0
            var branchCovered = 0.0
            var branchMissed = 0.0

            counters.forEach { counter ->
                val node = counter as groovy.util.Node
                val type = node.attribute("type") as String
                val covered = (node.attribute("covered") as String).toDouble()
                val missed = (node.attribute("missed") as String).toDouble()

                when (type) {
                    "LINE" -> {
                        lineCovered = covered
                        lineMissed = missed
                    }
                    "BRANCH" -> {
                        branchCovered = covered
                        branchMissed = missed
                    }
                }
            }

            val lineTotal = lineCovered + lineMissed
            val branchTotal = branchCovered + branchMissed

            val linePercentage = if (lineTotal > 0) (lineCovered / lineTotal) * 100 else 0.0
            val branchPercentage = if (branchTotal > 0) (branchCovered / branchTotal) * 100 else 0.0

            println("coverage = %.2f".format(linePercentage))
            println("branches = %.2f".format(branchPercentage))
        }
    }
}
