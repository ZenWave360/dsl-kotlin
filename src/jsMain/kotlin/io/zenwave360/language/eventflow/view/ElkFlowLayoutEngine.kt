package io.zenwave360.language.eventflow.view

import kotlinx.coroutines.await

/**
 * JS/Node.js actual for [ElkFlowLayoutEngine].
 *
 * Delegates to the ELK.js library (`elkjs` npm package) via the [ELK] external class.
 * Because ELK.js is Promise-based the [layout] function is a suspend function, which on
 * the JS target compiles to a function returning a Promise — a natural fit for Node.js
 * and for a Kotlin Multiplatform LSP server whose request handlers are already suspend.
 *
 * Graph construction:
 *  - All nodes are placed in a flat ELK graph (no hierarchy) so ELK's layered algorithm
 *    drives temporal ordering purely from edges: START → EVENT → POLICY → COMMAND → END.
 *  - Node sizes are set to semantic dimensions matching [FlowLayoutEngine].
 *  - Layout direction is LEFT→RIGHT (`elk.direction = RIGHT`).
 *
 * Post-layout:
 *  - ELK-computed x/y coordinates are mapped back to [FlowNode.position].
 *  - [FlowViewModel.systemGroups] swim lanes are derived from node metadata.
 *  - [FlowViewModel.bounds] is computed from the positioned nodes.
 */
actual class ElkFlowLayoutEngine actual constructor() {

    private val rankSpacing = 80.0
    private val nodeSpacing = 120.0
    private val canvasPadding = 20.0
    private val systemGroupPadding = 40.0

    actual suspend fun layout(viewModel: FlowViewModel): FlowViewModel {
        if (viewModel.nodes.isEmpty()) {
            return viewModel.copy(
                nodes = emptyList(),
                edges = emptyList(),
                layout = LayoutMetadata(
                    engine = "elk-layered",
                    direction = Direction.LR,
                    rankSpacing = rankSpacing,
                    nodeSpacing = nodeSpacing
                ),
                bounds = FlowBounds(0.0, 0.0, 0.0, 0.0)
            )
        }

        val elkGraph = buildElkGraph(viewModel)
        val result = ELK().layout(elkGraph).await()
        return buildPositionedViewModel(viewModel, result)
    }

    private fun buildElkGraph(viewModel: FlowViewModel): dynamic {
        val partitions = ElkLayoutConstraints.partitions(viewModel)
        val modelOrder = ElkLayoutConstraints.nodeModelOrder(viewModel)
        val layoutOptions = js("{}")
        layoutOptions["elk.algorithm"] = "layered"
        layoutOptions["elk.direction"] = "RIGHT"
        layoutOptions["elk.spacing.nodeNode"] = nodeSpacing
        layoutOptions["elk.layered.spacing.nodeNodeBetweenLayers"] = rankSpacing
        layoutOptions["elk.partitioning.activate"] = true
        layoutOptions["elk.layered.considerModelOrder.strategy"] = "NODES_AND_EDGES"
        layoutOptions["elk.layered.crossingMinimization.forceNodeModelOrder"] = true

        val children = viewModel.nodes
            .sortedBy { modelOrder[it.id] ?: Int.MAX_VALUE }
            .mapIndexed { index, node ->
            val dims = semanticNodeSize(node.type)
            val child = js("{}")
            val childLayoutOptions = js("{}")
            child.id = node.id
            child.width = dims.width
            child.height = dims.height
            childLayoutOptions["elk.partitioning.partition"] = partitions[node.id] ?: 0
            childLayoutOptions["elk.priority"] = index
            child.layoutOptions = childLayoutOptions
            child
        }.toTypedArray()

        val edges = viewModel.edges
            .filter { edge -> edge.source != edge.target }
            .sortedWith(
                compareBy<FlowEdge>(
                    { modelOrder[it.source] ?: Int.MAX_VALUE },
                    { modelOrder[it.target] ?: Int.MAX_VALUE },
                    { edgeTypePriority(it.type) }
                )
            )
            .map { edge ->
                val e = js("{}")
                e.id = edge.id
                e.sources = arrayOf(edge.source)
                e.targets = arrayOf(edge.target)
                e
            }
            .toTypedArray()

        val graph = js("{}")
        graph.id = "root"
        graph.layoutOptions = layoutOptions
        graph.children = children
        graph.edges = edges
        return graph
    }

    private fun buildPositionedViewModel(viewModel: FlowViewModel, elkResult: dynamic): FlowViewModel {
        val nodePositions = mutableMapOf<String, Pair<Double, Double>>()
        val children = elkResult.children.unsafeCast<Array<dynamic>>()
        children.forEach { elkNode ->
            val id = elkNode.id as String
            val x = (elkNode.x as Number).toDouble()
            val y = (elkNode.y as Number).toDouble()
            nodePositions[id] = Pair(x, y)
        }

        val positionedNodes = viewModel.nodes.map { node ->
            val (x, y) = nodePositions[node.id] ?: Pair(0.0, 0.0)
            node.copy(
                position = Point(canvasPadding + x, canvasPadding + y),
                dimensions = semanticNodeSize(node.type)
            )
        }
        val adjustedNodes = StartNodePostLayout.apply(
            nodes = positionedNodes,
            edges = viewModel.edges,
            canvasPadding = canvasPadding,
            desiredGap = rankSpacing
        )

        return viewModel.copy(
            nodes = adjustedNodes,
            edges = viewModel.edges,
            layout = LayoutMetadata(
                engine = "elk-layered",
                direction = Direction.LR,
                rankSpacing = rankSpacing,
                nodeSpacing = nodeSpacing
            ),
            systemGroups = calculateSystemGroups(adjustedNodes),
            bounds = calculateBounds(adjustedNodes)
        )
    }

    private fun edgeTypePriority(type: FlowEdgeType): Int = when (type) {
        FlowEdgeType.CALL -> 0
        FlowEdgeType.CAUSATION -> 1
        FlowEdgeType.OUTCOME_HANDLER -> 2
        FlowEdgeType.TRIGGER -> 3
        FlowEdgeType.CONDITIONAL -> 4
        FlowEdgeType.ERROR -> 5
    }

    private fun semanticNodeSize(type: FlowNodeType): Dimensions = when (type) {
        FlowNodeType.START   -> Dimensions(width = 180.0, height = 56.0)
        FlowNodeType.COMMAND -> Dimensions(width = 180.0, height = 56.0)
        FlowNodeType.EVENT   -> Dimensions(width = 160.0, height = 48.0)
        FlowNodeType.POLICY  -> Dimensions(width = 220.0, height = 64.0)
    }

    private fun calculateSystemGroups(nodes: List<FlowNode>): List<FlowSystemGroupView> =
        nodes.groupBy { it.system }.mapNotNull { (systemName, systemNodeList) ->
            if (systemName == null) return@mapNotNull null
            val minX = systemNodeList.minOf { it.position!!.x } - systemGroupPadding
            val minY = systemNodeList.minOf { it.position!!.y } - systemGroupPadding
            val maxX = systemNodeList.maxOf { it.position!!.x + it.dimensions!!.width } + systemGroupPadding
            val maxY = systemNodeList.maxOf { it.position!!.y + it.dimensions!!.height } + systemGroupPadding
            FlowSystemGroupView(
                systemName = systemName,
                bounds = FlowBounds(x = minX, y = minY, width = maxX - minX, height = maxY - minY)
            )
        }

    private fun calculateBounds(nodes: List<FlowNode>): FlowBounds {
        if (nodes.isEmpty()) return FlowBounds(0.0, 0.0, 0.0, 0.0)
        val maxX = nodes.maxOf { it.position!!.x + it.dimensions!!.width }
        val maxY = nodes.maxOf { it.position!!.y + it.dimensions!!.height }
        return FlowBounds(x = 0.0, y = 0.0, width = maxX + canvasPadding, height = maxY + canvasPadding)
    }
}

