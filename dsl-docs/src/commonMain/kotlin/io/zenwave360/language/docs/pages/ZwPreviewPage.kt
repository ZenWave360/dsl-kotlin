package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.CatalogueOption
import io.zenwave360.language.docs.CataloguePlugin
import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.PluginCatalogue
import io.zenwave360.language.docs.SourceKind
import io.zenwave360.language.docs.internal.Anchors
import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.string

/**
 * The preview of one `.zw` document: the global `config` with a description of it, then each plugin in document order with
 * what it is for (from the [PluginCatalogue]), a Bash command and its own and inherited options ([PluginResolution]).
 * Like [PreviewPage] it has no links but to its own headings, names no other file, and escapes
 * everything the user or the catalogue wrote ([Markdown.safeInline], [Markdown.safeLiteral]).
 */
internal object ZwPreviewPage {

    const val TITLE = "Plugin configuration"

    fun zw(text: String, catalogue: PluginCatalogue?): String {
        val document = ZdlDocument(DocsSource(SourceKind.ZDL, "preview.zw", text))
        val model = PluginModel.of(document.model, text)
        // First pass collects the headings, second pass links to them, as in ModulePage.
        val first = Anchors()
        build(document, model, catalogue, first)
        return build(document, model, catalogue, first.resolve())
    }

    private fun build(document: ZdlDocument, model: PluginModel, catalogue: PluginCatalogue?, anchors: Anchors): String = buildString {
        val title = document.title?.let { Markdown.safeHeading(it) }?.takeIf { it.isNotEmpty() } ?: TITLE
        // The client numbers repeated slugs over every heading, the title included.
        append(anchors.heading(1, title))
        document.model.string("javadoc")?.let { append(Markdown.safeJavadoc(it)).append("\n\n") }
        append(Markdown.problems(document.problems.map { Markdown.safeInline(it) }))

        // Before the sections, so its heading is numbered in page order, as the client numbers it.
        val toc = anchors.toc()
        val sections = StringBuilder()
        if (model.config.isNotEmpty() || model.cliOptions.isNotEmpty()) sections.append(configuration(model, anchors))
        if (model.plugins.isNotEmpty()) sections.append(plugins(model, catalogue, anchors))
        if (sections.isEmpty()) {
            append("This file has no `config` block and declares no plugins.\n")
            return@buildString
        }
        append(toc)
        if (catalogue != null && catalogue.plugins.isNotEmpty()) {
            append("Plugin descriptions come from the plugin catalogue: ").append(Markdown.safeInline(catalogue.source)).append(".\n\n")
        }
        append(sections)
        if (model.config.isEmpty() && model.cliOptions.isEmpty()) append("This file has no `config` block, so plugins inherit nothing.\n\n")
        if (model.plugins.isEmpty()) append("No plugins are declared.\n")
    }

    private fun configuration(model: PluginModel, anchors: Anchors): String = buildString {
        append(anchors.heading(2, "Configuration"))
        append("The options of the `config` block. Every plugin inherits them, and the command-line options, unless it declares `(inherit false)`; an option the plugin sets itself replaces the inherited one.\n\n")
        append(Markdown.table(listOf("Property", "Value"), model.config.map { (name, value) -> listOf(Markdown.safeLiteral(name), Markdown.safeLiteral(PluginResolution.formatValue(value))) }))
        if (model.config.isNotEmpty()) append('\n')
        if (model.cliOptions.isNotEmpty()) {
            append("Command-line options:\n\n")
            append(Markdown.table(listOf("Option", "Value"), model.cliOptions.map { listOf(Markdown.safeLiteral("--${it.name}"), cliText(PluginResolution.cliValue(it))) }))
            append('\n')
        }
    }

    private fun plugins(model: PluginModel, catalogue: PluginCatalogue?, anchors: Anchors): String = buildString {
        append(anchors.heading(2, "Plugins"))
        append("Each plugin in the order the file declares them, with its description, the command to run, and its own and inherited configuration.\n\n")
        for (plugin in model.plugins) {
            val entry = catalogue?.plugin(plugin.name)
            val resolved = PluginResolution.resolve(model, plugin)
            append(anchors.heading(3, Markdown.safeHeading(plugin.name + if (plugin.disabled) " (disabled)" else "")))

            append(anchors.heading(4, "Description"))
            if (plugin.disabled) append("Disabled: it does not run.\n\n")
            append(about(entry, catalogue))

            append("<details open>\n<summary>Command to run</summary>\n\n")
            append(Markdown.indentedCode((listOf("jbang", "zw") + resolved.arguments).joinToString(" ", transform = ::bashQuote)))
            append("</details>\n\n")

            append("<details>\n<summary>Configuration</summary>\n\n")
            append(anchors.heading(4, "Own configuration"))
            if (plugin.config.isEmpty() && plugin.cliOptions.isEmpty()) append("Declares no options of its own.\n\n")
            if (plugin.config.isNotEmpty()) {
                append(Markdown.table(listOf("Property", "Value", "Description"), plugin.config.map { (name, value) ->
                    listOf(Markdown.safeLiteral(name), Markdown.safeLiteral(PluginResolution.formatValue(value)), optionText(entry?.options?.get(name)))
                }))
                append('\n')
            }
            if (plugin.cliOptions.isNotEmpty()) {
                append(Markdown.table(listOf("Option", "Value"), plugin.cliOptions.map { listOf(Markdown.safeLiteral("--${it.name}"), cliText(PluginResolution.cliValue(it))) }))
                append('\n')
            }

            append(anchors.heading(4, "Inherited configuration"))
            if (!plugin.inherit) append("Declared `(inherit false)`: it inherits nothing from the `config` block.\n\n")
            val inheritedProperties = resolved.properties.filter { it.origin == Origin.GLOBAL }
            val inheritedCliOptions = resolved.cliOptions.filter { it.origin == Origin.GLOBAL }
            if (inheritedProperties.isEmpty() && inheritedCliOptions.isEmpty()) append("Inherits no options from the `config` block.\n\n")
            if (inheritedProperties.isNotEmpty()) {
                append(Markdown.table(listOf("Property", "Value", "Description"), inheritedProperties.map {
                    listOf(Markdown.safeLiteral(it.name), Markdown.safeLiteral(it.value), optionText(entry?.options?.get(it.name)))
                }))
                append('\n')
            }
            if (inheritedCliOptions.isNotEmpty()) {
                append(Markdown.table(listOf("Option", "Value"), inheritedCliOptions.map {
                    listOf(Markdown.safeLiteral("--${it.name}"), cliText(it.value))
                }))
                append('\n')
            }
            append("</details>\n\n")
        }
    }

    /** What the plugin is for: the catalogue's title, summary, description and documentation, or why there is none. */
    private fun about(entry: CataloguePlugin?, catalogue: PluginCatalogue?): String = buildString {
        if (entry == null) {
            if (catalogue != null) append("This plugin is not in the plugin catalogue, so it has no description.\n\n")
            return@buildString
        }
        entry.title?.let { append("**").append(Markdown.safeInline(it)).append("**\n\n") }
        entry.summary?.let { append(Markdown.safeJavadoc(it)).append("\n\n") }
        entry.description?.takeIf { it != entry.summary }?.let { append(Markdown.safeJavadoc(it)).append("\n\n") }
        entry.documentation?.let { append("Documentation: ").append(Markdown.safeInline(it)).append("\n\n") }
    }

    private fun cliText(value: String?) = value?.let { Markdown.safeLiteral(it) }.orEmpty()

    /** Quotes one SDK argument for Bash, matching the POSIX branch of the client's command builder. */
    private fun bashQuote(argument: String): String {
        if (SAFE_BASH_TOKEN.matches(argument)) return argument
        val equals = argument.indexOf('=')
        if (equals > 0) {
            val name = argument.substring(0, equals + 1)
            val value = argument.substring(equals + 1)
            if (SAFE_BASH_TOKEN.matches(name) && SAFE_BASH_DOUBLE_QUOTED.matches(value)) return "$name\"$value\""
        }
        return "'${argument.replace("'", "'\\''")}'"
    }

    /** What the catalogue says about an option: what it is for, its type, default and allowed values. */
    private fun optionText(option: CatalogueOption?): String {
        option ?: return ""
        val facts = listOfNotNull(
            option.type,
            option.defaultValue?.let { "default $it" },
            option.values.takeIf { it.isNotEmpty() }?.let { "one of ${it.joinToString(", ")}" },
        )
        val text = listOfNotNull(option.description, facts.takeIf { it.isNotEmpty() }?.joinToString(", ", "(", ")")).joinToString(" ")
        return Markdown.safeInline(text)
    }

    private val SAFE_BASH_TOKEN = Regex("^[a-zA-Z0-9_@%+=:,./-]+$")
    private val SAFE_BASH_DOUBLE_QUOTED = Regex("^[^\"\\$`\\\\\\r\\n“”„]*$")
}
