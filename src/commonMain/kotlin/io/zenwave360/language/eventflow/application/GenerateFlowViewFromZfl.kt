package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.ir.FlowIR
import io.zenwave360.language.eventflow.ir.ZflToFlowIrTransformer
import io.zenwave360.language.eventflow.view.FlowLayoutEngine
import io.zenwave360.language.eventflow.view.FlowViewModel
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer

class GenerateFlowViewFromZfl(
    private val parser: ZflParser = ZflParser(),
    private val semanticAnalyzer: ZflSemanticAnalyzer = ZflSemanticAnalyzer(),
    private val transformer: ZflToFlowIrTransformer = ZflToFlowIrTransformer(),
    private val layoutEngine: FlowLayoutEngine = FlowLayoutEngine()
) {
    fun execute(zflContent: String): FlowViewModel {
        val model = parser.parseModel(zflContent)
        val semantic = semanticAnalyzer.analyze(model)
        val ir = transformer.transform(semantic)
        val viewModel = layoutEngine.layout(ir)
        return viewModel
    }
}
