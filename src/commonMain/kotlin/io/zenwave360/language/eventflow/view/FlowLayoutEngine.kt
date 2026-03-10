package io.zenwave360.language.eventflow.view

class FlowLayoutEngine {

    private val rankSpacing = 200.0
    private val nodeSpacing = 80.0
    private val systemGroupPadding = 40.0
    private val canvasPadding = 20.0
    private val laneHeight = 200.0

    fun layout(viewModel: FlowViewModel): FlowViewModel {
        if (viewModel.nodes.isEmpty()) {
            return viewModel.copy(
                nodes = emptyList(),
                edges = emptyList(),
                systemGroups = emptyList(),
                layout = LayoutMetadata(
                    engine = "zfl-timeline",
                    direction = Direction.LR,
                    rankSpacing = rankSpacing,
                    nodeSpacing = nodeSpacing
                ),
                bounds = FlowBounds(0.0, 0.0, 0.0, 0.0)
            )
        }

        // Step 1: Assign nodes to timeline positions based on temporal sequence
        val timeline = assignNodesToTimeline(viewModel.nodes, viewModel.edges)

        // Step 2: Calculate system lanes for vertical positioning
        val systemLanes = calculateSystemLanes(viewModel.nodes)

        // Step 3: Calculate positions for each node
        val positionedNodes = calculateNodePositions(timeline, systemLanes, viewModel.nodes)

        // Step 4: Pass through edges unchanged (FlowEdge is already the unified type)
        val edges = viewModel.edges

        // Step 5: Calculate system groups
        val systemGroups = calculateSystemGroups(positionedNodes)

        // Step 6: Calculate overall bounds
        val bounds = calculateBounds(positionedNodes)

        return viewModel.copy(
            nodes = positionedNodes,
            edges = edges,
            systemGroups = systemGroups,
            layout = LayoutMetadata(
                engine = "zfl-timeline",
                direction = Direction.LR,
                rankSpacing = rankSpacing,
                nodeSpacing = nodeSpacing
            ),
            bounds = bounds
        )
    }

    /**
     * Assigns nodes to timeline positions based on temporal sequence.
     * Timeline follows event storming principles: START → EVENT → POLICY → COMMAND → EVENT → ... → END
     *
     * This implementation uses a topological sort with strict ordering constraints:
     * - Commands must immediately precede their resulting events
     * - Events must immediately precede any policies they trigger
     * - END nodes are always positioned at the rightmost position
     *
     * Returns a map of timeline position to list of node IDs.
     */
    private fun assignNodesToTimeline(nodes: List<FlowNode>, edges: List<FlowEdge>): Map<Int, List<String>> {
        val nodeMap = nodes.associateBy { it.id }
        val nodePosition = mutableMapOf<String, Int>()
        val outEdges = mutableMapOf<String, MutableList<String>>()
        val inEdges = mutableMapOf<String, MutableList<String>>()
        val inDegree = mutableMapOf<String, Int>()

        // Build adjacency lists and in-degree map
        nodes.forEach { node ->
            outEdges[node.id] = mutableListOf()
            inEdges[node.id] = mutableListOf()
            inDegree[node.id] = 0
        }

        edges.forEach { edge ->
            if (nodeMap.containsKey(edge.source) && nodeMap.containsKey(edge.target)) {
                outEdges[edge.source]?.add(edge.target)
                inEdges[edge.target]?.add(edge.source)
                inDegree[edge.target] = (inDegree[edge.target] ?: 0) + 1
            }
        }

        // Separate nodes by type for processing
        val startNodes = nodes.filter { it.type == FlowNodeType.START }.sortedBy { it.id }
        val endNodes = nodes.filter { it.type == FlowNodeType.END }.sortedBy { it.id }

        // Position 0: All START nodes
        var currentPosition = 0
        startNodes.forEach { node ->
            nodePosition[node.id] = currentPosition
        }
        if (startNodes.isNotEmpty()) {
            currentPosition++
        }

        // Use modified topological sort with strict temporal ordering
        val processed = mutableSetOf<String>()
        startNodes.forEach { processed.add(it.id) }

        // Process nodes in waves, respecting dependencies and event storming order
        while (processed.size < nodes.size - endNodes.size) {
            val readyNodes = nodes.filter { node ->
                !processed.contains(node.id) &&
                node.type != FlowNodeType.END &&
                (inEdges[node.id]?.all { processed.contains(it) } ?: true)
            }

            if (readyNodes.isEmpty()) {
                // Handle remaining unprocessed nodes (disconnected or cyclic)
                nodes.filter { !processed.contains(it.id) && it.type != FlowNodeType.END }.forEach { node ->
                    nodePosition[node.id] = currentPosition
                    processed.add(node.id)
                }
                if (nodePosition.values.any { it == currentPosition }) {
                    currentPosition++
                }
                break
            }

            // Group ready nodes by type and process in event storming order
            val nodesByType = readyNodes.groupBy { it.type }

            // Process in strict order: EVENT → POLICY → COMMAND
            val processingOrder = listOf(
                FlowNodeType.EVENT,
                FlowNodeType.POLICY,
                FlowNodeType.COMMAND
            )

            processingOrder.forEach { type ->
                nodesByType[type]?.sortedBy { it.id }?.forEach { node ->
                    nodePosition[node.id] = currentPosition
                    processed.add(node.id)
                }
            }

            currentPosition++
        }

        // Position END nodes at the rightmost position
        endNodes.forEach { node ->
            nodePosition[node.id] = currentPosition
        }

        // Group nodes by their timeline position
        val timeline = mutableMapOf<Int, MutableList<String>>()
        nodePosition.forEach { (nodeId, position) ->
            timeline.getOrPut(position) { mutableListOf() }.add(nodeId)
        }

        // Sort nodes within each timeline position for stable ordering
        return timeline.mapValues { (_, nodeIds) ->
            nodeIds.sortedBy { it }
        }
    }

    /**
     * Calculates system lanes for vertical positioning.
     * Each system gets its own horizontal lane (y-axis grouping).
     * Returns a map of system name to lane index.
     */
    private fun calculateSystemLanes(nodes: List<FlowNode>): Map<String?, Int> {
        val systems = nodes.mapNotNull { it.system }.distinct().sorted()
        val lanes = mutableMapOf<String?, Int>()

        // Assign lane 0 to null system (default lane)
        lanes[null] = 0

        // Assign lanes to other systems
        var laneIndex = 1
        systems.forEach { system ->
            lanes[system] = laneIndex++
        }

        return lanes
    }

    /**
     * Calculates positions for all nodes based on their timeline position and system lane.
     * X-coordinate is determined by timeline position (temporal sequence).
     * Y-coordinate is determined by system lane (swim lane).
     */
    private fun calculateNodePositions(
        timeline: Map<Int, List<String>>,
        systemLanes: Map<String?, Int>,
        nodes: List<FlowNode>
    ): List<FlowNode> {
        val nodeMap = nodes.associateBy { it.id }
        val positionedNodes = mutableListOf<FlowNode>()

        // Track vertical position within each lane at each timeline position
        val lanePositions = mutableMapOf<Pair<Int, Int>, Double>()

        timeline.entries.sortedBy { it.key }.forEach { (timelinePos, nodeIds) ->
            val x = canvasPadding + timelinePos.toDouble() * rankSpacing

            // Group nodes by system lane
            val nodesByLane = nodeIds.groupBy { nodeId ->
                val node = nodeMap[nodeId]
                systemLanes[node?.system] ?: 0
            }

            nodesByLane.entries.sortedBy { it.key }.forEach { (laneIndex, laneNodeIds) ->
                // Calculate base Y position for this lane
                val baseY = canvasPadding + laneIndex.toDouble() * laneHeight

                // Get current Y position within this lane at this timeline position
                val key = Pair(timelinePos, laneIndex)
                var y = lanePositions.getOrDefault(key, baseY)

                laneNodeIds.sortedBy { it }.forEach { nodeId ->
                    val node = nodeMap[nodeId] ?: return@forEach
                    val dimensions = semanticNodeSize(node.type)

                    positionedNodes.add(
                        node.copy(
                            position = Point(x, y),
                            dimensions = dimensions
                        )
                    )

                    y += dimensions.height + nodeSpacing
                    lanePositions[key] = y
                }
            }
        }

        return positionedNodes
    }

    /**
     * Returns estimated dimensions based on node type.
     */
    private fun semanticNodeSize(type: FlowNodeType): Dimensions {
        return when (type) {
            FlowNodeType.START -> Dimensions(width = 180.0, height = 56.0)
            FlowNodeType.COMMAND -> Dimensions(width = 180.0, height = 56.0)
            FlowNodeType.EVENT -> Dimensions(width = 160.0, height = 48.0)
            FlowNodeType.POLICY -> Dimensions(width = 220.0, height = 64.0)
            FlowNodeType.END -> Dimensions(width = 180.0, height = 56.0)
        }
    }

    /**
     * Calculates bounding boxes for system groups.
     * Expects nodes with position/dimensions already set.
     */
    private fun calculateSystemGroups(nodes: List<FlowNode>): List<FlowSystemGroupView> {
        val systemNodes = nodes.groupBy { it.system }

        return systemNodes.mapNotNull { (systemName, systemNodeList) ->
            if (systemName == null) return@mapNotNull null

            val minX = systemNodeList.minOf { it.position!!.x } - systemGroupPadding
            val minY = systemNodeList.minOf { it.position!!.y } - systemGroupPadding
            val maxX = systemNodeList.maxOf { it.position!!.x + it.dimensions!!.width } + systemGroupPadding
            val maxY = systemNodeList.maxOf { it.position!!.y + it.dimensions!!.height } + systemGroupPadding

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
     * Expects nodes with position/dimensions already set.
     */
    private fun calculateBounds(nodes: List<FlowNode>): FlowBounds {
        if (nodes.isEmpty()) {
            return FlowBounds(0.0, 0.0, 0.0, 0.0)
        }

        val minX = nodes.minOf { it.position!!.x }
        val minY = nodes.minOf { it.position!!.y }
        val maxX = nodes.maxOf { it.position!!.x + it.dimensions!!.width }
        val maxY = nodes.maxOf { it.position!!.y + it.dimensions!!.height }

        return FlowBounds(
            x = 0.0,
            y = 0.0,
            width = maxX + canvasPadding,
            height = maxY + canvasPadding
        )
    }
}
