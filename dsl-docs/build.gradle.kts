plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.kover)
}

mavenPublishing {
    pom {
        name.set("ZenWave DSL Docs")
        description.set("Markdown documentation from ZDL, ZFL, OpenAPI and AsyncAPI files, for Kotlin Multiplatform (JVM and JS)")
    }
}

kotlin {
    // JVM and JS targets are declared in the root build; only module specifics here.
    js(IR) {
        browser {
            testTask {
                // Fixtures are read from the filesystem, so the common tests run on Node only.
                // The browser run checks that the library loads without a Node runtime.
                filter.includeTestsMatching("io.zenwave360.language.docs.JsBrowserSmokeTest")
            }
        }
        // Not published to npm yet: the npm packaging of dsl-kotlin, dsl-views and dsl-docs is still open.
        binaries.library()
    }
    sourceSets {
        commonMain {
            dependencies {
                // Diagrams and model views come from dsl-views (and dsl-kotlin through it).
                api(project(":dsl-views"))
                implementation("it.krzeminski:snakeyaml-engine-kmp:4.0.1")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// The JVM jar is also the command line tool: `java -jar` or `jbang io.zenwave360.dsl:dsl-docs-jvm:<version> build ...`.
tasks.named<Jar>("jvmJar") {
    manifest {
        attributes("Main-Class" to "io.zenwave360.language.docs.cli.DslDocsCliKt")
    }
}

// Runs the command line tool from the build, for example:
//   ./gradlew :dsl-docs:dslDocs -Pargs="build --config path/to/dsl-docs.yml --out docs/zenwave"
val jvmMainCompilation = kotlin.jvm().compilations.getByName("main")
tasks.register<JavaExec>("dslDocs") {
    group = "documentation"
    description = "Runs the dsl-docs command line tool with -Pargs"
    classpath = jvmMainCompilation.output.allOutputs + jvmMainCompilation.runtimeDependencyFiles!!
    mainClass.set("io.zenwave360.language.docs.cli.DslDocsCliKt")
    workingDir = rootProject.projectDir
    args(providers.gradleProperty("args").getOrElse("").split(" ").filter { it.isNotBlank() })
}
