package io.zenwave360.language.docs

import io.zenwave360.language.docs.pages.ArchitectureContext
import io.zenwave360.language.docs.pages.ArchitecturePage
import io.zenwave360.language.docs.pages.AsyncApiPage
import io.zenwave360.language.docs.pages.DomainPage
import io.zenwave360.language.docs.pages.FlowsPage
import io.zenwave360.language.docs.pages.IndexPage
import io.zenwave360.language.docs.pages.ModulePage
import io.zenwave360.language.docs.pages.OpenApiPage
import io.zenwave360.language.docs.pages.PageEntry
import io.zenwave360.language.docs.pages.PreviewPage
import io.zenwave360.language.docs.pages.SpecDocument
import io.zenwave360.language.docs.pages.ZdlDocument
import io.zenwave360.language.zdl.architecture.ZdlArchitectureReader
import io.zenwave360.language.zdl.architecture.ZdlSource
import io.zenwave360.language.zdl.view.ZdlArchitectureToMermaidTransformer

/**
 * Generates Markdown documentation for one service or monolith from its ZDL, ZFL, OpenAPI and
 * AsyncAPI files. Diagrams are inline Mermaid blocks, which GitHub, GitLab and most Markdown
 * viewers render.
 *
 * The output is a function of the input only: the same input gives the same files, byte for byte,
 * on every platform. Line endings of the sources are normalized to `\n` first, so a checkout with
 * CRLF gives the same output and the same source hashes. A page is written only when it has sources.
 */
class DslDocs {

    fun generate(input: DocsInput): DocsOutput {
        val sources = input.sources.map { it.copy(content = normalize(it.content)) }
        val problems = mutableListOf<String>()
        val files = mutableListOf<DocsFile>()
        val pages = mutableListOf<PageEntry>()

        val zdl = sources.filter { it.kind == SourceKind.ZDL }.map { ZdlDocument(it) }
        val zfl = sources.filter { it.kind == SourceKind.ZFL }
        val openapi = sources.filter { it.kind == SourceKind.OPENAPI }.map { SpecDocument(it, input.fileReader) }
        val asyncapi = sources.filter { it.kind == SourceKind.ASYNCAPI }.map { SpecDocument(it, input.fileReader) }

        var architectureContext: ArchitectureContext? = null
        if (zdl.isNotEmpty()) {
            val architecture = ZdlArchitectureReader().read(zdl.map { ZdlSource(it.source.path, it.source.content) })
            val diagrams = ZdlArchitectureToMermaidTransformer().transform(architecture)
            val context = ArchitectureContext(architecture, diagrams, zdl, input.sourcesHref)
            architectureContext = context
            files += DocsFile(ArchitecturePage.PATH, ArchitecturePage.render(context, problems, FlowsPage.summaries(zfl)))
            pages += PageEntry(ArchitecturePage.PATH, ArchitecturePage.TITLE, ArchitecturePage.summary(context))
            if (context.modulePages) {
                for (module in architecture.modules.filter { !it.referenced && context.document(it) != null }) {
                    val path = context.pagePath(module)!!
                    files += DocsFile(path, ModulePage.render(context, module, problems))
                    pages += PageEntry(path, module.name, "module, ${context.document(module)!!.source.path.substringAfterLast('/')}")
                }
            } else {
                files += DocsFile(DomainPage.PATH, DomainPage.render(context, problems))
                pages += PageEntry(DomainPage.PATH, DomainPage.TITLE, count(zdl.size, "ZDL file"))
            }
        }
        if (zfl.isNotEmpty()) {
            files += FlowsPage.render(zfl, architectureContext, problems)
            pages += PageEntry(FlowsPage.PATH, FlowsPage.TITLE, count(zfl.size, "ZFL file"))
        }
        if (openapi.isNotEmpty()) {
            files += DocsFile(OpenApiPage.PATH, OpenApiPage.render(openapi, problems))
            pages += PageEntry(OpenApiPage.PATH, OpenApiPage.TITLE, count(openapi.size, "OpenAPI file"))
        }
        if (asyncapi.isNotEmpty()) {
            files += DocsFile(AsyncApiPage.PATH, AsyncApiPage.render(asyncapi, problems))
            pages += PageEntry(AsyncApiPage.PATH, AsyncApiPage.TITLE, count(asyncapi.size, "AsyncAPI file"))
        }

        val specs = openapi + asyncapi
        val title = input.title ?: zdl.firstNotNullOfOrNull { it.title } ?: specs.firstNotNullOfOrNull { it.title } ?: "Documentation"
        val version = input.version ?: zdl.firstNotNullOfOrNull { it.version } ?: specs.firstNotNullOfOrNull { it.version }
        files += DocsFile(IndexPage.PATH, IndexPage.render(title, version, input.date, pages, input.links, sources, problems))

        return DocsOutput(files.sortedBy { it.path }, problems.toList())
    }

    /**
     * The Markdown of a single page that previews one ZDL document, for an editor that shows the model beside its text. [generate]
     * is not involved: it writes a documentation folder, this gives one page whose output does not depend on other files.
     *
     * The page is the sections of the domain page that come from the document alone (services, aggregates and their class
     * diagrams, domain events, commands, glossary), with Mermaid diagrams as fenced `mermaid` blocks. It has no links to other pages,
     * names no other file, and what the user wrote in the model (descriptions, comments) is escaped, so it cannot bring raw HTML,
     * images or links into the page; `<script>` shows as text. Same-page links, from the contents and from commands to the glossary, remain.
     *
     * Like [generate], it never throws on a bad model: a document with semantic problems (unknown references) still gives its
     * page, and a syntax error gives the part of the model the parser could read, with a `Problems` block naming each error with
     * its line. A document with nothing to show gives a page that says so. Line endings are normalized as in [generate].
     * Runs on every platform, reads no file and gives the same text for the same input.
     */
    fun previewZdl(text: String): String = PreviewPage.zdl(normalize(text))

    private fun normalize(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
}
