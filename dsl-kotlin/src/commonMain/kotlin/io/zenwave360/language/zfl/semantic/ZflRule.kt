package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

/** A named business rule from a ZFL `rules` block. */
@Serializable
data class ZflRule(
    val name: String,
    val value: String,
    val aggregate: String? = null,
    /** Free annotations (`@invariant`, `@pre`...) by name; null value when written without one. */
    val options: Map<String, String?> = emptyMap(),
    val javadoc: String? = null,
    val sourceRef: SourceRef,
)
