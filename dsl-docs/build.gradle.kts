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
                // Needed on the compile classpath to name MermaidSequenceRenderMode, a serializable enum of dsl-views.
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// The Arcadia snapshot tests parse six ZDL files and render every page: more than mocha's default 2s on Node.
tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>()
    .matching { it.name == "jsNodeTest" }
    .configureEach {
        useMocha {
            timeout = "10s"
        }
    }
