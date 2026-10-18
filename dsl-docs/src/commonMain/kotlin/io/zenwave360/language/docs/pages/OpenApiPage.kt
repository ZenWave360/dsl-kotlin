package io.zenwave360.language.docs.pages

import io.zenwave360.language.apis.view.ApiSchemaGraphBuilder
import io.zenwave360.language.apis.view.ApiSchemaKind
import io.zenwave360.language.docs.internal.Anchors
import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.asList
import io.zenwave360.language.docs.internal.asMap
import io.zenwave360.language.docs.internal.string

/**
 * `apis.md`: per OpenAPI file, an operations table and the schemas, grouped by response schema. Each group is
 * a class diagram of the response and everything it reaches, with a field table per schema. Components no
 * response reaches (request bodies included) follow under "Other schemas".
 */
internal object OpenApiPage {

    const val PATH = "apis.md"
    const val TITLE = "HTTP APIs"

    private val METHODS = listOf("get", "put", "post", "delete", "options", "head", "patch", "trace")

    fun render(documents: List<SpecDocument>, problems: MutableList<String>): String =
        specPage(TITLE, documents, problems, ::Model, ::section)

    /** A request or response body: the text shown and the class it draws, when it has one. */
    private class Body(val display: String, val root: String?, val many: Boolean)

    private class Operation(val method: String, val path: String, val id: String?, val summary: String?, val request: Body?, val response: Body?) {
        val label get() = "$method $path"
    }

    private class Group(val root: String, val stereotype: String, val operations: List<Operation>)

    private class Model(val document: SpecDocument) {
        val builder = ApiSchemaGraphBuilder(document.spec, document.source.path, document.refs.resolver)
        val operations = operations(document)
        val groups: List<Group>
        val others: List<Group>
        val placement: Placement

        init {
            val responses = operations.mapNotNull { op -> op.response?.root?.let { it to op } }
            val responseRoots = responses.map { it.first }.distinct()
            val requestRoots = operations.mapNotNull { it.request?.root }.distinct()
            val components = document.spec["components"].asMap()["schemas"].asMap().keys.map { name ->
                builder.addJsonSchema(name, mapOf("\$ref" to "#/components/schemas/$name"))
            }.distinct()
            val graph = builder.build()
            groups = responseRoots.map { root -> Group(root, "response", responses.filter { it.first == root }.map { it.second }) }

            val covered = responseRoots.flatMap { graph.reachableFrom(it) }.map { it.name }.toMutableSet()
            val classLike = document.spec["components"].asMap()["schemas"].asMap().filterValues { schema ->
                val node = document.refs.deref(schema, document.ctx)?.value.asMap()
                isClassLike(node) || node.string("type") == "array"
            }.keys
            val candidates = (requestRoots + components).distinct().filter { name -> name !in covered && graph.schemas.containsKey(name) && (name in requestRoots || name in classLike || graph.schemas[name]?.kind == ApiSchemaKind.EXTERNAL) }
            // Roots first: a schema that another uncovered candidate reaches is described under that one.
            val reached = candidates.flatMap { c -> graph.reachableFrom(c).map { it.name }.filter { it != c }.map { c to it } }
            val inner = candidates.filter { c -> reached.any { (from, to) -> to == c && from != c } }
            val ordered = (candidates - inner.toSet()) + inner
            val rootsOfOthers = mutableListOf<String>()
            for (c in ordered) {
                if (c in covered) continue
                rootsOfOthers += c
                graph.reachableFrom(c).forEach { covered += it.name }
            }
            others = rootsOfOthers.map { root ->
                Group(root, if (root in requestRoots) "request" else "schema", operations.filter { it.request?.root == root })
            }
            placement = Placement(graph, groups.map { it.root } + others.map { it.root })
        }

        /** The anchor key of the heading of the group that describes [schema]. */
        fun keyOf(index: Int, schema: String): String? =
            placement.owner[schema]?.let { key(index, it) }

        private fun operations(document: SpecDocument): List<Operation> {
            val spec = document.spec
            val refs = document.refs
            return spec["paths"].asMap().flatMap { (path, item) ->
                val pathItem = item.asMap()
                pathItem.string("\$ref")?.let { ref ->
                    return@flatMap listOf(Operation("", path, null, "Defined in ${Markdown.code(ref)}", null, null))
                }
                pathItem.filterKeys { it in METHODS }.map { (method, operation) ->
                    val op = operation.asMap()
                    val id = op.string("operationId")
                    val name = id ?: (method + path.split('/', '{', '}').filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar(Char::uppercase) })
                    Operation(
                        method.uppercase(), path, id, op.string("summary") ?: op.string("description"),
                        requestBody(op, name, document.ctx, refs), responseBody(op, name, document.ctx, refs),
                    )
                }
            }
        }

        private fun requestBody(op: Map<String, Any?>, name: String, ctx: RefCtx, refs: SpecRefs): Body? {
            val body = refs.deref(op["requestBody"], ctx) ?: return null
            return mediaSchema(body, "${name}Request", refs)
        }

        private fun responseBody(op: Map<String, Any?>, name: String, ctx: RefCtx, refs: SpecRefs): Body? {
            val responses = op["responses"].asMap()
            val code = responses.keys.firstOrNull { it.startsWith("2") } ?: return null
            val response = refs.deref(responses[code], ctx) ?: return null
            return mediaSchema(response, "${name}Response", refs)
        }

        /** The schema of the first JSON-like media type (else the first one) of a body or response. */
        private fun mediaSchema(holder: Resolved, inlineName: String, refs: SpecRefs): Body? {
            val content = holder.value.asMap()["content"].asMap()
            val media = content.entries.firstOrNull { it.key.contains("json", ignoreCase = true) } ?: content.entries.firstOrNull() ?: return null
            val schema = media.value.asMap()["schema"] ?: return null
            return body(schema, holder.ctx, inlineName, refs)
        }

        private fun body(schema: Any?, ctx: RefCtx, inlineName: String, refs: SpecRefs): Body? {
            val resolved = refs.deref(schema, ctx)
            val node = resolved?.value.asMap()
            if (resolved != null && node["type"] == "array") {
                val inner = body(node["items"], resolved.ctx, inlineName, refs) ?: return Body("any[]", null, true)
                return Body(inner.display + "[]", inner.root, true)
            }
            if (resolved != null && !isClassLike(node)) {
                // A scalar or untyped schema: the type, no class.
                return Body(node.string("type")?.let { t -> node.string("format")?.let { "$t($it)" } ?: t } ?: "any", null, false)
            }
            val root = builder.addJsonSchema(inlineName, schema, ctx.document, ctx.base)
            return Body(root, root, false)
        }

        private fun isClassLike(s: Map<String, Any?>): Boolean =
            s["properties"].asMap().isNotEmpty() || s["allOf"].asList().isNotEmpty() || s["enum"].asList().isNotEmpty() ||
                s["oneOf"].asList().isNotEmpty() || s["anyOf"].asList().isNotEmpty() || s.string("type") == "object"
    }

    private fun key(index: Int, root: String) = "openapi:$index:$root"

    private fun section(document: SpecDocument, model: Model, index: Int, anchors: Anchors): String = buildString {
        append(document.heading(anchors, "openapi", "OpenAPI"))

        fun link(body: Body?): String? {
            body ?: return null
            val root = body.root ?: return Markdown.code(body.display)
            val key = model.keyOf(index, root)
            val label = Markdown.code(body.display)
            return if (key == null) label else anchors.link(key, label)
        }

        append(anchors.heading(3, "Operations"))
        if (model.operations.isEmpty()) {
            append("No operations.\n\n")
        } else {
            val rows = model.operations.map { op ->
                listOf(op.method, Markdown.code(op.path), op.id, link(op.request), link(op.response), op.summary)
            }
            append(Markdown.table(listOf("Method", "Path", "Operation", "Request", "Response", "Summary"), rows)).append('\n')
        }

        if (model.groups.isEmpty() && model.others.isEmpty()) return@buildString
        append(anchors.heading(3, "Schemas"))
        append("Schemas are grouped by the response that returns them. A diagram shows the response and everything it reaches; ")
        append("the tables list the fields.\n\n")
        for (group in model.groups) {
            append(anchors.heading(4, Markdown.inline(group.root), key(index, group.root)))
            val used = group.operations.joinToString(", ") { Markdown.code(it.label) }
            append("Returned by ").append(used).append(".\n\n")
            append(SchemaBlocks.render(anchors, model.placement, group.root, group.stereotype, true) { key(index, it) })
        }
        if (model.others.isNotEmpty()) {
            append(anchors.heading(4, "Other schemas"))
            append("Schemas no response returns, such as request bodies.\n\n")
            for (group in model.others) {
                append(anchors.heading(5, Markdown.inline(group.root), key(index, group.root)))
                if (group.operations.isNotEmpty()) {
                    append("Sent to ").append(group.operations.joinToString(", ") { Markdown.code(it.label) }).append(".\n\n")
                }
                append(SchemaBlocks.render(anchors, model.placement, group.root, group.stereotype, true) { key(index, it) })
            }
        }
    }
}
