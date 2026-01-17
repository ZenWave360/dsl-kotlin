plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    id("com.strumenta.antlr-kotlin") version "1.0.9"
    id("com.vanniktech.maven.publish") version "0.31.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.4"
}

group = "io.zenwave360.dsl"
version = "1.6.0-SNAPSHOT"

val antlrVersion = "4.13.2"

val antlrTool by configurations.creating

dependencies {
    antlrTool("org.antlr:antlr4:$antlrVersion")
}

val generateKotlinGrammarSource by tasks.registering(com.strumenta.antlrkotlin.gradle.AntlrKotlinTask::class) {
    dependsOn("cleanGenerateKotlinGrammarSource")
    source = fileTree("src/commonMain/antlr") { include("**/*.g4") }
    packageName = "io.zenwave360.language.antlr"
    outputDirectory = layout.buildDirectory.dir("generated/antlr/commonMain/kotlin").get().asFile
}

val prepareJavaGrammar = tasks.register("prepareJavaGrammar", Copy::class) {
    from("src/commonMain/antlr")
    into("build/generated/antlr-java")

    val javaMembersContent = file("src/commonMain/antlr/io.zenwave360.language.antlr/_parser_members.java.txt").readText()

    var skipping = false
    filter { line ->
        when {
            line.contains("@parser::members {") -> {
                skipping = true
                javaMembersContent
            }
            line.contains("} // end members") -> {
                skipping = false
                ""
            }
            // While between start and end, discard the Kotlin lines
            skipping -> ""
            else -> line
        }
    }

}

val generateJavaGrammarSource by tasks.registering(JavaExec::class) {
    dependsOn(prepareJavaGrammar)

    group = "antlr"
    description = "Genera parsers ANTLR en Java para target JVM"
    classpath = antlrTool
    mainClass.set("org.antlr.v4.Tool")
    val outputDir = layout.buildDirectory.dir("generated/antlr/jvmMain/java/io/zenwave360/language/antlr/java").get().asFile
    args = listOf(
        "-no-visitor",
        "-no-listener",
        "-Xexact-output-dir",
        "-o", outputDir.absolutePath,
        "-package", "io.zenwave360.language.antlr.java"
    ) + fileTree("build/generated/antlr-java") { include("**/*.g4") }.files.map { it.absolutePath }

    inputs.dir("src/commonMain/antlr")
    outputs.dir(layout.buildDirectory.dir("generated/antlr/jvmMain/java/").get().asFile)
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

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
            connection.set("scm:git:git://github.com/ZenWave360/dsl-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/ZenWave360/dsl-kotlin.git")
            url.set("https://github.com/ZenWave360/dsl-kotlin")
        }
    }
}

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        withJava() // Enables Java compilation for the JVM target
    }
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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("com.strumenta:antlr-kotlin-runtime:1.0.3")
            }
            kotlin.srcDir(generateKotlinGrammarSource)
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                compileOnly("org.antlr:antlr4-runtime:$antlrVersion")
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

java {
    sourceSets {
        getByName("main") {
            java.srcDirs(generateJavaGrammarSource)
        }
    }
}

tasks.clean {
    delete("bin")
}

// generateJavaGrammarSource must run before jvmProcessResources
tasks.named("jvmProcessResources") { dependsOn(generateJavaGrammarSource) }
tasks.named("compileKotlinJvm") { dependsOn(generateJavaGrammarSource) }
tasks.withType<JavaCompile>().configureEach { dependsOn(generateJavaGrammarSource) }

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


