package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflEnd(
    val completed: List<String> = emptyList(),
    val suspended: List<String> = emptyList(),
    val cancelled: List<String> = emptyList(),
    val sourceRef: SourceRef
)
