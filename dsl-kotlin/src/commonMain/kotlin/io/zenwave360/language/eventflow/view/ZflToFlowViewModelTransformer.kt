package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.semantic.ZflSemanticModel

class ZflToFlowViewModelTransformer(
    private val graphTransformer: ZflToFlowGraphTransformer = ZflToFlowGraphTransformer()
) {

    fun transform(semanticModel: ZflSemanticModel): FlowViewModel =
        transformGraph(graphTransformer.transform(semanticModel))

    fun transform(semanticModel: ZflSemanticModel, path: SequencePath): FlowViewModel =
        transform(semanticModel, listOf(path))

    /** The union of the paths: every node and edge any of them reaches (spec:flows/eventflow#ef-r25). */
    fun transform(semanticModel: ZflSemanticModel, paths: List<SequencePath>): FlowViewModel =
        transformGraph(graphTransformer.transform(semanticModel).forPaths(paths))

    fun transformGraph(graph: FlowGraph): FlowViewModel {
        val nodes = graph.nodes.map { node ->
            FlowNode(
                id = node.id,
                type = when (node.type) {
                    FlowGraphNodeType.START -> FlowNodeType.START
                    FlowGraphNodeType.ACTION -> FlowNodeType.COMMAND
                    FlowGraphNodeType.OUTCOME -> FlowNodeType.EVENT
                    FlowGraphNodeType.READ_MODEL -> FlowNodeType.READ_MODEL
                    FlowGraphNodeType.POLICY -> FlowNodeType.POLICY
                },
                label = node.label,
                system = node.system,
                service = node.service,
                sourceRef = node.sourceRef,
                endOutcomeLabels = node.endOutcomeLabels,
                startKind = node.startKind,
                actor = node.actor,
                title = node.title,
                policyKind = node.policyKind,
                timer = node.timer
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

private fun FlowGraph.forPaths(paths: List<SequencePath>): FlowGraph {
    val nodesById = nodes.associateBy { it.id }
    // by execution identity, never by the text drawn: a title or an actor arrow changes the label only
    val pathNodeIds = paths.flatMap { path -> path.messages.flatMap { it.refs } + "event:${path.start.name}" }
        .filterTo(linkedSetOf()) { it in nodesById }

    // Policies are structural links between an event and a command. Keep only
    // the ones that connect two nodes already reached by this execution.
    nodes.filter { it.type == FlowGraphNodeType.POLICY }.forEach { policy ->
        val incoming = edges.any { it.target == policy.id && it.source in pathNodeIds }
        val outgoing = edges.any { it.source == policy.id && it.target in pathNodeIds }
        if (incoming && outgoing) pathNodeIds += policy.id
    }

    return FlowGraph(
        nodes = nodes.filter { it.id in pathNodeIds },
        edges = edges.filter { it.source in pathNodeIds && it.target in pathNodeIds }
    )
}

/** Backward-compatibility alias; use [ZflToFlowViewModelTransformer] for new code. */
typealias ZflToFlowIrTransformer = ZflToFlowViewModelTransformer
