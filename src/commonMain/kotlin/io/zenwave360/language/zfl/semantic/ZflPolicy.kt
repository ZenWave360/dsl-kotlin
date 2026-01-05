package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflPolicy(
    val description: String?,
    val triggers: List<String>,
    val condition: String?,
    val command: String,
    val events: List<String>,
    val sourceRef: SourceRef
)
