package io.zenwave360.language.eventflow.view

import io.zenwave360.language.eventflow.ir.FlowEdge
import io.zenwave360.language.eventflow.ir.FlowIR
import io.zenwave360.language.eventflow.ir.FlowNode
import io.zenwave360.language.eventflow.ir.FlowNodeType

class FlowLayoutEngine {

    private val rankSpacing = 200.0
    private val nodeSpacing = 80.0
    private val systemGroupPadding = 40.0
    private val canvasPadding = 20.0

    fun layout(flowIR: FlowIR): FlowViewModel {
        if (flowIR.nodes.isEmpty()) {
            return FlowViewModel(
                nodes = emptyList(),
                edges = emptyList(),
                systemGroups = emptyList(),
                layout = LayoutMetadata(
                    engine = "zfl-layered",
                    direction = Direction.LR,
                    rankSpacing = rankSpacing,
                    nodeSpacing = nodeSpacing
                ),
                bounds = FlowBounds(0.0, 0.0, 0.0, 0.0)
            )
        }

        // Step 1: Assign nodes to layers (ranks) based on topological order
        val layers = assignNodesToLayers(flowIR.nodes, flowIR.edges)

        // Step 2: Calculate positions for each node
        val nodeViews = calculateNodePositions(layers, flowIR.nodes)

        // Step 3: Create edge views
        val edgeViews = flowIR.edges.map { edge ->
            FlowEdgeView(
                id = edge.id,
                source = edge.source,
                target = edge.target,
                type = edge.type,
                label = edge.label,
                sourceRef = edge.sourceRef
            )
        }

        // Step 4: Calculate system groups
        val systemGroups = calculateSystemGroups(nodeViews)

        // Step 5: Calculate overall bounds
        val bounds = calculateBounds(nodeViews)

        return FlowViewModel(
            nodes = nodeViews,
            edges = edgeViews,
            systemGroups = systemGroups,
            layout = LayoutMetadata(
                engine = "zfl-layered",
                direction = Direction.LR,
                rankSpacing = rankSpacing,
                nodeSpacing = nodeSpacing
            ),
            bounds = bounds
        )
    }

    /**
     * Assigns nodes to layers based on topological order.
     * Returns a map of layer index to list of node IDs.
     */
    private fun assignNodesToLayers(nodes: List<FlowNode>, edges: List<FlowEdge>): Map<Int, List<String>> {
        val nodeMap = nodes.associateBy { it.id }
        val inDegree = mutableMapOf<String, Int>()
        val outEdges = mutableMapOf<String, MutableList<String>>()

        // Initialize in-degree and out-edges
        nodes.forEach { node ->
            inDegree[node.id] = 0
            outEdges[node.id] = mutableListOf()
        }

        edges.forEach { edge ->
            if (nodeMap.containsKey(edge.source) && nodeMap.containsKey(edge.target)) {
                inDegree[edge.target] = (inDegree[edge.target] ?: 0) + 1
                outEdges[edge.source]?.add(edge.target)
            }
        }

        // Topological sort with layer assignment
        val layers = mutableMapOf<Int, MutableList<String>>()
        val nodeLayer = mutableMapOf<String, Int>()
        val queue = ArrayDeque<Pair<String, Int>>()

        // Start with nodes that have no incoming edges
        inDegree.forEach { (nodeId, degree) ->
            if (degree == 0) {
                queue.add(nodeId to 0)
                nodeLayer[nodeId] = 0
            }
        }

        while (queue.isNotEmpty()) {
            val (nodeId, layer) = queue.removeFirst()
            layers.getOrPut(layer) { mutableListOf() }.add(nodeId)

            outEdges[nodeId]?.forEach { targetId ->
                val currentDegree = inDegree[targetId] ?: 0
                inDegree[targetId] = currentDegree - 1

                if (inDegree[targetId] == 0) {
                    val targetLayer = layer + 1
                    nodeLayer[targetId] = targetLayer
                    queue.add(targetId to targetLayer)
                }
            }
        }

        // Handle any remaining nodes (cycles or disconnected nodes)
        nodes.forEach { node ->
            if (!nodeLayer.containsKey(node.id)) {
                val layer = (nodeLayer.values.maxOrNull() ?: -1) + 1
                layers.getOrPut(layer) { mutableListOf() }.add(node.id)
                nodeLayer[node.id] = layer
            }
        }

        // Sort nodes within each layer for stable ordering (by ID)
        return layers.mapValues { (_, nodeIds) ->
            nodeIds.sortedBy { it }
        }
    }

    /**
     * Calculates positions for all nodes based on their layer assignment.
     */
    private fun calculateNodePositions(
        layers: Map<Int, List<String>>,
        nodes: List<FlowNode>
    ): List<FlowNodeView> {
        val nodeMap = nodes.associateBy { it.id }
        val nodeViews = mutableListOf<FlowNodeView>()

        layers.entries.sortedBy { it.key }.forEach { (layerIndex, nodeIds) ->
            val x = canvasPadding + layerIndex.toDouble() * rankSpacing
            var y = canvasPadding

            // Group nodes by system within the layer for better visual grouping
            val groupedNodes = nodeIds.groupBy { nodeId -> nodeMap[nodeId]?.system }
            val sortedGroups = groupedNodes.entries.sortedBy { it.key ?: "" }

            sortedGroups.forEach { (system, groupNodeIds) ->
                groupNodeIds.forEach { nodeId ->
                    val node = nodeMap[nodeId] ?: return@forEach
                    val dimensions = semanticNodeSize(node.type)

                    nodeViews.add(
                        FlowNodeView(
                            id = node.id,
                            type = node.type,
                            label = node.label,
                            position = Point(x, y),
                            dimensions = dimensions,
                            system = node.system,
                            sourceRef = node.sourceRef
                        )
                    )

                    y += dimensions.height + nodeSpacing
                }
            }
        }

        return nodeViews
    }

    /**
     * Returns estimated dimensions based on node type.
     */
    private fun semanticNodeSize(type: FlowNodeType): Dimensions {
        return when (type) {
            FlowNodeType.COMMAND -> Dimensions(width = 180.0, height = 56.0)
            FlowNodeType.EVENT -> Dimensions(width = 160.0, height = 48.0)
            FlowNodeType.POLICY -> Dimensions(width = 220.0, height = 64.0)
        }
    }

    /**
     * Calculates bounding boxes for system groups.
     */
    private fun calculateSystemGroups(nodeViews: List<FlowNodeView>): List<FlowSystemGroupView> {
        val systemNodes = nodeViews.groupBy { it.system }

        return systemNodes.mapNotNull { (systemName, nodes) ->
            if (systemName == null) return@mapNotNull null

            val minX = nodes.minOf { it.position.x } - systemGroupPadding
            val minY = nodes.minOf { it.position.y } - systemGroupPadding
            val maxX = nodes.maxOf { it.position.x + it.dimensions.width } + systemGroupPadding
            val maxY = nodes.maxOf { it.position.y + it.dimensions.height } + systemGroupPadding

            FlowSystemGroupView(
                systemName = systemName,
                bounds = FlowBounds(
                    x = minX,
                    y = minY,
                    width = maxX - minX,
                    height = maxY - minY
                )
            )
        }
    }

    /**
     * Calculates overall bounds of the flow diagram.
     */
    private fun calculateBounds(nodeViews: List<FlowNodeView>): FlowBounds {
        if (nodeViews.isEmpty()) {
            return FlowBounds(0.0, 0.0, 0.0, 0.0)
        }

        val minX = nodeViews.minOf { it.position.x }
        val minY = nodeViews.minOf { it.position.y }
        val maxX = nodeViews.maxOf { it.position.x + it.dimensions.width }
        val maxY = nodeViews.maxOf { it.position.y + it.dimensions.height }

        return FlowBounds(
            x = 0.0,
            y = 0.0,
            width = maxX + canvasPadding,
            height = maxY + canvasPadding
        )
    }
}
