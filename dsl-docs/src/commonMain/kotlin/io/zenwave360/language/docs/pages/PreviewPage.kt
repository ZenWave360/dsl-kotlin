package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.SourceKind
import io.zenwave360.language.docs.internal.Anchors
import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.string
import io.zenwave360.language.zdl.view.ZdlDomain

/**
 * The preview of one ZDL document: a single page with the sections of [ModuleContent] that come from the document alone
 * (services, aggregates, domain events, commands and glossary), without the Architecture, Consumes and Calls sections, which
 * need other documents. Nothing links to another page or names another file, the text the user wrote is escaped (see
 * [Markdown.safeInline]), and the page has no generated-file header.
 */
internal object PreviewPage {

    const val TITLE = "Domain model"

    fun zdl(text: String): String {
        val document = ZdlDocument(DocsSource(SourceKind.ZDL, "preview.zdl", text))
        val domain = ZdlDomain(document.model)
        // First pass collects the headings, second pass links to them, as in ModulePage.
        val first = Anchors()
        build(document, domain, first)
        return build(document, domain, first.resolve())
    }

    private fun build(document: ZdlDocument, domain: ZdlDomain, anchors: Anchors): String = buildString {
        val title = document.title?.let { Markdown.safeHeading(it) }?.takeIf { it.isNotEmpty() } ?: TITLE
        // The client numbers repeated slugs over every heading, the title included.
        append(anchors.heading(1, title))
        document.model.string("javadoc")?.let { append(Markdown.safeJavadoc(it)).append("\n\n") }
        append(Markdown.problems(document.problems.map { Markdown.safeInline(it) }))

        // Before the sections, so its heading is numbered in page order, as the client numbers it.
        val toc = anchors.toc()
        val content = ModuleContent(domain, anchors, safe = true)
        val sections = content.services() + content.aggregates() + content.domainEvents() + content.commands() + content.glossary()
        if (sections.isEmpty()) {
            append("This model has no services, entities or events to show yet.\n")
            return@buildString
        }
        append(toc)
        append(sections)
    }
}
