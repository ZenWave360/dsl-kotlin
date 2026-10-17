package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.architecture.ArchModule
import io.zenwave360.language.zdl.architecture.ZdlArchitecture
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.memberText

/**
 * The whole architecture as one EventStorming-style Mermaid flowchart: a dashed box per module (not the
 * referenced ones) holding each of its services (pink), with an arrow from each command (blue) to its service
 * and from the service to each event it emits (orange). Module events that no command emits hang from the
 * first service. Queries are left out. Layout is Mermaid's own: boxes stack top to bottom, each one left to right.
 */
class ZdlArchitectureToEventStormingTransformer {

    /** Null when no module has a command or an event. */
    fun transform(architecture: ZdlArchitecture): String? {
        val boxes = architecture.modules.filter { !it.referenced }.mapNotNull { box(architecture, it) }
        if (boxes.isEmpty()) return null
        val commands = mutableListOf<String>()
        val services = mutableListOf<String>()
        val events = mutableListOf<String>()
        return buildString {
            append("flowchart LR\n")
            append("  classDef command ").append(ZdlServiceToEventStormingTransformer.COMMAND_STYLE).append('\n')
            append("  classDef service ").append(ZdlServiceToEventStormingTransformer.AGGREGATE_STYLE).append('\n')
            append("  classDef event ").append(ZdlServiceToEventStormingTransformer.EVENT_STYLE).append('\n')
            for (b in boxes) {
                append("  subgraph ").append(b.id).append("[\"").append(memberText(b.module.name)).append("\"]\n")
                append("    direction LR\n")
                for (s in b.services) {
                    s.commands.forEach { (id, name) -> append("    $id[\"${memberText(name)}\"] --> ${s.id}\n"); commands += id }
                    if (s.name != null) { append("    ${s.id}([\"${memberText(s.name)}\"])\n"); services += s.id }
                    s.events.forEach { (id, name) ->
                        append("    ").append(if (s.name != null) "${s.id} --> " else "").append("$id[\"${memberText(name)}\"]\n")
                        events += id
                    }
                }
                append("  end\n")
            }
            if (commands.isNotEmpty()) append("  class ${commands.joinToString(",")} command\n")
            if (services.isNotEmpty()) append("  class ${services.joinToString(",")} service\n")
            if (events.isNotEmpty()) append("  class ${events.joinToString(",")} event\n")
            boxes.forEach { append("  style ").append(it.id).append(' ').append(BOX_STYLE).append('\n') }
        }
    }

    /** A service of a box; [name] null holds the events of a module without commands. Pairs are (node id, label). */
    private class Service(val id: String, val name: String?, val commands: List<Pair<String, String>>, val events: List<Pair<String, String>>)

    private class Box(val module: ArchModule, val id: String, val services: List<Service>)

    private fun box(a: ZdlArchitecture, module: ArchModule): Box? {
        val commands = a.commandsOf(module.id).filter { !it.query }
        val moduleEvents = a.events.filter { it.module == module.id }.map { it.name }
        if (commands.isEmpty() && moduleEvents.isEmpty()) return null
        val prefix = id(module.id)
        val names = commands.map { it.service }.distinct().ifEmpty { listOf(null) }
        val loose = moduleEvents - commands.flatMap { it.events }.toSet()
        val placed = mutableSetOf<String>()
        val duplicated = commands.groupBy { it.method }.filter { it.value.size > 1 }.keys
        val services = names.mapIndexed { index, service ->
            val own = commands.filter { it.service == service }
            val cmds = own.map { c ->
                val suffix = if (c.method in duplicated) "_" + id(c.service) else ""
                "${prefix}_${id(c.method)}$suffix" to c.method
            }
            val evts = (own.flatMap { it.events } + if (index == 0) loose else emptyList())
                .filter { placed.add(it) }.map { "${prefix}_${id(it)}" to it }
            Service("${prefix}_${id(service ?: "events")}", service, cmds, evts)
        }
        return Box(module, "sg_$prefix", services)
    }

    private fun id(s: String) = s.map { if ((it.isLetterOrDigit() && it.code < 128) || it == '_') it else '_' }.joinToString("")

    companion object {
        const val BOX_STYLE = "fill:#ffffff,stroke:#999999,stroke-dasharray:4 3,color:#333333"

        /** Explains the colors; the page prints it under the diagram. */
        const val LEGEND = "Dashed box = module. Blue = command, handled by the pink service, which emits the orange events."
    }
}
