package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.architecture.ArchEvent
import io.zenwave360.language.zdl.architecture.ArchModule
import io.zenwave360.language.zdl.architecture.EventKind
import io.zenwave360.language.zdl.architecture.EventRef
import io.zenwave360.language.zdl.architecture.ZdlArchitecture

/** One Mermaid diagram. [moduleId] is set for the per-module diagrams of a scaled architecture. */
data class ArchitectureDiagram(val title: String, val moduleId: String?, val mermaid: String)

/** The diagrams of an architecture: one when not [scaled]; an overview first, then one per module, when scaled. */
data class ArchitectureDiagrams(val diagrams: List<ArchitectureDiagram>, val scaled: Boolean) {
    val legend: String get() = ZdlArchitectureToMermaidTransformer.LEGEND
}

/**
 * Renders a [ZdlArchitecture] as Mermaid `flowchart` diagrams: modules as hexagons inside a box,
 * internal events as rectangles inside the box of their module, external events as horizontal pipes
 * outside every box. Dashed arrows: consumed by. Solid: published by. Thick: direct call.
 *
 * The output is deterministic and inert (no `click` directives). All labels are quoted.
 */
class ZdlArchitectureToMermaidTransformer(
    /** Pipes as vertical cylinders and `flowchart TB`, for Mermaid versions without `h-cyl`. */
    private val legacyShapes: Boolean = false,
    /** More modules than this produces an overview plus one diagram per module. */
    private val maxModules: Int = 8,
    /** More events (including foreign ones) than this produces an overview plus one diagram per module. */
    private val maxEvents: Int = 15,
) {

    fun transform(architecture: ZdlArchitecture): ArchitectureDiagrams {
        val scaled = architecture.modules.size > maxModules ||
            architecture.events.size + architecture.foreignEvents.size > maxEvents
        val ids = Ids(architecture)
        if (!scaled) {
            val view = View(
                modules = architecture.modules.map { it.id }.toSet(),
                events = architecture.events.map { EventRef(it.module, it.name) }.toSet(),
                foreign = architecture.foreignEvents.map { it.channel }.toSet(),
            )
            val diagram = ArchitectureDiagram("Architecture", null, render(architecture, ids, view, null))
            return ArchitectureDiagrams(listOf(diagram), false)
        }
        val diagrams = mutableListOf(ArchitectureDiagram("Architecture overview", null, overview(architecture)))
        for (module in architecture.modules.filter { !it.referenced }) {
            val mermaid = render(architecture, ids, moduleView(architecture, module.id), module.id)
            diagrams += ArchitectureDiagram(module.name, module.id, mermaid)
        }
        return ArchitectureDiagrams(diagrams, true)
    }

    // ---- views ------------------------------------------------------------------------------

    /** What a diagram shows: modules, module-owned events and foreign events (by channel). */
    private class View(val modules: Set<String>, val events: Set<EventRef>, val foreign: Set<String>)

    /** The module, its events, the events it consumes with their owners, its consumers and call partners. */
    private fun moduleView(a: ZdlArchitecture, id: String): View {
        val modules = mutableSetOf(id)
        val events = mutableSetOf<EventRef>()
        val foreign = mutableSetOf<String>()
        a.eventsOf(id).forEach { events += EventRef(it.module, it.name) }
        for (c in a.consumptions) {
            if (c.event.module == id) modules += c.consumer
            if (c.consumer != id) continue
            val owner = c.event.module
            if (owner == null) {
                foreign += c.event.name
            } else {
                modules += owner
                events += c.event
            }
        }
        for (call in a.calls) {
            if (call.caller == id || call.callee == id) {
                modules += call.caller
                modules += call.callee
            }
        }
        return View(modules, events, foreign)
    }

    // ---- full / per-module diagram ----------------------------------------------------------

    private fun render(a: ZdlArchitecture, ids: Ids, view: View, focus: String?): String {
        val out = StringBuilder()
        out.append(header()).append('\n')
        val modules = a.modules.filter { it.id in view.modules }
        val events = a.events.filter { EventRef(it.module, it.name) in view.events }
        val foreign = a.foreignEvents.filter { it.channel in view.foreign }
        val referenced = modules.filter { it.referenced }

        out.append("  classDef module fill:#e8f0fe,stroke:#4a6fa5,stroke-width:2px,color:#111\n")
        out.append("  classDef event fill:#fff4d6,stroke:#c9a227,color:#111\n")
        if (foreign.isNotEmpty() || referenced.isNotEmpty()) {
            out.append("  classDef foreign fill:#f3f3f3,stroke:#888,stroke-dasharray:5 5,color:#111\n")
        }
        out.append('\n')

        for (m in modules) {
            out.append("  subgraph ${ids.box(m)}[\"${esc(m.name)}\"]\n")
            out.append("    ${m.id}{{\"${esc(m.name)}\"}}\n")
            for (e in events.filter { it.module == m.id && it.kind == EventKind.INTERNAL }) {
                out.append("    ${ids.event(e)}[\"${esc(e.name)}\"]\n")
            }
            out.append("  end\n\n")
        }
        val pipes = events.filter { it.kind == EventKind.EXTERNAL }
        for (e in pipes) out.append("  ${pipe(ids.event(e), e.name)}\n")
        for (f in foreign) out.append("  ${pipe(ids.foreign(f.channel), f.channel)}\n")
        if (pipes.isNotEmpty() || foreign.isNotEmpty()) out.append('\n')

        val referencedIds = referenced.map { it.id }.toSet()
        val ownEvents = events.filter { it.module !in referencedIds }
        classLine(out, "module", modules.filter { !it.referenced }.map { it.id })
        classLine(
            out, "foreign",
            referenced.map { it.id } + referenced.map { ids.box(it) } +
                events.filter { it.module in referencedIds }.map { ids.event(it) } +
                foreign.map { ids.foreign(it.channel) },
        )
        classLine(out, "event", ownEvents.map { ids.event(it) })
        out.append('\n')

        // published by (external events only), then consumed by, then direct calls
        val published = pipes.filter { it.module !in referencedIds }.map { "  ${it.module} --> ${ids.event(it)}" }
        val consumed = a.consumptions.mapNotNull { c ->
            if (c.consumer !in view.modules) return@mapNotNull null
            if (focus != null && c.consumer != focus && c.event.module != focus) return@mapNotNull null
            val from = if (c.event.module == null) {
                if (c.event.name !in view.foreign) return@mapNotNull null
                ids.foreign(c.event.name)
            } else {
                if (c.event !in view.events) return@mapNotNull null
                ids.event(a.event(c.event) ?: return@mapNotNull null)
            }
            "  $from -.-> ${c.consumer}"
        }
        val pairs = a.calls
            .filter { it.caller in view.modules && it.callee in view.modules }
            .filter { focus == null || it.caller == focus || it.callee == focus }
            .groupBy { it.caller to it.callee }
        val calls = pairs.entries.sortedBy { "${it.key.first} ${it.key.second}" }.map { (pair, list) ->
            val label = list.map { it.command }.distinct().sorted().joinToString("<br/>") { esc(it) }
            "  ${pair.first} ==>|\"$label\"| ${pair.second}"
        }
        (published.sorted() + consumed.distinct().sorted() + calls).forEach { out.append(it).append('\n') }
        return out.toString().trimEnd() + "\n"
    }

    private fun classLine(out: StringBuilder, cls: String, nodes: List<String>) {
        if (nodes.isNotEmpty()) out.append("  class ${nodes.distinct().sorted().joinToString(",")} $cls\n")
    }

    private fun pipe(id: String, label: String) =
        if (legacyShapes) "$id[(\"${esc(label)}\")]" else "$id@{ shape: h-cyl, label: \"${esc(label)}\" }"

    private fun header() = if (legacyShapes) "flowchart TB" else "flowchart LR"

    // ---- overview ---------------------------------------------------------------------------

    private fun overview(a: ZdlArchitecture): String {
        val out = StringBuilder()
        out.append(header()).append('\n')
        out.append("  classDef module fill:#e8f0fe,stroke:#4a6fa5,stroke-width:2px,color:#111\n")
        if (a.modules.any { it.referenced }) {
            out.append("  classDef foreign fill:#f3f3f3,stroke:#888,stroke-dasharray:5 5,color:#111\n")
        }
        out.append('\n')
        for (m in a.modules) out.append("  ${m.id}{{\"${esc(m.name)}\"}}\n")
        out.append('\n')
        classLine(out, "module", a.modules.filter { !it.referenced }.map { it.id })
        classLine(out, "foreign", a.modules.filter { it.referenced }.map { it.id })
        out.append('\n')
        val flows = a.consumptions
            .filter { it.event.module != null && it.event.module != it.consumer }
            .groupBy { it.event.module!! to it.consumer }
        for ((pair, list) in flows.entries.sortedBy { "${it.key.first} ${it.key.second}" }) {
            val names = list.map { it.event.name }.distinct().sorted()
            val label = if (names.size <= 3) names.joinToString("<br/>") { esc(it) } else "${names.size} events"
            out.append("  ${pair.first} -.->|\"$label\"| ${pair.second}\n")
        }
        val calls = a.calls.filter { it.caller != it.callee }.groupBy { it.caller to it.callee }
        for ((pair, list) in calls.entries.sortedBy { "${it.key.first} ${it.key.second}" }) {
            val names = list.map { it.command }.distinct().sorted()
            val label = if (names.size <= 3) names.joinToString("<br/>") { esc(it) } else "${names.size} calls"
            out.append("  ${pair.first} ==>|\"$label\"| ${pair.second}\n")
        }
        return out.toString().trimEnd() + "\n"
    }

    // ---- ids and text -----------------------------------------------------------------------

    /** Unique, valid Mermaid node ids. */
    private class Ids(a: ZdlArchitecture) {
        private val clashing: Set<String> = run {
            val moduleIds = a.modules.map { it.id }.toSet()
            a.events.groupBy { it.name }.filter { it.value.size > 1 }.keys +
                a.events.map { it.name }.filter { it in moduleIds }
        }

        fun box(m: ArchModule) = "sg_${m.id}"

        fun event(e: ArchEvent): String =
            if (e.name in clashing) "${e.module}_${sanitize(e.name)}" else sanitize(e.name)

        fun foreign(channel: String) = "ext_${sanitize(channel)}"

        private fun sanitize(s: String) =
            s.map { if ((it.isLetterOrDigit() && it.code < 128) || it == '_') it else '_' }.joinToString("")
    }

    private fun esc(s: String) = s.replace("\"", "#quot;")

    companion object {
        const val LEGEND: String = "Hexagon = module. Rectangle inside a box = internal event owned by that module. " +
            "Pipe outside boxes = external event (has a topic). Solid = published by. Dashed = consumed by. Thick = direct call."
    }
}
