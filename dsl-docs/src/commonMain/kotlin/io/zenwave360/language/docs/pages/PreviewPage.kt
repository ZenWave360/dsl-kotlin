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
 * need other documents. Its APIs may link to their relative or HTTP(S) locations; all other user text is escaped (see
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
        val policies = policies(document)
        val content = ModuleContent(domain, anchors, safe = true, policies = { aggregate -> policies.filter { it.aggregate == aggregate }.map { it.name to it.value } })
        val sections = apis(document, anchors) + content.services() + content.aggregates() + content.domainEvents() + content.commands() + content.glossary()
        if (sections.isEmpty()) {
            append("This model has no services, entities or events to show yet.\n")
            return@buildString
        }
        append(toc)
        append(sections)
    }

    private data class Policy(val name: String, val value: String, val aggregate: String?)

    @Suppress("UNCHECKED_CAST")
    private fun policies(document: ZdlDocument): List<Policy> =
        (document.model["policies"].let { it as? Map<*, *> }?.values ?: document.model["policies"] as? List<*>)?.mapNotNull { raw ->
            val policy = raw as? Map<String, Any?> ?: return@mapNotNull null
            val name = policy["name"] as? String ?: return@mapNotNull null
            val value = policy["value"]?.toString() ?: ""
            Policy(name, value, policy["aggregate"] as? String)
        }.orEmpty()

    @Suppress("UNCHECKED_CAST")
    private fun apis(document: ZdlDocument, anchors: Anchors): String {
        val apis = document.model["apis"] as? Map<String, Any?> ?: return ""
        val rows = apis.mapNotNull { (key, raw) ->
            val api = raw as? Map<String, Any?> ?: return@mapNotNull null
            val name = (api["name"] as? String) ?: key
            val type = api["type"] as? String ?: ""
            val role = api["role"] as? String ?: ""
            val location = api["uri"] as? String ?: ""
            listOf(Markdown.safeInline(name), Markdown.safeInline(type), Markdown.safeInline(role), location(location))
        }
        if (rows.isEmpty()) return ""
        return buildString {
            append(anchors.heading(2, "APIs"))
            append("The APIs this model provides or uses.\n\n")
            append(Markdown.table(listOf("API", "Kind", "Role", "Location"), rows)).append('\n')
        }
    }

    /** Only paths the preview host can follow are links. Everything else remains escaped text. */
    private fun location(uri: String): String {
        val href = uri.takeIf(::linkableLocation)?.replace("(", "%28")?.replace(")", "%29")
        return if (href == null) Markdown.safeLiteral(uri) else "[${Markdown.safeLiteral(uri)}]($href)"
    }

    private fun linkableLocation(uri: String): Boolean =
        (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true) || !uri.contains(':') && !uri.startsWith('/')) &&
            uri.matches(Regex("[-A-Za-z0-9._~:!$&'*+,;=@%/?#()]+"))
}
