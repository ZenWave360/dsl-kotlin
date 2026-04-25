package io.zenwave360.language.eventflow.view

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.elk.alg.layered.LayeredLayoutProvider
import org.eclipse.elk.alg.layered.options.LayeredMetaDataProvider
import org.eclipse.elk.alg.layered.options.LayeredOptions
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.core.options.CoreOptions
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.graph.util.ElkGraphUtil
import org.eclipse.elk.core.options.Direction as ElkDirection

actual class ElkFlowLayoutEngine actual constructor() {

    private val rankSpacing = 80.0
    private val nodeSpacing = 120.0
    private val canvasPadding = 20.0

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

        val elkNodeMap = viewModel.nodes.associate { node ->
            val dims = semanticNodeSize(node.type)
            val elkNode = ElkGraphUtil.createNode(root)
            elkNode.identifier = node.id
            elkNode.width = dims.width
            elkNode.height = dims.height
            node.id to elkNode
        }

        viewModel.edges.forEach { edge ->
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

        viewModel.copy(
            nodes = positionedNodes,
            edges = viewModel.edges,
            layout = LayoutMetadata(
                engine = "elk-layered",
                direction = Direction.LR,
                rankSpacing = rankSpacing,
                nodeSpacing = nodeSpacing
            ),
            bounds = calculateBounds(positionedNodes)
        )
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
}

