package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflCommand(
    val name: String,
    val system: String,
    val actor: String?,
    val sourceRef: SourceRef
)
