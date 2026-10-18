package io.zenwave360.language.source

import kotlinx.serialization.Serializable

/**
 * Represents a precise location in a source file.
 *
 * Lines and columns are 1-based.
 */
@Serializable
data class SourceRef(
    val file: String,
    val line: Int,
    val column: Int
) {

    init {
        require(line >= 1) { "line must be >= 1 (was $line)" }
        require(column >= 1) { "column must be >= 1 (was $column)" }
    }

    override fun toString(): String =
        "$file:$line:$column"
}
