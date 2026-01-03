package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

/**
 * Represents a when-clause in ZFL.
 *
 * Structure: (triggers) → command → (emitted events)
 */
@Serializable
data class ZflWhen(
    val triggers: List<String>,
    val command: String,
    val events: List<String>,
    val sourceRef: SourceRef
)

