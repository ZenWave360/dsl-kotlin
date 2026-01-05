package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflStart(
    val description: String,
    val name: String,
    val actor: String?,
    val timer: String?,
    val system: String?,
    val sourceRef: SourceRef
)
