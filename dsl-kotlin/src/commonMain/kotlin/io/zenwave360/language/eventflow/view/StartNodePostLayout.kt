package io.zenwave360.language.eventflow.view

internal object StartNodePostLayout {

    fun apply(
        nodes: List<FlowNode>,
        edges: List<FlowEdge>,
        canvasPadding: Double,
        desiredGap: Double
    ): List<FlowNode> {
        val positionedById = nodes.associateBy { it.id }

        return nodes.map { node ->
            if (node.type != FlowNodeType.START || node.position == null || node.dimensions == null) {
                return@map node
            }

            val targetX = edges.asSequence()
                .filter { it.source == node.id && it.target != node.id }
                .mapNotNull { edge -> positionedById[edge.target]?.position?.x }
                .minOrNull()
                ?: return@map node

            val adjustedX = maxOf(
                canvasPadding,
                targetX - node.dimensions.width - desiredGap
            )

            node.copy(position = node.position.copy(x = adjustedX))
        }
    }
}
