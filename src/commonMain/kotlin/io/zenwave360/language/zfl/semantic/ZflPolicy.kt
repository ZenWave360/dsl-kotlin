package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflPolicy(
    val name: String,
    val fromEvent: String,
    val toCommand: String,
    val system: String,
    val sourceRef: SourceRef
)
