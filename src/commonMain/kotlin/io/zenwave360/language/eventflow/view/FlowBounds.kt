package io.zenwave360.language.eventflow.view

import kotlinx.serialization.Serializable

@Serializable
data class FlowBounds(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double
)
