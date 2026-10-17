package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.DocsFileReader
import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.internal.Anchors
import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.Yaml
import io.zenwave360.language.docs.internal.asMap
import io.zenwave360.language.docs.internal.string

/**
 * An OpenAPI or AsyncAPI file read as plain maps. A file that is not valid YAML gives an empty [spec] and a [problem].
 * [refs] loads the files it refers to through [reader] (none: every file reference stays unresolved).
 */
internal class SpecDocument(val source: DocsSource, reader: DocsFileReader? = null) {
    val problem: String?
    val spec: Map<String, Any?>
    val refs = SpecRefs(reader, source.kind)

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

    val ctx: RefCtx get() = RefCtx(spec, source.path)

    val info: Map<String, Any?> get() = spec["info"].asMap()
    val title: String? get() = info.string("title")
    val version: String? get() = info.string("version")

    /** Title and version, source file and spec version ([specKey] is `openapi` or `asyncapi`), description and problems. */
    fun heading(anchors: Anchors, specKey: String, specName: String): String = buildString {
        append(anchors.heading(2, Markdown.inline(title ?: source.path) + (version?.let { " " + Markdown.inline(it) } ?: "")))
        append("Source: ").append(Markdown.code(source.path))
        spec.string(specKey)?.let { append(" · ").append(specName).append(" ").append(it) }
        append("\n\n")
        info.string("description")?.let { append(Markdown.inline(it)).append("\n\n") }
        append(Markdown.problems(listOfNotNull(problem)))
    }
}

/**
 * A page with one section per spec file, with a `## Contents` list. [model] is computed once per file;
 * [section] renders it and runs twice, so links can point forward. Problems reading a file, or a file it
 * refers to, are added to [problems]. The header lists the spec files and the referenced files that were read.
 */
internal fun <M> specPage(
    title: String,
    documents: List<SpecDocument>,
    problems: MutableList<String>,
    model: (SpecDocument) -> M,
    section: (SpecDocument, M, Int, Anchors) -> String,
): String {
    val models = documents.map { model(it) }
    fun build(anchors: Anchors): String = buildString {
        append("# ").append(title).append("\n\n")
        append(anchors.toc())
        documents.forEachIndexed { i, document -> append(section(document, models[i], i, anchors)) }
    }
    val first = Anchors()
    build(first)
    val body = build(first.resolve())
    for (document in documents) {
        document.problem?.let { problems += "${document.source.path}: $it" }
        document.refs.problems.forEach { problems += "${document.source.path}: $it" }
    }
    val files = documents.flatMap { listOf(it.source) + it.refs.read }.distinctBy { it.path }
    return Markdown.header(files) + body
}

/** The last segment of a local or remote `$ref`, for example `Order` for `#/components/schemas/Order`. */
internal fun refName(ref: String): String = ref.substringAfterLast('#').substringAfterLast('/').ifEmpty { ref }
