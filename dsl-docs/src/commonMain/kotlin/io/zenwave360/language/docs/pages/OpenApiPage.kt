package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.asList
import io.zenwave360.language.docs.internal.asMap
import io.zenwave360.language.docs.internal.string

/** `apis.md`: per OpenAPI file, its operations and its schemas. */
internal object OpenApiPage {

    const val PATH = "apis.md"
    const val TITLE = "HTTP APIs"

    private val METHODS = listOf("get", "put", "post", "delete", "options", "head", "patch", "trace")

    fun render(documents: List<SpecDocument>, problems: MutableList<String>): String =
        specPage(TITLE, documents, problems, ::section)

    private fun section(document: SpecDocument): String = buildString {
        val spec = document.spec
        append(document.heading("openapi", "OpenAPI"))

        val operations = operations(spec)
        append("### Operations\n\n")
        if (operations.isEmpty()) {
            append("No operations.\n\n")
        } else {
            append(Markdown.table(listOf("Method", "Path", "Operation", "Tags", "Summary"), operations)).append('\n')
        }

        val schemas = spec["components"].asMap()["schemas"].asMap()
        if (schemas.isNotEmpty()) {
            append("### Schemas\n\n")
            val rows = schemas.map { (name, schema) -> listOf(name, type(schema.asMap()), properties(schema.asMap()), schema.asMap().string("description")) }
            append(Markdown.table(listOf("Schema", "Type", "Properties", "Description"), rows)).append('\n')
        }
    }

    private fun operations(spec: Map<String, Any?>): List<List<String?>> =
        spec["paths"].asMap().flatMap { (path, item) ->
            val pathItem = item.asMap()
            pathItem.string("\$ref")?.let { ref -> return@flatMap listOf(listOf("", path, "", "", "Defined in ${Markdown.code(ref)}")) }
            pathItem.filterKeys { it in METHODS }.map { (method, operation) ->
                val op = operation.asMap()
                listOf(
                    method.uppercase(),
                    Markdown.code(path),
                    op.string("operationId"),
                    op["tags"].asList().joinToString(", ") { it.toString() },
                    op.string("summary") ?: op.string("description"),
                )
            }
        }

    private fun type(schema: Map<String, Any?>): String {
        schema.string("\$ref")?.let { return refName(it) }
        for (combinator in listOf("allOf", "oneOf", "anyOf")) {
            val parts = schema[combinator].asList()
            if (parts.isNotEmpty()) return "$combinator: " + parts.joinToString(", ") { type(it.asMap()) }
        }
        val type = schema.string("type") ?: return ""
        if (type == "array") return "array of " + type(schema["items"].asMap()).ifEmpty { "any" }
        return type
    }

    private fun properties(schema: Map<String, Any?>): String =
        schema["properties"].asMap().keys.joinToString(", ")
}
