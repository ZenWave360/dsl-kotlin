package io.zenwave360.language.zfl.semantic

import kotlinx.serialization.Serializable

@Serializable
data class ZflFlow(
    val name: String,
    val commands: List<ZflCommand>,
    val events: List<ZflEvent>,
    val policies: List<ZflPolicy>,
    val whens: List<ZflWhen>
)
