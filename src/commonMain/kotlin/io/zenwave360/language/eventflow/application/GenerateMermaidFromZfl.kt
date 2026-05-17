package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.MermaidDiagramsView
import io.zenwave360.language.eventflow.view.ZflToMermaidDiagramsTransformer
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer

class GenerateMermaidFromZfl(
    private val parser: ZflParser = ZflParser(),
    private val semanticAnalyzer: ZflSemanticAnalyzer = ZflSemanticAnalyzer(),
    private val transformer: ZflToMermaidDiagramsTransformer = ZflToMermaidDiagramsTransformer()
) {
    fun execute(zflContent: String): MermaidDiagramsView {
        val model = parser.parseModel(zflContent)
        val semantic = semanticAnalyzer.analyze(model)
        return transformer.transform(semantic)
    }
}
