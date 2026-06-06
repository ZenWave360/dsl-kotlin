package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflEmission(
    val eventName: String,
    val outcome: String? = null
)

@Serializable
data class ZflCommand(
    val name: String,
    val system: String?,
    val service: String?,
    val servicePath: String?,
    val actor: String?,
    val emits: List<ZflEmission> = emptyList(),
    val responses: List<String> = emptyList(),
    val steps: List<ZflActionStep> = emptyList(),
    val sourceRef: SourceRef
)
