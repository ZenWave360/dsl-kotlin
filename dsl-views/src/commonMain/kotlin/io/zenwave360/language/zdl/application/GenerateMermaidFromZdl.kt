package io.zenwave360.language.zdl.application

import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer

/**
 * Generates a Mermaid `classDiagram` document for ZDL content: aggregates and their commands,
 * entities with their fields, enums with their values, inputs, outputs and events, services
 * with their commands, and relationships with their cardinality.
 *
 * The ZDL counterpart of [io.zenwave360.language.eventflow.application.GenerateMermaidFromZfl].
 * The returned document is inert: it contains no click, link or callback directives, no init
 * directives and no remote references.
 */
class GenerateMermaidFromZdl(
    private val parser: ZdlParser = ZdlParser(),
    private val transformer: ZdlToMermaidClassDiagramTransformer = ZdlToMermaidClassDiagramTransformer(),
) {
    fun execute(zdlContent: String): String = transformer.transform(parser.parseModel(zdlContent))
}
