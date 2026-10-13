plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.kover)
}

mavenPublishing {
    pom {
        name.set("ZenWave DSL Views")
        description.set("Views from the ZenWave DSL semantic model to diagram text, for Kotlin Multiplatform (JVM and JS)")
    }
}

kotlin {
    // JVM and JS targets are declared in the root build; only module specifics here.
    js(IR) {
        browser {
            testTask {
                // Fixtures are read from the filesystem, so the common tests run on Node only.
                // The browser run checks that the library loads without a Node runtime.
                filter.includeTestsMatching("io.zenwave360.language.views.JsBrowserSmokeTest")
            }
        }
        // Not published to npm yet: the npm packaging of dsl-kotlin and dsl-views is still open.
        binaries.library()
    }
    sourceSets {
        commonMain {
            dependencies {
                // Callers pass dsl-kotlin semantic model objects into views.
                api(project(":dsl-kotlin"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
