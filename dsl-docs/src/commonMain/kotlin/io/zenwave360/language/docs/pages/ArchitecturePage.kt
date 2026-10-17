package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.zdl.architecture.ArchEvent
import io.zenwave360.language.zdl.architecture.ArchModule
import io.zenwave360.language.zdl.architecture.ConsumptionVia
import io.zenwave360.language.zdl.architecture.EventKind
import io.zenwave360.language.zdl.architecture.EventRef
import io.zenwave360.language.zdl.architecture.ZdlArchitecture
import io.zenwave360.language.zdl.view.ArchitectureDiagram
import io.zenwave360.language.zdl.view.ArchitectureDiagrams

/**
 * What the architecture page and the module pages need to know.
 *
 * With several ZDL files each module has its own page under `modules/`; with one, `domain.md` is the
 * module's page and nothing new is written for it.
 */
internal class ArchitectureContext(
    val architecture: ZdlArchitecture,
    val diagrams: ArchitectureDiagrams,
    val documents: List<ZdlDocument>,
    /** Relative path from the output folder to the folder the source paths are relative to, or null for no links to sources. */
    val sourcesHref: String?,
) {
    val modulePages: Boolean = documents.size > 1

    fun document(module: ArchModule): ZdlDocument? {
        val path = module.path
        return documents.firstOrNull { simplify(it.source.path) == path }
            ?: documents.firstOrNull { it.source.path.substringAfterLast('/') == path.substringAfterLast('/') }
    }

    /** Page of a module, relative to the output folder. Referenced modules have none. */
    fun pagePath(module: ArchModule): String? = when {
        module.referenced -> null
        modulePages -> "${ArchitecturePage.MODULES_DIR}/${module.slug}.md"
        else -> DomainPage.PATH
    }

    private fun simplify(path: String): String {
        var p = path.replace('\\', '/')
        while (p.startsWith("./")) p = p.removePrefix("./")
        return p.replace("/./", "/")
    }
}

private fun link(text: String, href: String) = "[$text](${href.replace(" ", "%20")})"

/** Link to a ZDL file from a page [depth] folders below the output folder, or just its name when the folder is not known. */
private fun sourceLink(context: ArchitectureContext, module: ArchModule, depth: Int): String {
    val name = module.path.substringAfterLast('/')
    val prefix = context.sourcesHref ?: return Markdown.code(name)
    return link(Markdown.code(name), collapseDots("../".repeat(depth) + prefix + module.path))
}

/** Resolves `x/../` pairs so `../../cfg/../../a.zdl` reads `../../../a.zdl`; leading `..` are kept. */
internal fun collapseDots(path: String): String {
    val out = ArrayList<String>()
    for (part in path.split('/')) {
        if (part == ".." && out.isNotEmpty() && out.last() != "..") out.removeAt(out.size - 1) else out.add(part)
    }
    return out.joinToString("/")
}

private fun comma(items: List<String>) = items.joinToString(", ")

/** `architecture.md`: how the modules of the system relate, its events and who consumes them, and direct calls. */
internal object ArchitecturePage {

    const val PATH = "architecture.md"
    const val TITLE = "Architecture"
    const val MODULES_DIR = "modules"

    fun summary(context: ArchitectureContext): String {
        val a = context.architecture
        val modules = a.modules.count { !it.referenced }
        val events = a.events.count { e -> !a.module(e.module)!!.referenced }
        return "${count(modules, "module")}, ${count(events, "event")}"
    }

    fun render(context: ArchitectureContext, problems: MutableList<String>): String = buildString {
        val a = context.architecture
        append(Markdown.header(context.documents.map { it.source }))
        append("# ").append(TITLE).append("\n\n")
        append(intro(context)).append("\n\n")

        // With module pages, the per-module diagrams of a scaled architecture go there.
        val onThisPage = if (context.modulePages) context.diagrams.diagrams.filter { it.moduleId == null } else context.diagrams.diagrams
        val scaled = context.diagrams.scaled
        append(if (scaled) "## Overview\n\n" else "## Diagram\n\n")
        if (scaled) {
            append("The system has too many modules or events for one diagram, so this overview shows modules only. ")
            append(if (context.modulePages) "Each module page has the diagram of that module." else "The diagrams below show each module in detail.")
            append("\n\n")
        } else {
            append("Modules, the events they own and who consumes them, and direct calls between modules.\n\n")
        }
        for ((i, diagram) in onThisPage.withIndex()) {
            if (i > 0) append("## ").append(Markdown.inline(diagram.title)).append("\n\n")
            append(diagramBlock(diagram, context.diagrams.legend))
        }

        append(eventsSection(context))
        append(modulesSection(context))
        append(callsSection(context))

        val known = context.documents.flatMap { it.problems }
        // Syntax problems are reported by the ZDL pages as well: show each one once.
        val own = a.problems.filter { problem ->
            val message = problem.substringAfter(": ", problem)
            known.none { it.endsWith(message) }
        }
        problems += own
        append(Markdown.problems(own))
    }

    private fun intro(context: ArchitectureContext): String {
        val a = context.architecture
        val modules = a.modules.count { !it.referenced }
        val events = a.events.filter { !a.module(it.module)!!.referenced }
        val external = events.count { it.kind == EventKind.EXTERNAL }
        return buildString {
            append(count(modules, "module")).append(" declaring ").append(count(events.size, "event"))
            append(" (").append(events.size - external).append(" internal, ").append(external).append(" external)")
            if (a.foreignEvents.isNotEmpty()) append(", plus ").append(count(a.foreignEvents.size, "event")).append(" from other systems")
            append(". Generated from the ZDL files: edit the models, not this page.")
        }
    }

    internal fun diagramBlock(diagram: ArchitectureDiagram, legend: String): String =
        Markdown.mermaid(diagram.mermaid) + "\n" + legend + "\n\n"

    // ---- events -----------------------------------------------------------------------------

    private fun eventsSection(context: ArchitectureContext): String = buildString {
        val a = context.architecture
        if (a.events.isEmpty() && a.foreignEvents.isEmpty()) return ""
        append("## Events\n\n")
        append("Every event with the module that owns it and the modules that consume it. ")
        append("An event is external when it has an `@asyncapi` topic and internal otherwise. ")
        append("Consumers come from `@listener` and `@asyncapi` on service methods.\n\n")

        val rows = mutableListOf<List<String?>>()
        for (event in a.events) {
            val owner = a.module(event.module) ?: continue
            val ref = EventRef(event.module, event.name)
            rows += listOf(
                event.name,
                kind(event.kind),
                moduleLink(context, owner, ""),
                event.topic?.let { Markdown.code(it) },
                if (owner.referenced) Markdown.code(owner.path.substringAfterLast('/')) else sourceLink(context, owner, 0),
                comma(a.consumersOf(ref).map { moduleLink(context, a.module(it)!!, "") }),
            )
        }
        for (event in a.foreignEvents) {
            val ref = EventRef(null, event.channel)
            rows += listOf(
                event.channel,
                "external",
                "unknown",
                null,
                "${Markdown.code(event.api)} (not in these files)",
                comma(a.consumersOf(ref).map { moduleLink(context, a.module(it)!!, "") }),
            )
        }
        append(Markdown.table(listOf("Event", "Kind", "Owner", "Topic", "Defined in", "Consumers"), rows)).append('\n')

        val orphans = a.eventsWithoutConsumers().filter { a.module(it.module)?.referenced == false }
        if (orphans.isNotEmpty()) {
            append("### Events with no consumers\n\n")
            append("No service method in these files listens to them. An external event may be an integration point for other systems.\n\n")
            for (event in orphans) {
                append("- ").append(event.name).append(" (").append(a.module(event.module)!!.name).append(", ").append(kind(event.kind)).append(")\n")
            }
            append('\n')
        }
    }

    private fun kind(kind: EventKind) = if (kind == EventKind.EXTERNAL) "external" else "internal"

    /** The module name, linked to its page when it has one. [prefix] is the path from the current page to the output folder. */
    private fun moduleLink(context: ArchitectureContext, module: ArchModule, prefix: String): String {
        val page = context.pagePath(module) ?: return module.name
        return link(Markdown.inline(module.name), prefix + page)
    }

    // ---- modules ----------------------------------------------------------------------------

    private fun modulesSection(context: ArchitectureContext): String = buildString {
        val a = context.architecture
        append("## Modules\n\n")
        append("One module per ZDL file")
        append(if (context.modulePages) ", each with its own page.\n\n" else ".\n\n")
        val rows = a.modules.map { module ->
            listOf(
                module.name,
                module.basePackage?.let { Markdown.code(it) },
                if (module.referenced) "${Markdown.code(module.path.substringAfterLast('/'))} (not in these files)" else sourceLink(context, module, 0),
                context.pagePath(module)?.let { link(it, it) },
            )
        }
        append(Markdown.table(listOf("Module", "Package", "ZDL", "Page"), rows)).append('\n')
    }

    // ---- calls ------------------------------------------------------------------------------

    private fun callsSection(context: ArchitectureContext): String = buildString {
        val a = context.architecture
        if (a.calls.isEmpty()) return ""
        append("## Direct calls\n\n")
        append("Calls from one module to a command of another, declared with `@calls`. ")
        append("The caller waits for the answer, unlike an event.\n\n")
        val rows = a.calls.map { call ->
            listOf(
                moduleLink(context, a.module(call.caller)!!, ""),
                moduleLink(context, a.module(call.callee)!!, ""),
                Markdown.code(call.command),
                Markdown.code(call.declaredOn),
            )
        }
        append(Markdown.table(listOf("Caller", "Callee", "Command", "Called from"), rows)).append('\n')
    }

    fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
}

/** `modules/<slug>.md`: the domain model of one module (as `domain.md` has it) plus its architecture: events it consumes and calls it makes. */
internal object ModulePage {

    fun render(context: ArchitectureContext, module: ArchModule, problems: MutableList<String>): String = buildString {
        val document = context.document(module)!!
        append(Markdown.header(listOf(document.source)))
        problems += document.problems.map { "${document.source.path}: $it" }

        val diagram = context.diagrams.diagrams.firstOrNull { it.moduleId == module.id }
        val before = buildString {
            append("Back to the [architecture](../${ArchitecturePage.PATH}).\n\n")
            if (diagram != null) {
                append("## Architecture\n\n")
                append("This module, the events it owns and consumes, and the modules it talks to.\n\n")
                append(ArchitecturePage.diagramBlock(diagram, context.diagrams.legend))
            }
        }
        val after = buildString {
            append(consumes(context, module))
            append(consumedBy(context, module))
            append(calls(context, module))
        }
        append(DomainPage.section(document, intros = true, heading = module.name, beforeDiagram = before, atEnd = after))
    }

    private fun page(context: ArchitectureContext, module: ArchModule): String? =
        context.pagePath(module)?.removePrefix("${ArchitecturePage.MODULES_DIR}/")

    private fun name(context: ArchitectureContext, module: ArchModule): String {
        val page = page(context, module) ?: return module.name
        return link(Markdown.inline(module.name), page)
    }

    private fun eventName(event: ArchEvent?, ref: EventRef) =
        if (event == null) "${ref.name} (other system)" else event.name

    private fun consumes(context: ArchitectureContext, module: ArchModule): String = buildString {
        val a = context.architecture
        val mine = a.consumptions.filter { it.consumer == module.id }
        if (mine.isEmpty()) return ""
        append("## Consumes\n\n")
        append("Events from other modules or systems that this module reacts to, and the method that handles each one.\n\n")
        val rows = mine.sortedWith(compareBy({ it.event.module ?: "~" }, { it.event.name }, { it.method })).map { c ->
            val owner = c.event.module?.let { a.module(it) }
            listOf(
                eventName(a.event(c.event), c.event),
                owner?.let { name(context, it) } ?: "unknown",
                Markdown.code(c.method),
                if (c.via == ConsumptionVia.LISTENER) "@listener" else "@asyncapi",
            )
        }
        append(Markdown.table(listOf("Event", "Owner", "Handled by", "Via"), rows)).append('\n')
    }

    private fun consumedBy(context: ArchitectureContext, module: ArchModule): String = buildString {
        val a = context.architecture
        val own = a.eventsOf(module.id)
        val rows = own.flatMap { event ->
            a.consumptionsOf(EventRef(module.id, event.name)).map { c ->
                listOf(event.name, name(context, a.module(c.consumer)!!), Markdown.code(c.method), if (c.via == ConsumptionVia.LISTENER) "@listener" else "@asyncapi")
            }
        }
        if (rows.isEmpty()) return ""
        append("## Consumed by\n\n")
        append("Who reacts to the events this module owns.\n\n")
        append(Markdown.table(listOf("Event", "Consumer", "Handled by", "Via"), rows)).append('\n')
    }

    private fun calls(context: ArchitectureContext, module: ArchModule): String = buildString {
        val a = context.architecture
        val out = a.calls.filter { it.caller == module.id }
        val into = a.calls.filter { it.callee == module.id }
        if (out.isNotEmpty()) {
            append("## Calls\n\n")
            append("Direct calls this module makes to commands of other modules.\n\n")
            val rows = out.map { listOf(name(context, a.module(it.callee)!!), Markdown.code(it.command), Markdown.code(it.declaredOn)) }
            append(Markdown.table(listOf("Callee", "Command", "Called from"), rows)).append('\n')
        }
        if (into.isNotEmpty()) {
            append("## Called by\n\n")
            append("Other modules that call commands of this module directly.\n\n")
            val rows = into.map { listOf(name(context, a.module(it.caller)!!), Markdown.code(it.command), Markdown.code(it.declaredOn)) }
            append(Markdown.table(listOf("Caller", "Command", "Called from"), rows)).append('\n')
        }
    }
}
