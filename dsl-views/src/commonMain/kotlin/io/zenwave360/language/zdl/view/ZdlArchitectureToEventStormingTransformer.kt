package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.architecture.ArchCommand
import io.zenwave360.language.zdl.architecture.ArchModule
import io.zenwave360.language.zdl.architecture.ZdlArchitecture
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.memberText

/**
 * The whole architecture as one EventStorming-style Mermaid flowchart, without arrows: a dashed box per module
 * (not the referenced ones) holding each of its services (pink) with its commands (blue) on the left and the
 * events it emits (orange) on the right. Module events that no command emits sit next to the first service.
 * Queries are left out.
 *
 * Layout uses only invisible links (`~~~`): commands, service and events take consecutive ranks, so each
 * group stacks as a column; a column of more than [maxColumn] nodes is split in two. Modules are chained in
 * rows of [perRow] boxes.
 */
class ZdlArchitectureToEventStormingTransformer(
    private val maxColumn: Int = 4,
    private val perRow: Int = 3,
) {

    /** Null when no module has a command or an event. */
    fun transform(architecture: ZdlArchitecture): String? {
        val boxes = architecture.modules.filter { !it.referenced }.mapNotNull { box(architecture, it) }
        if (boxes.isEmpty()) return null
        return buildString {
            append("flowchart LR\n")
            append("  classDef command ").append(ZdlServiceToEventStormingTransformer.COMMAND_STYLE).append('\n')
            append("  classDef event ").append(ZdlServiceToEventStormingTransformer.EVENT_STYLE).append('\n')
            append("  classDef service ").append(ZdlServiceToEventStormingTransformer.AGGREGATE_STYLE).append('\n')
            for (b in boxes) {
                append("  subgraph ").append(b.id).append("[\"").append(memberText(b.module.name)).append("\"]\n")
                append("    direction LR\n")
                b.nodes.forEach { append("    ").append(it).append('\n') }
                append("  end\n")
            }
            boxes.forEach { b -> b.links.forEach { append("  ").append(it).append('\n') } }
            for (row in boxes.chunked(perRow)) {
                for ((left, right) in row.zipWithNext()) append("  ${left.last} ~~~ ${right.first}\n")
            }
            classLine(this, boxes.flatMap { it.commands }, "command")
            classLine(this, boxes.flatMap { it.services }, "service")
            classLine(this, boxes.flatMap { it.events }, "event")
            boxes.forEach { append("  style ").append(it.id).append(' ').append(BOX_STYLE).append('\n') }
        }
    }

    private class Box(
        val module: ArchModule, val id: String, val nodes: List<String>, val links: List<String>,
        val commands: List<String>, val services: List<String>, val events: List<String>,
        val first: String, val last: String,
    )

    private fun box(a: ZdlArchitecture, module: ArchModule): Box? {
        val commands = a.commandsOf(module.id).filter { !it.query }
        val services = commands.map { it.service }.distinct()
        val moduleEvents = a.events.filter { it.module == module.id }.map { it.name }
        if (commands.isEmpty() && moduleEvents.isEmpty()) return null

        val prefix = id(module.id)
        val nodes = mutableListOf<String>()
        val links = mutableListOf<String>()
        val commandIds = mutableListOf<String>()
        val serviceIds = mutableListOf<String>()
        val eventIds = mutableListOf<String>()
        val placed = mutableSetOf<String>()
        var first: String? = null
        var last: String? = null

        // Events of the module emitted by no command go with the first service (or alone when there is none).
        val emitted = commands.flatMap { it.events }.toSet()
        val loose = moduleEvents.filter { it !in emitted }

        fun column(names: List<String>, idOf: (String) -> String, label: (String) -> String, into: MutableList<String>): List<List<String>> {
            val ids = names.map { name -> idOf(name).also { nodes += "$it[\"${memberText(label(name))}\"]"; into += it } }
            return if (ids.size > maxColumn) ids.chunked((ids.size + 1) / 2) else listOf(ids)
        }

        val groups: List<Pair<String?, List<ArchCommand>>> =
            if (services.isEmpty()) listOf(null to emptyList()) else services.map { s -> s to commands.filter { it.service == s } }
        for ((index, group) in groups.withIndex()) {
            val (service, own) = group
            val events = (own.flatMap { it.events } + if (index == 0) loose else emptyList())
                .filter { placed.add(it) }
            val serviceId = service?.let { "${prefix}__s_${id(it)}" }
            // Commands: the outer column (index 1) is left of the inner one (index 0), which touches the service.
            val cmdColumns = column(own.map { it.method }, { "${prefix}__c_${id(service.orEmpty())}_${id(it)}" }, { it }, commandIds)
            val evtColumns = column(events, { "${prefix}__e_${id(it)}" }, { it }, eventIds)
            if (serviceId != null) {
                nodes += "$serviceId([\"${memberText(service)}\"])"
                serviceIds += serviceId
                cmdColumns[0].forEach { links += "$it ~~~ $serviceId" }
                evtColumns[0].forEach { links += "$serviceId ~~~ $it" }
            }
            if (cmdColumns.size > 1) cmdColumns[1].zip(cmdColumns[0]).forEach { (outer, inner) -> links += "$outer ~~~ $inner" }
            if (evtColumns.size > 1) evtColumns[0].zip(evtColumns[1]).forEach { (inner, outer) -> links += "$inner ~~~ $outer" }
            val leftmost = (cmdColumns.last().firstOrNull() ?: serviceId ?: evtColumns[0].firstOrNull())
            val rightmost = (evtColumns.last().firstOrNull() ?: serviceId ?: cmdColumns[0].firstOrNull())
            if (first == null) first = leftmost
            if (rightmost != null) last = rightmost
        }
        return Box(module, "sg_${prefix}", nodes, links, commandIds, serviceIds, eventIds, first!!, last!!)
    }

    private fun classLine(out: StringBuilder, ids: List<String>, cls: String) {
        if (ids.isNotEmpty()) out.append("  class ").append(ids.joinToString(",")).append(' ').append(cls).append('\n')
    }

    private fun id(s: String) = s.map { if ((it.isLetterOrDigit() && it.code < 128) || it == '_') it else '_' }.joinToString("")

    companion object {
        const val BOX_STYLE = "fill:#ffffff,stroke:#999999,stroke-dasharray:4 3,color:#333333"

        /** Explains the colors; the page prints it under the diagram. */
        const val LEGEND = "Dashed box = module. Pink = service. Blue = command. Orange = event emitted by the service."
    }
}
