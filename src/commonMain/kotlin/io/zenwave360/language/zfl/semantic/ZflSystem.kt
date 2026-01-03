package io.zenwave360.language.zfl.semantic

import kotlinx.serialization.Serializable

@Serializable
data class ZflSystem(
    val name: String,
    val boundedContext: Boolean = true
)
