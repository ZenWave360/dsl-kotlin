package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.semantic.ZflSemanticModel

class ZflToFlowViewModelTransformer(
    private val graphTransformer: ZflToFlowGraphTransformer = ZflToFlowGraphTransformer()
) {

    fun transform(semanticModel: ZflSemanticModel): FlowViewModel =
        transformGraph(graphTransformer.transform(semanticModel))

    fun transformGraph(graph: FlowGraph): FlowViewModel {
        val nodes = graph.nodes.map { node ->
            FlowNode(
                id = node.id,
                type = when (node.type) {
                    FlowGraphNodeType.START -> FlowNodeType.START
                    FlowGraphNodeType.ACTION -> FlowNodeType.COMMAND
                    FlowGraphNodeType.OUTCOME -> FlowNodeType.EVENT
                    FlowGraphNodeType.POLICY -> FlowNodeType.POLICY
                },
                label = node.label,
                system = node.system,
                service = node.service,
                sourceRef = node.sourceRef,
                endOutcomeLabels = node.endOutcomeLabels
            )
        }

        val edges = graph.edges.map { edge ->
            FlowEdge(
                id = edge.id,
                source = edge.source,
                target = edge.target,
                type = when (edge.type) {
                    FlowGraphEdgeType.CAUSATION -> FlowEdgeType.CAUSATION
                    FlowGraphEdgeType.CALL -> FlowEdgeType.CALL
                    FlowGraphEdgeType.OUTCOME_HANDLER -> FlowEdgeType.OUTCOME_HANDLER
                    FlowGraphEdgeType.TRIGGER -> FlowEdgeType.TRIGGER
                    FlowGraphEdgeType.CONDITIONAL -> FlowEdgeType.CONDITIONAL
                },
                label = edge.label,
                outcome = edge.outcome,
                sourceRef = edge.sourceRef
            )
        }

        return FlowViewModel(
            nodes = nodes,
            edges = edges
        )
    }
}

/** Backward-compatibility alias; use [ZflToFlowViewModelTransformer] for new code. */
typealias ZflToFlowIrTransformer = ZflToFlowViewModelTransformer
