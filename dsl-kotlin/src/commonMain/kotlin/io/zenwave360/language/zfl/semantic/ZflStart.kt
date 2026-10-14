package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflField(
    val name: String,
    val type: String?,
    val isArray: Boolean = false,
    val description: String? = null,
    val options: Map<String, String?> = emptyMap(),
)

@Serializable
data class ZflStart(
    val description: String,
    val name: String,
    val actor: String?,
    val timer: String?,
    val system: String?,
    val fields: Map<String, ZflField> = emptyMap(),
    val sourceRef: SourceRef
)
