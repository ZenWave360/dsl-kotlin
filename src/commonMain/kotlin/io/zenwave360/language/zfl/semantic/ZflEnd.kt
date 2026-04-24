package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflEnd(
    val outcomes: Map<String, List<String>> = emptyMap(),
    val sourceRef: SourceRef
)
