package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflEvent(
    val name: String,
    val system: String,
    val isError: Boolean = false,
    val sourceRef: SourceRef
)
