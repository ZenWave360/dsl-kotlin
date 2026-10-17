package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.internal.Anchors
import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.zdl.architecture.ZdlArchitecture
import io.zenwave360.language.zdl.view.CommandAdapter
import io.zenwave360.language.zdl.view.ZdlAggregateToMermaidClassDiagramTransformer
import io.zenwave360.language.zdl.view.ZdlArchitectureToMermaidTransformer
import io.zenwave360.language.zdl.view.ZdlCommand
import io.zenwave360.language.zdl.view.ZdlDomain
import io.zenwave360.language.zdl.view.ZdlEventToMermaidClassDiagramTransformer
import io.zenwave360.language.zdl.view.ZdlField
import io.zenwave360.language.zdl.view.ZdlLifecycleToMermaidStateDiagramTransformer
import io.zenwave360.language.zdl.view.ZdlServiceToEventStormingTransformer
import io.zenwave360.language.zdl.view.ZdlTypeKind

/**
 * The sections of a module page that come from its ZDL: Architecture, Services, Aggregates,
 * Domain events, Commands and (last) Glossary. Shared by `modules/<slug>.md` and `domain.md`.
 *
 * Intra-page links go through [Anchors], keyed `type:<Name>` for glossary terms and nested event
 * types and `event:<Name>` for the event headings of the glossary.
 *
 * [moduleId] is this module's id (null when the architecture has none for the file). [resolvedAdapters]
 * gives the adapters of a command with the owner module of listener and AsyncAPI events resolved (null:
 * use the unresolved ones). [modulePage] is the page of a module, relative to this page.
 */
internal class ModuleContent(
    private val domain: ZdlDomain,
    private val anchors: Anchors,
    private val moduleId: String? = null,
    private val resolvedAdapters: (service: String, method: String) -> List<CommandAdapter>? = { _, _ -> null },
    private val modulePage: (moduleId: String) -> String? = { null },
) {

    /** `## Architecture`: the module flowchart, or nothing when the module has no commands or events. */
    fun architecture(architecture: ZdlArchitecture, moduleId: String): String {
        val mermaid = ZdlArchitectureToMermaidTransformer().moduleDiagram(architecture, moduleId)
        if (mermaid.lines().count { it.isNotBlank() } <= 1) return ""
        return buildString {
            append(anchors.heading(2, "Architecture"))
            append("This module's commands and events, and the commands and events of other modules that connect to them.\n\n")
            append(Markdown.mermaid(mermaid)).append('\n')
            append(ZdlArchitectureToMermaidTransformer.MODULE_LEGEND).append("\n\n")
        }
    }

    /** `## Services`: one EventStorming diagram per service. */
    fun services(): String {
        if (domain.services.isEmpty()) return ""
        val diagrams = ZdlServiceToEventStormingTransformer().transform(domain)
        return buildString {
            append(anchors.heading(2, "Services"))
            append("Each service with its commands (blue) and the events they emit (orange), inside the aggregate it works on.\n\n")
            for ((service, diagram) in domain.services.zip(diagrams)) {
                append(anchors.heading(3, Markdown.inline(service.name)))
                service.javadoc?.let { append(Markdown.javadoc(it)).append("\n\n") }
                diagram.mermaid?.let { append(Markdown.mermaid(it)).append('\n') }
                diagram.queriesLine?.let { append(it).append("\n\n") }
            }
        }
    }

    /** `## Aggregates`: class diagram per aggregate, its lifecycle when it has one, and the entities no aggregate reaches. */
    fun aggregates(): String {
        val classes = ZdlAggregateToMermaidClassDiagramTransformer().transform(domain)
        if (classes.aggregates.isEmpty() && classes.others == null) return ""
        val lifecycles = ZdlLifecycleToMermaidStateDiagramTransformer().transform(domain).associateBy { it.aggregate }
        return buildString {
            append(anchors.heading(2, "Aggregates"))
            append("Each aggregate root with the entities and enums it owns, and how they relate.\n\n")
            for (diagram in classes.aggregates) {
                append(anchors.heading(3, Markdown.inline(diagram.aggregate)))
                append(Markdown.mermaid(diagram.mermaid)).append('\n')
                lifecycles[diagram.aggregate]?.let { lifecycle ->
                    append(anchors.heading(4, "Lifecycle"))
                    append(Markdown.mermaid(lifecycle.mermaid)).append('\n')
                    append(lifecycle.caption).append("\n\n")
                }
            }
            classes.others?.let {
                append(anchors.heading(3, "Other entities"))
                append("Entities and enums that no aggregate reaches.\n\n")
                append(Markdown.mermaid(it)).append('\n')
            }
        }
    }

    /** `## Domain events`: a class diagram per service with the events its commands emit, then the events no command emits. */
    fun domainEvents(): String {
        val diagrams = ZdlEventToMermaidClassDiagramTransformer().transform(domain)
        if (diagrams.services.isEmpty() && diagrams.others == null) return ""
        return buildString {
            append(anchors.heading(2, "Domain events"))
            append("The events each service emits, with their fields. Fields copied with `@copy` are included; field descriptions are in the glossary.\n\n")
            for (d in diagrams.services) {
                append(anchors.heading(3, Markdown.inline(d.service)))
                append(Markdown.mermaid(d.mermaid)).append('\n')
            }
            diagrams.others?.let {
                append(anchors.heading(3, "Other events"))
                append("Events that no command of a service emits.\n\n")
                append(Markdown.mermaid(it)).append('\n')
            }
        }
    }

    /**
     * `## Commands`: the methods of the services; input, return type and events link to their headings, and
     * `Triggered by` lists the REST endpoints, listeners and AsyncAPI channels. One `### <Service>` table per
     * service when there are several.
     */
    fun commands(): String {
        val services = domain.services.filter { it.methods.isNotEmpty() }
        if (services.isEmpty()) return ""
        val headers = listOf("Command", "Input", "Returns", "Events", "Triggered by", "Description")
        return buildString {
            append(anchors.heading(2, "Commands"))
            append("The methods of the module's services, the events each one emits and what triggers it.\n\n")
            for (service in services) {
                if (services.size > 1) append(anchors.heading(3, Markdown.inline(service.name)))
                val rows = service.methods.map { m ->
                    listOf(
                        m.name, input(m), returns(m),
                        m.events.joinToString(", ") { group -> group.joinToString(" | ") { anchors.link("event:$it", it) } },
                        (resolvedAdapters(service.name, m.name) ?: m.adapters).joinToString("<br>") { adapter(it) },
                        Markdown.javadoc(m.javadoc),
                    )
                }
                append(Markdown.table(headers, rows)).append('\n')
            }
        }
    }

    private fun adapter(a: CommandAdapter): String = when (a) {
        is CommandAdapter.Rest -> Markdown.code("${a.method} ${a.path}")
        is CommandAdapter.Listener -> "listener " + listenerEvent(a)
        is CommandAdapter.AsyncApi -> "asyncapi ${Markdown.code(a.channel)}" + (a.api?.let { " ($it)" } ?: "")
    }

    private fun listenerEvent(a: CommandAdapter.Listener): String {
        val own = if (a.module != null) a.module == moduleId else a.zdl == null
        if (own) return anchors.link("event:${a.event}", a.event)
        val page = a.module?.let(modulePage) ?: return a.event
        return "[${a.event}]($page)"
    }

    private fun input(m: ZdlCommand): String =
        listOfNotNull(if (m.hasId) "id" else null, m.parameter?.let { typeLink(it, false) }).joinToString(", ")

    private fun returns(m: ZdlCommand): String? = m.returnType?.let { typeLink(it, m.returnIsArray) }

    private fun typeLink(type: String, array: Boolean): String = anchors.link("type:$type", type) + if (array) "[]" else ""

    /** `## Glossary`: aggregates with their entities, enums, inputs, outputs and domain events. No services. */
    fun glossary(): String {
        val roots = domain.aggregates.filter { domain.kindOf(it.root) == ZdlTypeKind.ENTITY }.map { it.root }.distinct()
        val reachable = roots.associateWith { domain.reachableFrom(it) }
        val placed = mutableSetOf<String>().apply { addAll(roots) }
        val entitiesOf = roots.associateWith { root -> reachable.getValue(root).entities.filter { placed.add(it) } }
        val loose = domain.typesOf(ZdlTypeKind.ENTITY).map { it.name }.filter { it !in placed }
        val enums = domain.typesOf(ZdlTypeKind.ENUM)
        val inputs = domain.typesOf(ZdlTypeKind.INPUT)
        val outputs = domain.typesOf(ZdlTypeKind.OUTPUT)
        val events = domain.events
        if (roots.isEmpty() && loose.isEmpty() && enums.isEmpty() && inputs.isEmpty() && outputs.isEmpty() && events.isEmpty()) return ""

        return buildString {
            append(anchors.heading(2, "Glossary"))
            append("Every named type of this module, by kind.\n\n")
            if (roots.isNotEmpty() || loose.isNotEmpty()) {
                append(anchors.heading(3, "Aggregates"))
                for (root in roots) {
                    append(term(root, "Aggregate root"))
                    for (entity in entitiesOf.getValue(root)) append(term(entity, "${embedded(entity)}entity of ${Markdown.code(root)}".replaceFirstChar { it.uppercase() }))
                }
                for (entity in loose) append(term(entity, "${embedded(entity)}entity".replaceFirstChar { it.uppercase() }))
            }
            if (enums.isNotEmpty()) {
                append(anchors.heading(3, "Enums"))
                for (e in enums) {
                    append(anchors.heading(4, Markdown.inline(e.name), "type:${e.name}"))
                    e.javadoc?.let { append(Markdown.javadoc(it)).append("\n\n") }
                    val rows = domain.enumValues(e.name).map { v -> listOf(if (v.value != null) "${v.name} (${v.value})" else v.name, Markdown.javadoc(v.javadoc)) }
                    append(Markdown.table(listOf("Value", "Description"), rows)).append('\n')
                }
            }
            if (inputs.isNotEmpty()) {
                append(anchors.heading(3, "Inputs"))
                for (t in inputs) append(term(t.name, null))
            }
            if (outputs.isNotEmpty()) {
                append(anchors.heading(3, "Outputs"))
                for (t in outputs) append(term(t.name, null))
            }
            if (events.isNotEmpty()) {
                append(anchors.heading(3, "Domain events"))
                for (event in events) {
                    append(anchors.heading(4, Markdown.inline(event.name), "event:${event.name}"))
                    event.javadoc?.let { append(Markdown.javadoc(it)).append("\n\n") }
                    val where = listOfNotNull(event.channel?.let { "Channel: ${Markdown.code(it)}." }, event.topic?.let { "Topic: ${Markdown.code(it)}." })
                    if (where.isNotEmpty()) append(where.joinToString(" ")).append("\n\n")
                    append(fieldTable(event.name)).append('\n')
                    for (nested in domain.nestedTypesOf(event.name)) append(term(nested, null))
                }
            }
        }
    }

    private fun embedded(type: String) = if (domain.types[type]?.embedded == true) "embedded " else ""

    /** `#### <Type>`, an optional kind line, its javadoc and its field table. */
    private fun term(type: String, kind: String?): String = buildString {
        append(anchors.heading(4, Markdown.inline(type), "type:$type"))
        kind?.let { append(it).append("\n\n") }
        domain.types[type]?.javadoc?.let { append(Markdown.javadoc(it)).append("\n\n") }
        append(fieldTable(type)).append('\n')
    }

    private fun fieldTable(type: String): String =
        Markdown.table(listOf("Field", "Type", "Description"), domain.fieldsOf(type).map { f -> listOf(f.name, fieldType(f), Markdown.javadoc(f.javadoc)) })

    private fun fieldType(f: ZdlField): String = typeLink(f.type, f.isArray)
}
