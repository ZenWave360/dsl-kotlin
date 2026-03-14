package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.ElkFlowLayoutEngine
import io.zenwave360.language.eventflow.view.FlowViewModel
import io.zenwave360.language.eventflow.view.ZflToFlowViewModelTransformer
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer

class GenerateFlowViewFromZfl(
    private val parser: ZflParser = ZflParser(),
    private val semanticAnalyzer: ZflSemanticAnalyzer = ZflSemanticAnalyzer(),
    private val transformer: ZflToFlowViewModelTransformer = ZflToFlowViewModelTransformer(),
    private val layoutEngine: ElkFlowLayoutEngine = ElkFlowLayoutEngine()
) {
    fun execute(zflContent: String): FlowViewModel {
        val model = parser.parseModel(zflContent)
        val semantic = semanticAnalyzer.analyze(model)
        val viewModel = transformer.transform(semantic) // FlowViewModel without layout
        val positioned = layoutEngine.layout(viewModel)  // FlowViewModel with layout
        return positioned
    }
}
