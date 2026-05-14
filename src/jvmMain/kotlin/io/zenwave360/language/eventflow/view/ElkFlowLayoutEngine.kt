package io.zenwave360.language.eventflow.view

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.elk.alg.layered.LayeredLayoutProvider
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider
import org.eclipse.elk.alg.layered.options.LayeredOptions
import org.eclipse.elk.alg.layered.options.OrderingStrategy
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.core.options.CoreOptions
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.graph.util.ElkGraphUtil
import org.eclipse.elk.core.options.Direction as ElkDirection

actual class ElkFlowLayoutEngine actual constructor() {

    private val rankSpacing = 80.0
    private val nodeSpacing = 120.0
    private val canvasPadding = 20.0
    private val systemGroupPadding = 40.0

    companion object {
        @Volatile private var initialized = false

        private fun ensureInitialized() {
            if (!initialized) {
                synchronized(ElkFlowLayoutEngine::class.java) {
                    if (!initialized) {
                        LayoutMetaDataService.getInstance()
                            .registerLayoutMetaDataProviders(LayeredMetaDataProvider())
                        initialized = true
                    }
                }
            }
        }
    }

    actual suspend fun layout(viewModel: FlowViewModel): FlowViewModel = withContext(Dispatchers.IO) {
        ensureInitialized()
        if (viewModel.nodes.isEmpty()) {
            return@withContext viewModel.copy(
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

        val root = ElkGraphUtil.createGraph()
        root.setProperty(CoreOptions.DIRECTION, ElkDirection.RIGHT)
        root.setProperty(CoreOptions.SPACING_NODE_NODE, nodeSpacing)
        root.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, rankSpacing)
        root.setProperty(LayeredOptions.PARTITIONING_ACTIVATE, true)
        root.setProperty(LayeredOptions.CONSIDER_MODEL_ORDER_STRATEGY, OrderingStrategy.NODES_AND_EDGES)
        root.setProperty(LayeredOptions.CROSSING_MINIMIZATION_FORCE_NODE_MODEL_ORDER, true)

        val partitions = ElkLayoutConstraints.partitions(viewModel)
        val modelOrder = ElkLayoutConstraints.nodeModelOrder(viewModel)
        val orderedNodes = viewModel.nodes.sortedBy { modelOrder[it.id] ?: Int.MAX_VALUE }

        val elkNodeMap = orderedNodes.mapIndexed { index, node ->
            val dims = semanticNodeSize(node.type)
            val elkNode = ElkGraphUtil.createNode(root)
            elkNode.identifier = node.id
            elkNode.width = dims.width
            elkNode.height = dims.height
            elkNode.setProperty(LayeredOptions.PARTITIONING_PARTITION, partitions[node.id] ?: 0)
            elkNode.setProperty(CoreOptions.PRIORITY, index)
            node.id to elkNode
        }.toMap()

        viewModel.edges
            .sortedWith(
                compareBy<FlowEdge>(
                    { modelOrder[it.source] ?: Int.MAX_VALUE },
                    { modelOrder[it.target] ?: Int.MAX_VALUE },
                    { edgeTypePriority(it.type) }
                )
            )
            .forEach { edge ->
                if (edge.source == edge.target) {
                    return@forEach
                }
                val source = elkNodeMap[edge.source] ?: return@forEach
                val target = elkNodeMap[edge.target] ?: return@forEach
                val elkEdge = ElkGraphUtil.createEdge(root)
                elkEdge.identifier = edge.id
                elkEdge.sources.add(source)
                elkEdge.targets.add(target)
            }

        LayeredLayoutProvider().also { it.initialize("") }.layout(root, BasicProgressMonitor())

        val positionedNodes = viewModel.nodes.map { node ->
            val elkNode = elkNodeMap.getValue(node.id)
            node.copy(
                position = Point(canvasPadding + elkNode.x, canvasPadding + elkNode.y),
                dimensions = Dimensions(elkNode.width, elkNode.height)
            )
        }
        val adjustedNodes = StartNodePostLayout.apply(
            nodes = positionedNodes,
            edges = viewModel.edges,
            canvasPadding = canvasPadding,
            desiredGap = rankSpacing
        )

        viewModel.copy(
            nodes = adjustedNodes,
            edges = viewModel.edges,
            systemGroups = calculateSystemGroups(adjustedNodes),
            layout = LayoutMetadata(
                engine = "elk-layered",
                direction = Direction.LR,
                rankSpacing = rankSpacing,
                nodeSpacing = nodeSpacing
            ),
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
        FlowNodeType.START   -> Dimensions(width = 180.0, height = 124.0)
        FlowNodeType.COMMAND -> Dimensions(width = 180.0, height = 124.0)
        FlowNodeType.EVENT   -> Dimensions(width = 180.0, height = 124.0)
        FlowNodeType.POLICY  -> Dimensions(width = 180.0, height = 124.0)
    }

    private fun calculateBounds(nodes: List<FlowNode>): FlowBounds {
        if (nodes.isEmpty()) return FlowBounds(0.0, 0.0, 0.0, 0.0)
        val minX = nodes.minOf { it.position!!.x }
        val minY = nodes.minOf { it.position!!.y }
        val maxX = nodes.maxOf { it.position!!.x + it.dimensions!!.width }
        val maxY = nodes.maxOf { it.position!!.y + it.dimensions!!.height }
        return FlowBounds(x = 0.0, y = 0.0, width = maxX + canvasPadding, height = maxY + canvasPadding)
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
}

