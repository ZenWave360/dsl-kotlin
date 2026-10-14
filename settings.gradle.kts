pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Not "dsl-kotlin": the root shares the group, so that name would clash with the :dsl-kotlin module
// (io.zenwave360.dsl:dsl-kotlin) and Gradle would resolve project(":dsl-kotlin") to the root.
rootProject.name = "dsl-kotlin-parent"

include("dsl-kotlin", "dsl-views", "dsl-docs")
