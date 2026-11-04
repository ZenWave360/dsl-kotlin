package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.ElkFlowLayoutEngine
import io.zenwave360.language.eventflow.view.FlowViewModel
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
    suspend fun execute(zflContent: String): FlowViewModel {
        val model = parser.parseModel(zflContent)
        val semantic = semanticAnalyzer.analyze(model)
        val viewModel = transformer.transform(semantic) // FlowViewModel without layout
        val positioned = layoutEngine.layout(viewModel)  // FlowViewModel with layout
        return positioned
    }

    fun variants(zflContent: String): List<FlowVariant> =
        variants(semanticAnalyzer.analyze(parser.parseModel(zflContent))).map { it.variant }

    suspend fun variant(zflContent: String, id: String): FlowVariantViewResult {
        val semantic = semanticAnalyzer.analyze(parser.parseModel(zflContent))
        val variant = variants(semantic).firstOrNull { it.variant.id == id }
            ?: return FlowVariantViewResult.UnknownVariant(id)
        return FlowVariantViewResult.Found(layoutEngine.layout(transformer.transform(semantic, variant.path)))
    }

    private fun variants(semantic: io.zenwave360.language.zfl.semantic.ZflSemanticModel) =
        semantic.flows.firstOrNull()?.let(::zflFlowVariants).orEmpty()
}

sealed interface FlowVariantViewResult {
    data class Found(val view: FlowViewModel) : FlowVariantViewResult
    data class UnknownVariant(val id: String) : FlowVariantViewResult
}
