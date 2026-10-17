package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.DocsFile
import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.internal.Anchors
import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.problemText
import io.zenwave360.language.eventflow.application.GenerateMermaidFromZfl
import io.zenwave360.language.eventflow.view.MermaidDiagramsView
import io.zenwave360.language.eventflow.view.MermaidSequenceDiagram
import io.zenwave360.language.eventflow.view.MermaidSequenceRenderMode
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflFlow
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.language.zfl.semantic.ZflSemanticModel

/**
 * `flows.md` is the index of the event flows, one row per ZFL file. Each flow has its own page,
 * `flows/<slug>.md`: the happy path first, the flowchart, outcomes and systems, then every other
 * path as a separate sequence diagram.
 */
internal object FlowsPage {

    const val PATH = "flows.md"
    const val TITLE = "Event flows"
    const val DIR = "flows"

    /** A flow that rendered: its page and the row for the index. */
    private class Rendered(val source: DocsSource, val flow: ZflFlow?, val view: MermaidDiagramsView, val slug: String, val problems: List<String>)

    /** The index and one page per flow. [context] is null when there are no ZDL files, so no module links. */
    fun render(sources: List<DocsSource>, context: ArchitectureContext?, problems: MutableList<String>): List<DocsFile> {
        val failed = mutableListOf<Pair<DocsSource, List<String>>>()
        val rendered = mutableListOf<Rendered>()
        val slugs = mutableSetOf<String>()
        for (source in sources) {
            val sourceProblems = ZflParser().parse(source.content).syntaxProblems.map { problemText(it) }
            problems += sourceProblems.map { "${source.path}: $it" }
            try {
                val view = GenerateMermaidFromZfl().execute(source.content, MermaidSequenceRenderMode.SEPARATE_VARIANTS)
                val semantic = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(source.content))
                val base = kebab(view.flowName.ifBlank { source.path.substringAfterLast('/').substringBeforeLast('.') })
                var slug = base
                var n = 2
                while (!slugs.add(slug)) slug = "$base-${n++}"
                rendered += Rendered(source, semantic.flows.firstOrNull(), view, slug, sourceProblems)
            } catch (e: Exception) {
                val reason = "cannot render the flow: ${e.message?.lineSequence()?.firstOrNull() ?: e::class.simpleName}"
                problems += "${source.path}: $reason"
                failed += source to (sourceProblems + reason)
            }
        }
        val files = mutableListOf(DocsFile(PATH, index(sources, rendered, failed)))
        for (r in rendered) {
            val semantic = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(r.source.content))
            files += DocsFile("$DIR/${r.slug}.md", page(r, semantic, context))
        }
        return files
    }

    // ---- index ------------------------------------------------------------------------------

    private fun index(sources: List<DocsSource>, rendered: List<Rendered>, failed: List<Pair<DocsSource, List<String>>>): String = buildString {
        append(Markdown.header(sources))
        append("# ").append(TITLE).append("\n\n")
        if (rendered.isNotEmpty()) {
            append("One page per flow, with its happy path, flowchart and every alternative path as a sequence diagram.\n\n")
            val rows = rendered.map { r ->
                listOf(
                    "[${Markdown.inline(r.view.flowName.ifBlank { r.source.path })}]($DIR/${r.slug}.md)",
                    r.flow?.starts?.joinToString(", ") { Markdown.code(it.name) },
                    outcomes(r).joinToString(", ") { Markdown.code(it) },
                    r.view.sequences.size.toString(),
                    Markdown.code(r.source.path),
                )
            }
            append(Markdown.table(listOf("Flow", "Starts", "End outcomes", "Paths", "Source"), rows)).append('\n')
        }
        for (r in rendered.filter { it.problems.isNotEmpty() }) {
            append("## ").append(Markdown.code(r.source.path)).append("\n\n")
            append(Markdown.problems(r.problems))
        }
        for ((source, list) in failed) {
            append("## ").append(Markdown.code(source.path)).append("\n\n")
            append(Markdown.problems(list))
        }
    }

    /** End outcomes in `end {}` order, then any outcome only the diagrams know. */
    private fun outcomes(r: Rendered): List<String> =
        ((r.flow?.end?.endOutcomes?.keys ?: emptySet()) + r.view.sequences.map { it.endOutcome }).distinct()

    // ---- flow page --------------------------------------------------------------------------

    private fun page(r: Rendered, semantic: ZflSemanticModel, context: ArchitectureContext?): String {
        val first = Anchors()
        build(r, semantic, context, first)
        return build(r, semantic, context, first.resolve())
    }

    private fun build(r: Rendered, semantic: ZflSemanticModel, context: ArchitectureContext?, anchors: Anchors): String = buildString {
        val view = r.view
        val flow = r.flow
        val happy = view.sequences.firstOrNull { it.happy }
        val others = view.sequences.filter { it !== happy }
        fun key(d: MermaidSequenceDiagram) = if (d === happy) "happy" else "variant:${d.title}"

        append(Markdown.header(listOf(r.source)))
        append("# ").append(Markdown.inline(view.flowName.ifBlank { r.source.path })).append("\n\n")
        append(description(flow, view.flowName))
        append("Source: ").append(Markdown.code(r.source.path)).append("\n\n")
        append(Markdown.problems(r.problems))
        append("Back to the [event flows](../$PATH).\n\n")
        append(anchors.toc())

        if (happy != null) {
            append(anchors.heading(2, "Happy path", "happy"))
            append("Starts with ").append(Markdown.code(happy.startLabel)).append(" and ends with the outcome ")
            append(Markdown.code(happy.endOutcome)).append(".\n\n")
            append(Markdown.mermaid(happy.mermaid)).append('\n')
        }

        append(anchors.heading(2, "Flowchart"))
        append("Every command and event of the flow, whichever path they belong to.\n\n")
        append(Markdown.mermaid(view.flowchart)).append('\n')

        val outcomes = outcomes(r)
        if (outcomes.isNotEmpty()) {
            append(anchors.heading(2, "Outcomes"))
            append("How the flow can end, the events that end it and the paths that lead there.\n\n")
            val rows = outcomes.map { outcome ->
                val paths = view.sequences.filter { it.endOutcome == outcome }.joinToString("<br>") { d ->
                    if (d === happy) anchors.link("happy", "Happy path") else anchors.link(key(d), Markdown.inline(d.title))
                }
                listOf(Markdown.code(outcome), flow?.end?.endOutcomes?.get(outcome)?.joinToString(", ") { Markdown.code(it) }, paths)
            }
            append(Markdown.table(listOf("Outcome", "Terminal events", "Paths"), rows)).append('\n')
        }

        val systems = systems(semantic, flow, context)
        if (systems.isNotEmpty()) {
            append(anchors.heading(2, "Systems"))
            append("The systems that take part, the services they expose in this flow and the commands they run.")
            if (context != null) append(" A system with the name of a module links to the page of that module.")
            append("\n\n")
            append(Markdown.table(listOf("System", "Services", "Commands", "Module"), systems)).append('\n')
        }

        if (others.isNotEmpty()) {
            append(anchors.heading(2, "Alternative paths"))
            append("Every other way through the flow, one diagram per path, grouped by the outcome where it ends. ")
            append("The title of a path lists the choices it takes.\n\n")
            for (outcome in outcomes.filter { o -> others.any { it.endOutcome == o } }) {
                append(anchors.heading(3, outcome))
                for (d in others.filter { it.endOutcome == outcome }) {
                    append(anchors.heading(4, Markdown.inline(d.title), key(d)))
                    append(Markdown.mermaid(d.mermaid)).append('\n')
                }
            }
        }
    }

    /**
     * The javadoc of the flow on one line (the parser keeps no paragraph breaks). It usually starts with the flow
     * name in words ("Place Order Flow"), which the heading already shows, so that prefix is dropped.
     */
    private fun description(flow: ZflFlow?, name: String): String {
        var text = Markdown.javadoc(flow?.description)
        val words = name.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        if (words.isNotBlank() && text.startsWith(words, ignoreCase = true)) text = text.substring(words.length).trim()
        return if (text.isEmpty()) "" else "$text\n\n"
    }

    private fun systems(semantic: ZflSemanticModel, flow: ZflFlow?, context: ArchitectureContext?): List<List<String?>> =
        semantic.systems.values.map { system ->
            val commands = flow?.commands.orEmpty().filter { it.system == system.name }.map { it.name }.distinct()
            listOf(
                Markdown.code(system.name),
                system.services.keys.joinToString(", ") { Markdown.code(it) },
                commands.joinToString(", ") { Markdown.code(it) },
                moduleLink(context, system.name),
            )
        }

    /** `../modules/<slug>.md` (or `../domain.md` for a single ZDL) when a module has the id of the system. */
    private fun moduleLink(context: ArchitectureContext?, system: String): String? {
        val module = context?.architecture?.modules?.firstOrNull { it.id == system && !it.referenced } ?: return null
        val page = context.pagePath(module) ?: return null
        return "[${Markdown.inline(module.name)}](../$page)"
    }

    /** `PlaceOrderFlow` becomes `place-order-flow`. */
    internal fun kebab(name: String): String =
        name.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1-$2")
            .lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "flow" }
}
