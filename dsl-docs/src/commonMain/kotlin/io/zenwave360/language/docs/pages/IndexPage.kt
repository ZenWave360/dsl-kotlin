package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.DocsLink
import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.SourceKind
import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.Sha256

/** A generated page, as listed on the index. */
internal class PageEntry(val path: String, val title: String, val summary: String)

/** `README.md`: title, version, the generated pages, links to pages written by hand, and the sources with their hashes. */
internal object IndexPage {

    const val PATH = "README.md"

    fun render(
        title: String,
        version: String?,
        date: String?,
        pages: List<PageEntry>,
        links: List<DocsLink>,
        sources: List<DocsSource>,
        problems: List<String>,
    ): String = buildString {
        append(Markdown.header(sources))
        append("# ").append(Markdown.inline(title)).append("\n\n")
        val meta = listOfNotNull(version?.let { "Version ${Markdown.inline(it)}" }, date?.let { Markdown.inline(it) })
        if (meta.isNotEmpty()) append(meta.joinToString(" · ")).append("\n\n")

        append("## Contents\n\n")
        if (pages.isEmpty()) {
            append("No model or specification files were given.\n\n")
        } else {
            pages.forEach { append("- [").append(it.title).append("](").append(it.path).append("): ").append(it.summary).append('\n') }
            append('\n')
        }

        if (links.isNotEmpty()) {
            append("## More documentation\n\n")
            links.forEach { append("- [").append(Markdown.inline(it.title)).append("](").append(it.href.replace(" ", "%20")).append(")\n") }
            append('\n')
        }

        if (sources.isNotEmpty()) {
            append("## Sources\n\n")
            val rows = sources.map { listOf(Markdown.code(it.path), it.kind.label, Markdown.code(Sha256.hex(it.content))) }
            append(Markdown.table(listOf("File", "Kind", "SHA-256"), rows)).append('\n')
        }

        if (problems.isNotEmpty()) {
            append("## Problems\n\n")
            problems.forEach { append("- ").append(Markdown.inline(it)).append('\n') }
            append('\n')
        }
    }

    private val SourceKind.label: String
        get() = when (this) {
            SourceKind.ZDL -> "ZDL"
            SourceKind.ZFL -> "ZFL"
            SourceKind.OPENAPI -> "OpenAPI"
            SourceKind.ASYNCAPI -> "AsyncAPI"
        }
}
