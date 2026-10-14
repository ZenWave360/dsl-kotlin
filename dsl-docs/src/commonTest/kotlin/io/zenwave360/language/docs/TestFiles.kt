package io.zenwave360.language.docs

/** Reads a file under dsl-docs/src/commonTest/resources, with line endings normalized to `\n`. */
expect fun readTestFile(path: String): String

/**
 * Writes a snapshot under dsl-docs/src/commonTest/resources when the environment variable
 * DSL_DOCS_UPDATE_SNAPSHOTS is `true`, and returns whether it did. Only the JVM tests write.
 */
expect fun updateSnapshot(path: String, content: String): Boolean

