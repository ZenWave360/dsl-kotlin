package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.asList
import io.zenwave360.language.docs.internal.asMap
import io.zenwave360.language.docs.internal.problemText
import io.zenwave360.language.docs.internal.string
import io.zenwave360.language.zdl.ZdlModel
import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer

/** A ZDL file with its parsed model, read once and shared by the pages that need it. */
internal class ZdlDocument(val source: DocsSource) {
    val model: ZdlModel = ZdlParser().parseModel(source.content)
    val title: String? = model["config"].asMap().string("title")
    val version: String? = model["config"].asMap().string("version")

    val problems: List<String> = model.getProblems().map { problemText(it) }
}

/** `domain.md`: per ZDL file, its class diagram, a glossary of its terms, its commands and its events. */
internal object DomainPage {

    const val PATH = "domain.md"
    const val TITLE = "Domain model"

    fun render(documents: List<ZdlDocument>, problems: MutableList<String>): String = buildString {
        append(Markdown.header(documents.map { it.source }))
        append("# ").append(TITLE).append("\n\n")
        for (document in documents) {
            problems += document.problems.map { "${document.source.path}: $it" }
            append(section(document))
        }
    }

    /** One ZDL file: class diagram, glossary, commands and events. On `domain.md` it is a `##` section; on a module page ([intros] set) it is the page, and each section starts with a line saying what it shows. */
    internal fun section(document: ZdlDocument, intros: Boolean = false, beforeDiagram: String = "", atEnd: String = ""): String = buildString {
        val h = if (intros) "##" else "###"
        val model = document.model
        append(if (intros) "# " else "## ").append(Markdown.inline(document.title ?: document.source.path)).append("\n\n")
        append("Source: ").append(Markdown.code(document.source.path)).append("\n\n")
        model.string("javadoc")?.let { append(Markdown.javadoc(it)).append("\n\n") }
        append(Markdown.problems(document.problems))
        append(beforeDiagram)

        append("$h Class diagram\n\n")
        if (intros) append("Aggregates, entities and enums of this module and how they relate.\n\n")
        append(Markdown.mermaid(ZdlToMermaidClassDiagramTransformer().transform(model))).append('\n')

        val glossary = glossary(model)
        if (glossary.isNotEmpty()) {
            append("$h Glossary\n\n")
            if (intros) append("Every named type and service of this module.\n\n")
            append(Markdown.table(listOf("Term", "Kind", "Description"), glossary)).append('\n')
        }

        val commands = commands(model)
        if (commands.isNotEmpty()) {
            append("$h Commands\n\n")
            if (intros) append("The methods of the module's services and the events each one emits.\n\n")
            append(Markdown.table(listOf("Service", "Command", "Input", "Returns", "Events", "Description"), commands)).append('\n')
        }

        val events = model["events"].asMap().values.map { it.asMap() }
        if (events.isNotEmpty()) {
            append("$h Events\n\n")
            if (intros) append("Events declared by this module and their fields.\n\n")
            val rows = events.map { event -> listOf(event.string("name"), fields(event), Markdown.javadoc(event.string("javadoc"))) }
            append(Markdown.table(listOf("Event", "Fields", "Description"), rows)).append('\n')
        }
        append(atEnd)
    }

    /** Every named type and service, sorted by name so the order does not depend on the file layout. */
    private fun glossary(model: ZdlModel): List<List<String?>> {
        val terms = mutableListOf<List<String?>>()
        val aggregates = model["aggregates"].asMap().values.map { it.asMap() }
        aggregates.forEach { terms += term(it, "Aggregate") }
        // Same rule as the class diagram: an entity is a root when an aggregate names it or it has @aggregate.
        val roots = aggregates.mapNotNull { it.string("aggregateRoot") }.toSet()
        model["entities"].asMap().values.forEach { entity ->
            val options = entity.asMap()["options"].asMap()
            val kind = when {
                entity.asMap().string("name") in roots || options["aggregate"] != null -> "Aggregate root"
                options["embedded"] != null -> "Embedded entity"
                else -> "Entity"
            }
            terms += term(entity.asMap(), kind)
        }
        model["enums"].asMap().values.forEach { terms += term(it.asMap(), "Enum") }
        model["inputs"].asMap().values.forEach { terms += term(it.asMap(), "Input") }
        model["outputs"].asMap().values.forEach { terms += term(it.asMap(), "Output") }
        model["events"].asMap().values.forEach { terms += term(it.asMap(), "Event") }
        model["services"].asMap().values.forEach { terms += term(it.asMap(), "Service") }
        return terms.sortedWith(compareBy({ it[0].orEmpty().lowercase() }, { it[1].orEmpty() }))
    }

    private fun term(type: Map<String, Any?>, kind: String): List<String?> =
        listOf(type.string("name"), kind, Markdown.javadoc(type.string("javadoc")))

    private fun commands(model: ZdlModel): List<List<String?>> =
        model["services"].asMap().values.flatMap { service ->
            val serviceName = service.asMap().string("name")
            service.asMap()["methods"].asMap().values.map { it.asMap() }.map { method ->
                val input = listOfNotNull(
                    method["paramId"]?.let { "id" },
                    method.string("parameter"),
                ).joinToString(", ")
                val returns = method.string("returnType")?.let { it + if (method["returnTypeIsArray"] == true) "[]" else "" }
                listOf(serviceName, method.string("name"), input, returns, events(method["withEvents"]), Markdown.javadoc(method.string("javadoc")))
            }
        }

    /** `withEvents` is a list of event names; a nested list is a choice between events. */
    private fun events(withEvents: Any?): String = withEvents.asList().joinToString(", ") { event ->
        when (event) {
            is List<*> -> event.joinToString(" | ")
            else -> event?.toString().orEmpty()
        }
    }

    private fun fields(type: Map<String, Any?>): String =
        type["fields"].asMap().values.map { it.asMap() }.joinToString(", ") { field ->
            "${field.string("name")}: ${field.string("type")}${if (field["isArray"] == true) "[]" else ""}"
        }
}
