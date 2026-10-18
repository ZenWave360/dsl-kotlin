package io.zenwave360.language.eventflow.view

import kotlinx.serialization.Serializable

@Serializable
data class FlowSystemGroupView(
    val systemName: String,
    val bounds: FlowBounds
)
