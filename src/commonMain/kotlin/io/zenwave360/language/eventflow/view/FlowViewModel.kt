package io.zenwave360.language.eventflow.view

import kotlinx.serialization.Serializable

@Serializable
data class FlowViewModel(
    val schema: String = "zfl.eventflow.view@1",
    val nodes: List<FlowNodeView>,
    val edges: List<FlowEdgeView>,
    val systemGroups: List<FlowSystemGroupView>,
    val layout: LayoutMetadata,
    val bounds: FlowBounds
)
