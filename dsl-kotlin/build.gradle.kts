plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.antlr.kotlin)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.kover)
}

// npm prereleases use a separate opt-in train; keep Maven snapshot publishing intact.
val npmVersion = providers.gradleProperty("npmVersion")
    .getOrElse(version.toString().replace("-SNAPSHOT", "-next.0"))

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
    from("src/commonMain/antlr") {
        include("**/*.g4") // only copy grammar files; _parser_members.java.txt is read at config time and must not be filtered (its shared `skipping` state would corrupt subsequent files)
    }
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
    pom {
        name.set("ZDL Kotlin Multiplatform")
        description.set("ZenWave Domain Language (ZDL) parser for Kotlin Multiplatform (JVM and JS)")
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
}

kotlin {
    // JVM and JS targets are declared in the root build; only module specifics here.
    js(IR) {
        browser {
            testTask {
                // The common test suite reads its fixtures from the filesystem, so it runs on Node
                // only. In the browser, JsBrowserSmokeTest loads the library and exercises it without
                // a Node runtime; a static Node import anywhere in the bundle fails this run.
                filter.includeTestsMatching("io.zenwave360.language.JsBrowserSmokeTest")
            }
        }
        // Keep the file names of the npm package (kotlin/dsl-kotlin.mjs, .d.mts) as in the single-module build.
        outputModuleName = "dsl-kotlin"
        binaries.executable()

        // Generate ES modules instead of CommonJS
        useEsModules()
        generateTypeScriptDefinitions()

        // Set the NPM package name to use scoped naming for main compilation only
        compilations["main"].packageJson {
            customField("name", "@zenwave360/dsl")
            customField("version", npmVersion)
            customField("description", "ZenWave Domain and Flow Language (ZDL and ZFL) parsers for JavaScript/TypeScript")
            customField("keywords", listOf("zdl", "zfl", "domain-driven-design", "event-storming"))
            // .mjs exports remain ES modules; generated webpack.config.js is CommonJS.
            customField("types", "kotlin/dsl-kotlin.d.mts")
            customField("files", listOf("kotlin/", "README.md", "LICENSE"))
            customField("publishConfig", mapOf(
                "access" to "public",
                "registry" to "https://registry.npmjs.org/",
                "tag" to if (npmVersion.contains("-")) "next" else "latest"
            ))
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
                implementation("com.strumenta:antlr-kotlin-runtime:1.0.4")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
            kotlin.srcDir(generateKotlinGrammarSource)
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                compileOnly("org.antlr:antlr4-runtime:$antlrVersion")
                implementation("org.eclipse.elk:org.eclipse.elk.alg.layered:0.10.0")
            }
        }
        val jvmTest by getting
        val jsMain by getting {
            dependencies {
                implementation(npm("elkjs", "0.9.3"))
            }
        }
        val jsTest by getting
    }
}

// Include the documentation and license in the package consumed by npm and CI.
tasks.named("jsProductionExecutableCompileSync") {
    val npmDocumentation = rootProject.files("README.md", "LICENSE")
    inputs.files(npmDocumentation).withPropertyName("npmDocumentation")
    doLast {
        copy {
            from(npmDocumentation)
            into(rootProject.layout.buildDirectory.dir("js/packages/dsl-kotlin"))
        }
    }
}

java {
    sourceSets {
        getByName("jvmMain") {
            java.srcDirs(generateJavaGrammarSource)
        }
    }
}

tasks.clean {
    delete("bin")
}

tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>()
    .matching { it.name == "jsNodeTest" }
    .configureEach {
        useMocha {
            timeout = "10s"
        }
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


// Task to print both line and branch coverage
tasks.register("koverPrintCoverageDetailed") {
    group = "verification"
    description = "Prints line and branch coverage percentages"
    dependsOn("koverXmlReport")

    doLast {
        val reportFile = layout.buildDirectory.file("reports/kover/report.xml").get().asFile
        if (reportFile.exists()) {
            // JaCoCo reports include a DTD declaration. Strip it before parsing so the
            // secure XML parser does not need to enable external entity resolution.
            val reportXml = reportFile.readText().replace(Regex("<!DOCTYPE[^>]*>"), "")
            val xml = groovy.xml.XmlParser().parseText(reportXml)
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


