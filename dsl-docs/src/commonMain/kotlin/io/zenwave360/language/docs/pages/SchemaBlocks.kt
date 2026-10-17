package io.zenwave360.language.docs.pages

import io.zenwave360.language.apis.view.ApiSchema
import io.zenwave360.language.apis.view.ApiSchemaGraph
import io.zenwave360.language.apis.view.ApiSchemaKind
import io.zenwave360.language.apis.view.ApiSchemaToMermaidClassDiagramTransformer
import io.zenwave360.language.docs.internal.Anchors
import io.zenwave360.language.docs.internal.Markdown

/**
 * Which root describes each schema: the first root, in order, that reaches it. A schema is shown in a table only
 * under its owner; later roots that reach it say "Also uses".
 */
internal class Placement(val graph: ApiSchemaGraph, val roots: List<String>) {
    val owner: Map<String, String> = linkedMapOf<String, String>().also { map ->
        for (root in roots) for (schema in graph.reachableFrom(root)) map.getOrPut(schema.name) { root }
    }
}

/** A class diagram and the field tables of everything it shows, for one root. Used by the OpenAPI and AsyncAPI pages. */
internal object SchemaBlocks {

    private val diagrams = ApiSchemaToMermaidClassDiagramTransformer()

    /**
     * The diagram of [root] (stereotype `response`, `request`, `message` or `schema`) and, when [tables] is set, a
     * table per schema the root owns. [headingKey] gives the anchor key of the heading of the root that owns a schema.
     */
    fun render(
        anchors: Anchors,
        placement: Placement,
        root: String,
        stereotype: String,
        tables: Boolean,
        headingKey: (String) -> String,
    ): String = buildString {
        val diagram = diagrams.transform(root, placement.graph, stereotype)
        if (diagram != null) append(Markdown.mermaid(diagram)).append('\n')
        if (!tables) return@buildString
        val reachable = placement.graph.reachableFrom(root)
        val own = reachable.filter { placement.owner[it.name] == root }
        val also = reachable.filter { placement.owner[it.name] != root }
        for (schema in own) append(table(schema))
        if (also.isNotEmpty()) {
            val links = also.joinToString(", ") { anchors.link(headingKey(placement.owner.getValue(it.name)), Markdown.inline(it.name)) }
            append("Also uses ").append(links).append(" (described above).\n\n")
        }
    }

    private fun table(schema: ApiSchema): String = buildString {
        append("**").append(Markdown.inline(schema.name)).append("**")
        if (schema.kind == ApiSchemaKind.ENUM) append(" (enumeration)")
        append("\n\n")
        schema.description?.let { append(Markdown.inline(it)).append("\n\n") }
        when (schema.kind) {
            ApiSchemaKind.ENUM -> append(Markdown.table(listOf("Value"), schema.values.map { listOf(Markdown.code(it)) })).append('\n')
            ApiSchemaKind.EXTERNAL -> if (schema.description == null) append("Defined elsewhere.\n\n")
            ApiSchemaKind.OBJECT -> {
                val rows = schema.fields.map { f ->
                    listOf(Markdown.code(f.name), Markdown.code(f.type), f.required?.let { if (it) "yes" else "no" }, f.description)
                }
                append(if (rows.isEmpty()) "No fields.\n\n" else Markdown.table(listOf("Field", "Type", "Required", "Description"), rows) + "\n")
            }
        }
    }
}
