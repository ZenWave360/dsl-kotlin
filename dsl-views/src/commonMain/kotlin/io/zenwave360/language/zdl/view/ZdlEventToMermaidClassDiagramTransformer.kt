package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.ZdlModel
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.classBlock
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.id
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.memberText

/**
 * Domain events as Mermaid class diagrams: one per service, with the events its commands emit (in
 * order of first appearance), plus one for the module events no service command emits.
 *
 * Each event is a `<<event>>` class with its fields (`@copy` resolved, see [ZdlDomain.fieldsOf]); types
 * declared inline inside an event are `<<embedded>>` classes joined by a composition arrow (`*--`, with
 * `"*"` for arrays) labeled with the field name. Light styling as in [MermaidLight].
 *
 * Events emitted only by commands of an `aggregate` block (which belong to no service) are in the
 * "other events" diagram. A service whose commands emit no declared event has no diagram.
 */
class ZdlEventToMermaidClassDiagramTransformer {

    fun transform(model: ZdlModel): EventClassDiagrams = transform(ZdlDomain(model))

    fun transform(domain: ZdlDomain): EventClassDiagrams {
        val declared = domain.events.map { it.name }.toSet()
        val shown = mutableSetOf<String>()
        val services = mutableListOf<ServiceEventClassDiagram>()
        for (service in domain.services) {
            val events = service.methods.flatMap { it.eventNames }.filter { it in declared }.distinct()
            if (events.isEmpty()) continue
            shown += events
            services += ServiceEventClassDiagram(service.name, events, render(domain, events))
        }
        val otherEvents = domain.events.map { it.name }.filter { it !in shown }
        return EventClassDiagrams(services, if (otherEvents.isEmpty()) null else render(domain, otherEvents), otherEvents)
    }

    private fun render(domain: ZdlDomain, events: List<String>): String {
        val classes = StringBuilder()
        val styles = StringBuilder()
        val links = LinkedHashSet<String>()
        val drawn = mutableSetOf<String>()

        fun draw(name: String, stereotype: String, nested: Set<String>) {
            if (!drawn.add(name)) return
            val fields = domain.fieldsOf(name)
            classes.append(classBlock(name, stereotype, fields.map { f -> memberText(f.type + (if (f.isArray) "[]" else "") + " " + f.name) }))
            styles.append(MermaidLight.styleLine(id(name), stereotype))
            for (f in fields) {
                if (f.type !in nested || f.type == name) continue
                val cardinality = if (f.isArray) "\"*\" " else ""
                links += "${id(name)} *-- $cardinality${id(f.type)} : ${memberText(f.name)}"
            }
        }

        for (event in events) {
            val nested = domain.nestedTypesOf(event)
            draw(event, "event", nested.toSet())
            for (n in nested) draw(n, "embedded", nested.toSet())
        }
        return buildString {
            append(MermaidLight.FRONT_MATTER)
            append("classDiagram\n")
            append(classes)
            for (link in links) append("    ").append(link).append('\n')
            append(styles)
        }
    }
}

/** The events emitted by the commands of one service, [events] in order of first appearance. */
data class ServiceEventClassDiagram(val service: String, val events: List<String>, val mermaid: String)

/** [others] is the diagram of the events no service command emits ([otherEvents]), null when there are none. */
data class EventClassDiagrams(val services: List<ServiceEventClassDiagram>, val others: String?, val otherEvents: List<String>)
