package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.ZdlModel
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.classBlock
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.id
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.memberText

/**
 * One Mermaid `classDiagram` per service method. The method signature is the centre class; its
 * parameter and return type, when present, hang from it. The generated identifier includes the
 * service name, so methods with the same name in different services remain distinct.
 */
class ZdlCommandToMermaidClassDiagramTransformer {

    /** One entry per service method, in service and method declaration order. */
    fun transform(model: ZdlModel): List<CommandClassDiagram> = transform(ZdlDomain(model))

    fun transform(domain: ZdlDomain): List<CommandClassDiagram> =
        domain.services.flatMap { service -> service.methods.map { command -> transform(service, command) } }

    /** The diagram for one command, identified by its containing [service]. */
    fun transform(service: ZdlService, command: ZdlCommand): CommandClassDiagram {
        val commandId = "cmd_${id(service.name)}__${id(command.name)}"
        val input = command.parameter
        val output = command.returnType
        return CommandClassDiagram(service.name, command.name, render(commandId, command, input, output))
    }

    /** Null when [serviceName] or [commandName] is not declared. */
    fun transform(domain: ZdlDomain, serviceName: String, commandName: String): CommandClassDiagram? {
        val service = domain.services.firstOrNull { it.name == serviceName } ?: return null
        val command = service.methods.firstOrNull { it.name == commandName } ?: return null
        return transform(service, command)
    }

    private fun render(commandId: String, command: ZdlCommand, input: String?, output: String?): String = buildString {
        append(MermaidLight.FRONT_MATTER)
        append("classDiagram\n")
        append("    class ").append(commandId).append("[\"").append(commandLabel(command)).append("\"]\n")
        input?.let { append(classBlock(it, "input", emptyList())) }
        output?.let { append(classBlock(it, "output", emptyList())) }
        input?.let { append("    ").append(id(it)).append(" --> ").append(commandId).append('\n') }
        output?.let { append("    ").append(commandId).append(" --> ").append(id(it)).append('\n') }
        append(MermaidLight.styleLine(commandId, null))
        input?.let { append(MermaidLight.styleLine(id(it), "input")) }
        output?.let { append(MermaidLight.styleLine(id(it), "output")) }
    }

    /** Names are sanitised first; the array marker is added afterwards as Mermaid entity codes, so it survives. */
    private fun commandLabel(command: ZdlCommand): String {
        val parameter = command.parameter?.plus(if (command.parameterIsOptional) "?" else "")
        val signature = labelText("${command.name}(${parameter.orEmpty()})")
        val returnType = command.returnType?.let { type ->
            labelText(type) + (if (command.returnIsArray) ARRAY_MARKER else "") + (if (command.returnIsOptional) "?" else "")
        }
        return signature + (returnType?.let { " $it" } ?: "")
    }

    private fun labelText(text: String): String = memberText(text).replace("[", "").replace("]", "")

    private companion object {
        /** `[]` as Mermaid entity codes, which [memberText] would otherwise strip along with other markup characters. */
        const val ARRAY_MARKER = "#91;#93;"
    }
}

/** One command diagram, identified by service and method because method names are not globally unique. */
data class CommandClassDiagram(val service: String, val command: String, val mermaid: String)
