package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.Yaml
import io.zenwave360.language.docs.internal.asMap
import io.zenwave360.language.docs.internal.string

/** An OpenAPI or AsyncAPI file read as plain maps. A file that is not valid YAML gives an empty [spec] and a [problem]. */
internal class SpecDocument(val source: DocsSource) {
    val problem: String?
    val spec: Map<String, Any?>

    init {
        var read: Map<String, Any?> = emptyMap()
        var failure: String? = null
        try {
            read = Yaml.read(source.content)
        } catch (e: Exception) {
            failure = "cannot read the file as YAML: ${e.message?.lineSequence()?.firstOrNull() ?: e::class.simpleName}"
        }
        spec = read
        problem = failure
    }

    val info: Map<String, Any?> get() = spec["info"].asMap()
    val title: String? get() = info.string("title")
    val version: String? get() = info.string("version")

    /** Title and version, source file and spec version ([specKey] is `openapi` or `asyncapi`), description and problems. */
    fun heading(specKey: String, specName: String): String = buildString {
        append("## ").append(Markdown.inline(title ?: source.path))
        version?.let { append(" ").append(Markdown.inline(it)) }
        append("\n\n")
        append("Source: ").append(Markdown.code(source.path))
        spec.string(specKey)?.let { append(" · ").append(specName).append(" ").append(it) }
        append("\n\n")
        info.string("description")?.let { append(Markdown.inline(it)).append("\n\n") }
        append(Markdown.problems(listOfNotNull(problem)))
    }
}

/** A page with one section per spec file. Problems reading a file are added to [problems]. */
internal fun specPage(
    title: String,
    documents: List<SpecDocument>,
    problems: MutableList<String>,
    section: (SpecDocument) -> String,
): String = buildString {
    append(Markdown.header(documents.map { it.source }))
    append("# ").append(title).append("\n\n")
    for (document in documents) {
        document.problem?.let { problems += "${document.source.path}: $it" }
        append(section(document))
    }
}

/** The last segment of a local or remote `$ref`, for example `Order` for `#/components/schemas/Order`. */
internal fun refName(ref: String): String = ref.substringAfterLast('#').substringAfterLast('/').ifEmpty { ref }

