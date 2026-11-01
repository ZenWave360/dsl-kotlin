package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** [DslDocs.previewZw]: one page for one `.zw` document, and the [PluginCatalogue] it reads. */
class PreviewZwTest {

    private val catalogueJson = readTestFile("plugin-catalogue/sdk-plugin-catalogue.json")
    private val catalogue = PluginCatalogue.parse(catalogueJson)
    private val scripts = readTestFile("zw/scripts.zw")
    private val arcadia = readTestFile("arcadia-editions/orders-checkout-api/domain-model.zdl")

    private fun preview(text: String, catalogue: PluginCatalogue? = this.catalogue) = DslDocs().previewZw(text, catalogue)

    private fun hostileCatalogue(text: String) = PluginCatalogue.parse(
        """{"version":"1","source":"$text","plugins":[{"name":"P","configClassName":"x.P","title":"$text","summary":"$text","description":"$text",""" +
            """"documentation":"$text","options":{"o":{"description":"$text","type":"T","defaultValue":"$text","values":["$text"]}}}]}""",
    )

    @Test
    fun matchesTheSnapshot() {
        val page = preview(scripts)
        val snapshot = "snapshots/preview/zw-scripts.md"
        if (updateSnapshot(snapshot, page)) return
        val expected = try {
            readTestFile(snapshot)
        } catch (e: Throwable) {
            fail("Missing snapshot $snapshot. Run the JVM tests with DSL_DOCS_UPDATE_SNAPSHOTS=true to create it.")
        }
        assertEquals(expected, page)
    }

    @Test
    fun showsTheConfigAndEachPluginInDocumentOrder() {
        val page = preview(scripts)

        assertTrue(page.startsWith("# Arcadia Editions - Orders Checkout scripts\n"), page.take(80))
        assertTrue(page.contains("\n## Configuration\n"))
        assertTrue(page.contains("\n## Plugins\n"))
        val order = listOf("### ZDLToOpenAPIPlugin\n", "### ZDLToAsyncAPIPlugin\n", "### ZdlToMarkdownPlugin (disabled)\n", "### TeamOwnPlugin\n").map { page.indexOf(it) }
        assertTrue(order.all { it > 0 } && order == order.sorted(), order.toString())
        // What the plugin is for comes from the catalogue.
        assertTrue(page.contains(assertNotNull(catalogue.plugin("ZDLToOpenAPIPlugin")).summary!!))
    }

    @Test
    fun resolvedConfigurationListsInheritedAndOwnValues() {
        val page = preview(scripts)
        val openApi = page.substringAfter("### ZDLToOpenAPIPlugin\n").substringBefore("\n### ")
        val asyncApi = page.substringAfter("### ZDLToAsyncAPIPlugin\n").substringBefore("\n### ")

        val resolved = openApi.substringAfter("#### Resolved configuration")
        assertTrue(resolved.contains("| basePackage | io.example.orders | config |"), resolved)
        assertTrue(resolved.contains("| targetFolder | build/generated | plugin, replaces config |"), resolved)
        assertTrue(resolved.contains("| --force |  | config |"), resolved)
        assertTrue(resolved.contains("| --profile | test | plugin, replaces config |"), resolved)
        // (inherit false) lists only its own.
        val own = asyncApi.substringAfter("#### Resolved configuration")
        assertTrue(own.contains("| schemaFormat | avro | plugin |"), own)
        assertFalse(own.contains("basePackage"), own)
        assertFalse(own.contains("--force"), own)
        assertTrue(asyncApi.contains("Declared `(inherit false)`"))
    }

    @Test
    fun aPluginNotInTheCatalogueStillShowsItsNameAndConfiguration() {
        val page = preview(scripts)
        val team = page.substringAfter("### TeamOwnPlugin\n")

        assertTrue(team.contains("This plugin is not in the plugin catalogue, so it has no description."))
        assertTrue(team.contains("| maxDepth | 3 | plugin |"), team)
        assertTrue(team.contains("| paths | src/a,src/b | plugin |"), team)
        assertTrue(team.contains("| basePackage | io.example.orders | config |"), team)
    }

    @Test
    fun withoutACatalogueNoPluginHasADescription() {
        for (none in listOf(null, PluginCatalogue.EMPTY)) {
            val page = preview(scripts, none)

            assertFalse(page.contains(assertNotNull(catalogue.plugin("ZDLToOpenAPIPlugin")).summary!!))
            assertTrue(page.contains("| targetFolder | build/generated | plugin, replaces config |"))
            assertTrue(page.contains("### ZDLToOpenAPIPlugin\n"))
        }
    }

    @Test
    fun optionDescriptionsComeFromTheCatalogue() {
        val option = assertNotNull(assertNotNull(catalogue.plugin("ZDLToOpenAPIPlugin")).options["idType"])
        val page = preview(scripts)

        assertTrue(page.contains(Regex("\\| idType \\| integer \\| plugin \\| ${Regex.escape(option.description!!)}")), page)
    }

    @Test
    fun arcadiaConfigurationPreviewsWithoutRemoteReferences() {
        val page = preview(arcadia)

        assertTrue(page.contains("### ZDLToAsyncAPIPlugin\n"))
        assertTrue(page.contains("asyncapiOverlayFiles"))
        assertFalse(Regex("https?://").containsMatchIn(page), "an unescaped URL")
        assertTrue(page.contains("https\\://raw.githubusercontent.com"))
        val links = Regex("\\]\\(([^)]*)\\)").findAll(page).map { it.groupValues[1] }.toList()
        assertTrue(links.isNotEmpty() && links.all { it.startsWith("#") }, links.toString())
        assertFalse(page.contains("Generated by dsl-docs"))
        assertFalse(page.contains("```mermaid"))
    }

    @Test
    fun catalogueTextAndModelValuesCannotBringScriptImagesOrRemoteReferences() {
        val hostile = "<script>alert(1)</script> <img src=http://evil.test/x.png onerror=alert(2)> ![pic](http://evil.test/y.png) [l](https://evil.test) <https://evil.test/a> `code` *bold*"
        val zw = """
            config {
              title "<script>alert(3)</script>"
              basePackage "<img src=http://evil.test/b.png>"
              plugins {
                P { value "![pic](http://evil.test/c.png) <iframe src=http://evil.test></iframe>" o "[l](https://evil.test)" }
              }
            }
        """.trimIndent()

        val page = preview(zw, hostileCatalogue(hostile.replace("\"", "'")))

        assertFalse(Regex("<script|<img|<iframe|<https|!\\[|(?<!\\\\)\\]\\(http|https?://", RegexOption.IGNORE_CASE).containsMatchIn(page), page)
        assertTrue(page.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "shown as text")
        assertTrue(page.contains("!\\[pic\\](http\\://evil.test/y.png)"), "the image shows as text")
        assertFalse(page.contains("```"), "no code the catalogue could open or close")
    }

    @Test
    fun aFileWithNoPluginsOrNoConfigGivesASensiblePage() {
        val noPlugins = preview("config { basePackage \"io.example\" }", null)
        val noConfig = preview("config { plugins { ZDLToOpenAPIPlugin { targetFolder \".\" } } }", null)
        val empty = preview("", null)

        assertTrue(noPlugins.contains("| basePackage | io.example |"))
        assertTrue(noPlugins.contains("No plugins are declared."))
        assertTrue(noConfig.contains("### ZDLToOpenAPIPlugin\n"))
        assertTrue(noConfig.contains("| targetFolder | . | plugin |"))
        assertTrue(noConfig.contains("so plugins inherit nothing"))
        assertTrue(empty.contains("This file has no `config` block and declares no plugins."))
        for (page in listOf(noPlugins, noConfig, empty)) assertFalse(page.contains("null"), page)
    }

    @Test
    fun aZdlModelWithoutConfigIsAnEmptyConfigurationPage() {
        assertTrue(preview("entity Order { name String }").contains("declares no plugins"))
    }

    @Test
    fun semanticProblemsStillGiveThePage() {
        val page = preview("entity Order { customer Customer }\nconfig { plugins { ZDLToOpenAPIPlugin { targetFolder \".\" } } }")

        assertTrue(page.contains("### ZDLToOpenAPIPlugin\n"), page)
    }

    @Test
    fun syntaxErrorGivesAPageWithTheProblemsAndWhatWasRead() {
        val page = preview("config { basePackage \"io.example\" plugins { ZDLToOpenAPIPlugin { targetFolder \".\" } Broken { x \n")

        assertTrue(page.contains("> **Problems**"), page)
        assertTrue(page.contains("line "), page)
        assertTrue(page.contains("### ZDLToOpenAPIPlugin\n"), page)
        assertFalse(page.contains("### Broken"), "an unfinished declaration is left out, as in the extension")
    }

    @Test
    fun isDeterministicAndIndependentOfLineEndings() {
        assertEquals(preview(scripts), preview(scripts))
        assertEquals(preview(scripts), preview(scripts.replace("\n", "\r\n")))
    }

    @Test
    fun pluginsWithTheSameNameKeepTheirOwnHeadings() {
        val page = preview("config { plugins { P { a 1 } P { a 2 } } }")
        val anchors = Regex("\\]\\(#([^)]*)\\)").findAll(page).map { it.groupValues[1] }.toList()

        assertTrue(page.contains("| a | 1 | plugin |") && page.contains("| a | 2 | plugin |"))
        assertTrue(anchors.contains("p") && anchors.contains("p-1"), anchors.toString())
    }

    // The catalogue

    @Test
    fun theShippedCatalogueParsesAndPluginsAreFoundByName() {
        assertEquals("ZenWave SDK 2.8.0-SNAPSHOT", catalogue.source)
        assertTrue(catalogue.plugins.size >= 16, catalogue.plugins.size.toString())
        val plugin = assertNotNull(catalogue.plugin("ZDLToOpenAPIPlugin"))
        assertEquals("io.zenwave360.sdk.plugins.ZDLToOpenAPIPlugin", plugin.configClassName)
        assertTrue(plugin.options.containsKey("idType"))
        assertEquals(null, catalogue.plugin("zdltoopenapiplugin"), "the match is exact, as in the extension")
        assertEquals(null, catalogue.plugin("io.zenwave360.sdk.plugins.ZDLToOpenAPIPlugin"), "the class name is not a name")
    }

    @Test
    fun theReaderFollowsTheExtensionsReader() {
        // catalogue.test.ts: catalogue parsing uses the simple class name and retains reported option metadata
        val parsed = PluginCatalogue.parse(
            """{"version":"9.8.7","plugins":[{"configClassName":"com.example.CustomPlugin","plugin":{"summary":"A custom plugin"},
            |"documentation":"https://example.test/plugin","options":{"mode":{"description":"Generation mode","type":"Mode",
            |"defaultValue":"safe","values":["safe","fast"],"docLink":"https://example.test/plugin#mode"}},"futureField":"ignored"},
            |{"name":"NoClass"},"junk"]}""".trimMargin(),
        )

        assertEquals("ZenWave SDK 9.8.7", parsed.source)
        assertEquals(1, parsed.plugins.size)
        val plugin = parsed.plugins[0]
        assertEquals("CustomPlugin", plugin.name)
        assertEquals("A custom plugin", plugin.summary)
        assertEquals(CatalogueOption("mode", "Generation mode", "Mode", "safe", listOf("safe", "fast"), "https://example.test/plugin#mode"), plugin.options["mode"])
        // catalogue.test.ts: documentation is used only when the catalogue reports it
        assertEquals("https://example.test/plugin", plugin.documentation)
        assertEquals(null, PluginCatalogue.parse("""{"plugins":[{"configClassName":"com.example.PrivatePlugin","options":{}}]}""").plugins[0].documentation)
    }

    @Test
    fun aMalformedCatalogueFailsWithAClearMessage() {
        assertTrue(assertFailsWith<IllegalArgumentException> { PluginCatalogue.parse("{ not json") }.message!!.startsWith("The plugin catalogue is not valid JSON"))
        assertEquals("The plugin catalogue must be a JSON object", assertFailsWith<IllegalArgumentException> { PluginCatalogue.parse("[]") }.message)
        assertEquals("The plugin catalogue has no \"plugins\" array", assertFailsWith<IllegalArgumentException> { PluginCatalogue.parse("{\"version\":\"1\"}") }.message)
        assertEquals("The plugin catalogue has no \"plugins\" array", assertFailsWith<IllegalArgumentException> { PluginCatalogue.parse("{\"plugins\":{}}") }.message)
        assertTrue(assertFailsWith<IllegalArgumentException> { PluginCatalogue.parse("") }.message!!.contains("JSON"))
    }

    @Test
    fun timesParsingTheCatalogueApartFromThePage() {
        val sources = listOf(scripts, arcadia) + arcadiaModulith().map { it.content }
        val largest = sources.maxByOrNull { it.length }!!
        PluginCatalogue.parse(catalogueJson)
        preview(largest)
        val parseStart = kotlin.time.TimeSource.Monotonic.markNow()
        repeat(5) { PluginCatalogue.parse(catalogueJson) }
        val parse = parseStart.elapsedNow() / 5
        val pageStart = kotlin.time.TimeSource.Monotonic.markNow()
        repeat(10) { preview(largest) }
        val page = pageStart.elapsedNow() / 10
        println("ZW-TIMING catalogue parse (${catalogueJson.length} chars): $parse; page for ${largest.length} chars: $page")
    }
}
