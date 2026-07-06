package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.ServiceViewLayoutEngine
import io.zenwave360.language.eventflow.view.ServiceViewModel
import io.zenwave360.language.eventflow.view.ZflToServiceViewModelTransformer
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer

class GenerateServiceViewFromZfl(
    private val parser: ZflParser = ZflParser(),
    private val semanticAnalyzer: ZflSemanticAnalyzer = ZflSemanticAnalyzer(),
    private val transformer: ZflToServiceViewModelTransformer = ZflToServiceViewModelTransformer(),
    private val layoutEngine: ServiceViewLayoutEngine = ServiceViewLayoutEngine()
) {
    suspend fun execute(zflContent: String): ServiceViewModel {
        val model = parser.parseModel(zflContent)
        val semantic = semanticAnalyzer.analyze(model)
        return layoutEngine.layout(transformer.transform(semantic))
    }
}
