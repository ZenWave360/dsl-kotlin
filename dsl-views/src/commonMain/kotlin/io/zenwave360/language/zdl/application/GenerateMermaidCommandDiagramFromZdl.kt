package io.zenwave360.language.zdl.application

import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zdl.view.CommandClassDiagram
import io.zenwave360.language.zdl.view.ZdlCommandToMermaidClassDiagramTransformer
import io.zenwave360.language.zdl.view.ZdlDomain

/** Generates Mermaid class diagrams for the service methods declared in ZDL content. */
class GenerateMermaidCommandDiagramFromZdl(
    private val parser: ZdlParser = ZdlParser(),
    private val transformer: ZdlCommandToMermaidClassDiagramTransformer = ZdlCommandToMermaidClassDiagramTransformer(),
) {
    fun execute(zdlContent: String): List<CommandClassDiagram> = transformer.transform(parser.parseModel(zdlContent))

    /** Null when [serviceName] or [commandName] is not declared in [zdlContent]. */
    fun execute(zdlContent: String, serviceName: String, commandName: String): String? =
        transformer.transform(ZdlDomain(parser.parseModel(zdlContent)), serviceName, commandName)?.mermaid
}
