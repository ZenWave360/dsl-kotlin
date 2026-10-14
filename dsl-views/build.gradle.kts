plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.kover)
}

mavenPublishing {
    pom {
        name.set("ZenWave DSL Views")
        description.set("Mermaid diagrams from ZDL and ZFL models, for Kotlin Multiplatform (JVM and JS)")
    }
}

kotlin {
    // JVM and JS targets are declared in the root build; only module specifics here.
    js(IR) {
        browser {
            testTask {
                // Fixtures are read from the filesystem, so the common tests run on Node only.
                // The browser run checks that the library loads without a Node runtime.
                filter.includeTestsMatching("io.zenwave360.language.JsBrowserSmokeTest")
            }
        }
        // Not published to npm yet: the npm packaging of dsl-views and dsl-docs is still open.
        binaries.library()
    }
    sourceSets {
        commonMain {
            dependencies {
                // Views read the parsed models of dsl-kotlin.
                api(project(":dsl-kotlin"))
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
