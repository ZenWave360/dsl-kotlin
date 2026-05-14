package io.zenwave360.language.eventflow.view

import io.zenwave360.language.source.SourceRef

data class FlowGraph(
    val nodes: List<FlowGraphNode>,
    val edges: List<FlowGraphEdge>
)

data class FlowGraphNode(
    val id: String,
    val type: FlowGraphNodeType,
    val label: String,
    val system: String?,
    val service: String?,
    val sourceRef: SourceRef,
    val endOutcomeLabels: List<String>? = null
)

enum class FlowGraphNodeType {
    START,
    ACTION,
    OUTCOME,
    POLICY
}

data class FlowGraphEdge(
    val id: String,
    val source: String,
    val target: String,
    val type: FlowGraphEdgeType,
    val label: String? = null,
    val sourceRef: SourceRef? = null
)

enum class FlowGraphEdgeType {
    CAUSATION,
    CALL,
    OUTCOME_HANDLER,
    TRIGGER,
    CONDITIONAL
}
