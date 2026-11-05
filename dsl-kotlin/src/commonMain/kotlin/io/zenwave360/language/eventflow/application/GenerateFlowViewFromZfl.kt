package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.ElkFlowLayoutEngine
import io.zenwave360.language.eventflow.view.FlowBounds
import io.zenwave360.language.eventflow.view.FlowLaneLayout
import io.zenwave360.language.eventflow.view.FlowLanes
import io.zenwave360.language.eventflow.view.FlowSystemGroupView
import io.zenwave360.language.eventflow.view.FlowViewModel
import io.zenwave360.language.eventflow.view.Point
import io.zenwave360.language.eventflow.view.FlowVariant
import io.zenwave360.language.eventflow.view.ZflToFlowViewModelTransformer
import io.zenwave360.language.eventflow.view.zflFlowVariants
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer

class GenerateFlowViewFromZfl(
    private val parser: ZflParser = ZflParser(),
    private val semanticAnalyzer: ZflSemanticAnalyzer = ZflSemanticAnalyzer(),
    private val transformer: ZflToFlowViewModelTransformer = ZflToFlowViewModelTransformer(),
    private val layoutEngine: ElkFlowLayoutEngine = ElkFlowLayoutEngine()
) {
    suspend fun execute(zflContent: String, lanes: FlowLanes = FlowLanes.NONE): FlowViewModel {
        val model = parser.parseModel(zflContent)
        val semantic = semanticAnalyzer.analyze(model)
        val viewModel = transformer.transform(semantic) // FlowViewModel without layout
        val positioned = layoutEngine.layout(viewModel)  // FlowViewModel with layout
        return FlowLaneLayout.apply(positioned, lanes)
    }

    fun variants(zflContent: String): List<FlowVariant> =
        variants(semanticAnalyzer.analyze(parser.parseModel(zflContent))).map { it.variant }

    suspend fun variant(zflContent: String, id: String, lanes: FlowLanes = FlowLanes.NONE): FlowVariantViewResult {
        val semantic = semanticAnalyzer.analyze(parser.parseModel(zflContent))
        val variant = variants(semantic).firstOrNull { it.variant.id == id }
            ?: return FlowVariantViewResult.UnknownVariant(id)
        return FlowVariantViewResult.Found(
            FlowLaneLayout.apply(layoutEngine.layout(transformer.transform(semantic, variant.path)), lanes)
        )
    }

    /**
     * Applies saved positions to the full flow view (spec:flows/eventflow#ef-r14). All or nothing: the positions
     * are applied only when every node of the view has a finite position; ids the view lacks are ignored.
     * Otherwise the automatic layout is returned with the reason and the offending node ids.
     * With lanes (spec:flows/eventflow#ef-r24) positions are not used: the automatic laned layout is returned as applied.
     */
    suspend fun withPositions(zflContent: String, positions: Map<String, Point>, lanes: FlowLanes = FlowLanes.NONE): FlowPositionsResult {
        if (lanes != FlowLanes.NONE) return FlowPositionsResult.Applied(execute(zflContent, lanes))
        val automatic = execute(zflContent)
        val missing = automatic.nodes.map { it.id }.filter { it !in positions }
        if (missing.isNotEmpty()) return FlowPositionsResult.NotApplied(automatic, FlowPositionsReason.MISSING_NODES, missing)
        val invalid = automatic.nodes.map { it.id }.filter { id -> positions.getValue(id).let { !it.x.isFinite() || !it.y.isFinite() } }
        if (invalid.isNotEmpty()) return FlowPositionsResult.NotApplied(automatic, FlowPositionsReason.INVALID, invalid)
        val nodes = automatic.nodes.map { it.copy(position = positions.getValue(it.id)) }
        return FlowPositionsResult.Applied(
            automatic.copy(nodes = nodes, bounds = boundsOf(nodes), systemGroups = systemGroupsOf(nodes))
        )
    }

    // Same formulas as the ELK layout engines, so applied positions yield the metadata automatic layout would.
    private fun boundsOf(nodes: List<io.zenwave360.language.eventflow.view.FlowNode>): FlowBounds {
        if (nodes.isEmpty()) return FlowBounds(0.0, 0.0, 0.0, 0.0)
        val maxX = nodes.maxOf { it.position!!.x + it.dimensions!!.width }
        val maxY = nodes.maxOf { it.position!!.y + it.dimensions!!.height }
        return FlowBounds(x = 0.0, y = 0.0, width = maxX + CANVAS_PADDING, height = maxY + CANVAS_PADDING)
    }

    private fun systemGroupsOf(nodes: List<io.zenwave360.language.eventflow.view.FlowNode>): List<FlowSystemGroupView> =
        nodes.groupBy { it.system }.mapNotNull { (systemName, group) ->
            if (systemName == null) return@mapNotNull null
            val minX = group.minOf { it.position!!.x } - SYSTEM_GROUP_PADDING
            val minY = group.minOf { it.position!!.y } - SYSTEM_GROUP_PADDING
            val maxX = group.maxOf { it.position!!.x + it.dimensions!!.width } + SYSTEM_GROUP_PADDING
            val maxY = group.maxOf { it.position!!.y + it.dimensions!!.height } + SYSTEM_GROUP_PADDING
            FlowSystemGroupView(systemName, FlowBounds(minX, minY, maxX - minX, maxY - minY))
        }

    private companion object {
        const val CANVAS_PADDING = 20.0
        const val SYSTEM_GROUP_PADDING = 40.0
    }

    private fun variants(semantic: io.zenwave360.language.zfl.semantic.ZflSemanticModel) =
        semantic.flows.firstOrNull()?.let(::zflFlowVariants).orEmpty()
}

sealed interface FlowVariantViewResult {
    data class Found(val view: FlowViewModel) : FlowVariantViewResult
    data class UnknownVariant(val id: String) : FlowVariantViewResult
}

enum class FlowPositionsReason { MISSING_NODES, INVALID }

sealed interface FlowPositionsResult {
    data class Applied(val view: FlowViewModel) : FlowPositionsResult
    /** [view] has the automatic layout; [missing] lists the node ids without a position (MISSING_NODES) or with a non-finite one (INVALID). */
    data class NotApplied(val view: FlowViewModel, val reason: FlowPositionsReason, val missing: List<String>) : FlowPositionsResult
}
