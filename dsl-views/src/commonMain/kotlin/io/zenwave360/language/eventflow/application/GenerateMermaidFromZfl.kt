package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.MermaidDiagramsView
import io.zenwave360.language.eventflow.view.MermaidSequenceRenderMode
import io.zenwave360.language.eventflow.view.ZflToMermaidDiagramsTransformer
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer

class GenerateMermaidFromZfl(
    private val parser: ZflParser = ZflParser(),
    private val semanticAnalyzer: ZflSemanticAnalyzer = ZflSemanticAnalyzer(),
    private val transformer: ZflToMermaidDiagramsTransformer = ZflToMermaidDiagramsTransformer()
) {
    fun execute(zflContent: String): MermaidDiagramsView {
        return execute(zflContent, MermaidSequenceRenderMode.ALT_BLOCKS)
    }

    fun execute(
        zflContent: String,
        sequenceRenderMode: MermaidSequenceRenderMode
    ): MermaidDiagramsView {
        val model = parser.parseModel(zflContent)
        val semantic = semanticAnalyzer.analyze(model)
        return transformer.transform(semantic, sequenceRenderMode)
    }

    /** As [variants], with the variant tree line the id names; the document is parsed once. */
    fun variantsWithTree(zflContent: String, id: String): Pair<io.zenwave360.language.eventflow.view.MermaidSequenceDiagram, io.zenwave360.language.eventflow.view.FlowVariantNode>? =
        transformer.transformVariantsWithNode(semanticAnalyzer.analyze(parser.parseModel(zflContent)), id)

    /** The variants under a variant id or a variant tree line id, as one sequence diagram; null when the id names nothing. */
    fun variants(zflContent: String, id: String): io.zenwave360.language.eventflow.view.MermaidSequenceDiagram? =
        transformer.transformVariants(semanticAnalyzer.analyze(parser.parseModel(zflContent)), id)
}
