package io.zenwave360.language.docs

import io.zenwave360.language.docs.pages.AsyncApiPage
import io.zenwave360.language.docs.pages.DomainPage
import io.zenwave360.language.docs.pages.FlowsPage
import io.zenwave360.language.docs.pages.IndexPage
import io.zenwave360.language.docs.pages.OpenApiPage
import io.zenwave360.language.docs.pages.PageEntry
import io.zenwave360.language.docs.pages.SpecDocument
import io.zenwave360.language.docs.pages.ZdlDocument

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
        val openapi = sources.filter { it.kind == SourceKind.OPENAPI }.map { SpecDocument(it) }
        val asyncapi = sources.filter { it.kind == SourceKind.ASYNCAPI }.map { SpecDocument(it) }

        if (zdl.isNotEmpty()) {
            files += DocsFile(DomainPage.PATH, DomainPage.render(zdl, problems))
            pages += PageEntry(DomainPage.PATH, DomainPage.TITLE, count(zdl.size, "ZDL file"))
        }
        if (zfl.isNotEmpty()) {
            files += DocsFile(FlowsPage.PATH, FlowsPage.render(zfl, problems))
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

    private fun normalize(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')

    private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"
}
