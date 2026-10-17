package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.ZdlModel
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.id
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.memberText

/**
 * One service as an EventStorming-style Mermaid flowchart: the service and its aggregates are the box (pink), its
 * commands (blue) sit on the left and the events they emit (orange) on the right, with a
 * command to event arrow for each event a command can emit.
 *
 * Only commands are drawn; queries (see [ZdlCommand.query]) are listed in [ServiceEventStorming.queries]
 * so the page can mention them under the diagram.
 */
class ZdlServiceToEventStormingTransformer {

    /** One entry per service of the model, in declaration order. */
    fun transform(model: ZdlModel): List<ServiceEventStorming> = transform(ZdlDomain(model))

    fun transform(domain: ZdlDomain): List<ServiceEventStorming> = domain.services.map { transform(it) }

    fun transform(service: ZdlService): ServiceEventStorming {
        val commands = service.commands
        val queries = service.queries.map { it.name }
        if (commands.isEmpty()) return ServiceEventStorming(service.name, null, queries)

        val label = memberText(boxLabel(service))
        val boxId = "agg_" + id(service.name)
        val events = commands.flatMap { it.eventNames }.distinct()
        val mermaid = buildString {
            append("flowchart LR\n")
            append("  classDef command ").append(COMMAND_STYLE).append('\n')
            append("  classDef event ").append(EVENT_STYLE).append('\n')
            append("  subgraph ").append(boxId).append("[\"").append(label).append("\"]\n")
            append("    direction LR\n")
            for (c in commands) append("    ").append(commandId(c)).append("[\"").append(memberText(c.name)).append("\"]\n")
            for (e in events) append("    ").append(eventId(e)).append("[\"").append(memberText(e)).append("\"]\n")
            append("  end\n")
            append("  style ").append(boxId).append(' ').append(AGGREGATE_STYLE).append('\n')
            for (c in commands) append("  class ").append(commandId(c)).append(" command\n")
            for (e in events) append("  class ").append(eventId(e)).append(" event\n")
            val edges = LinkedHashSet<String>()
            for (c in commands) for (e in c.eventNames) edges += "  ${commandId(c)} --> ${eventId(e)}\n"
            edges.forEach { append(it) }
        }
        return ServiceEventStorming(service.name, mermaid, queries)
    }

    /** As declared in the ZDL: `service OrdersCheckoutService for Order, OtherAggregate`. */
    private fun boxLabel(service: ZdlService) =
        "service ${service.name}" + service.aggregates.joinToString(", ", " for ").takeIf { service.aggregates.isNotEmpty() }.orEmpty()

    private fun commandId(c: ZdlCommand) = "cmd_" + id(c.name)

    private fun eventId(name: String) = "evt_" + id(name)

    companion object {
        const val COMMAND_STYLE = "fill:#a7c7f2,stroke:#3b6fb6,color:#111"
        const val EVENT_STYLE = "fill:#ffb76b,stroke:#d9822b,color:#111"
        const val AGGREGATE_STYLE = "fill:#fbd3ee,stroke:#c2569b,color:#111"

        /** Explains the colors; the page prints it under the diagram. */
        const val LEGEND = "Pink box = aggregate. Blue = command. Orange = event. Arrow = the event a command can emit."
    }
}

/**
 * The diagram of one service. [mermaid] is null when the service has no commands (only queries).
 * [queries] are the names of the methods left out of the diagram.
 */
data class ServiceEventStorming(val service: String, val mermaid: String?, val queries: List<String>) {
    /** "Queries: `getOrder`, `listOrders`." or null when there are none. */
    val queriesLine: String?
        get() = queries.takeIf { it.isNotEmpty() }?.joinToString(", ", "Queries: ", ".") { "`$it`" }
}
