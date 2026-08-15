package io.zenwave360.language.zfl.semantic

import kotlinx.serialization.Serializable

@Serializable
data class ZflFlow(
    val name: String,
    val description: String,
    val options: Map<String, String?> = emptyMap(),
    val starts: List<ZflStart>,
    val policies: List<ZflPolicy>,
    val commands: List<ZflCommand>,
    val events: List<ZflEvent>,
    val end: ZflEnd,
    val sourceRef: io.zenwave360.language.source.SourceRef? = null,
)
