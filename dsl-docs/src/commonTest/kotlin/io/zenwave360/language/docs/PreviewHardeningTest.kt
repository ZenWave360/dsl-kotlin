package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Review fixes for the previews: text the user wrote reaches no active Markdown or HTML (LVR-R14), the anchors of the
 * contents agree with the client's numbering of every heading (MP-R14, MP-R15), and nested large integers resolve as the client's
 * `settings` do (MP-R14, MP-T6).
 */
class PreviewHardeningTest {

    private val image = "![x](https://evil.test/a.png)"
    private val script = "<script>alert(1)</script>"
    private val active = Regex("<script|<img|<iframe|!\\[|(?<!\\\\)\\]\\(http|https?://|(?<!\\\\)://|<https", RegexOption.IGNORE_CASE)

    /** The page without code blocks, where only the generator and the escaped text write. */
    private fun prose(page: String) = page.split("```mermaid\n").mapIndexed { i, part -> if (i == 0) part else part.substringAfter("\n```\n") }
        .joinToString("").lines().filterNot { it.startsWith("    ") }.joinToString("\n")

    private fun zdl(text: String) = DslDocs().previewZdl(text)

    private fun zw(text: String) = DslDocs().previewZw(text, null)

    /** `anchorSlugs` of `webview/preview/markdown.ts`, written again here so the test does not use the producer's rule. */
    private fun clientSlugs(headings: List<String>): List<String> {
        val used = mutableSetOf<String>()
        val counts = mutableMapOf<String, Int>()
        return headings.map { text ->
            val base = text.lowercase().replace(Regex("[^a-z0-9 _-]"), "").replace(" ", "-")
            var candidate = base
            while (candidate in used) {
                val next = (counts[base] ?: 0) + 1
                counts[base] = next
                candidate = "$base-$next"
            }
            used.add(candidate)
            candidate
        }
    }

    /** The ATX headings of a page outside fenced blocks, as the client sees them: level and text. */
    private fun headings(page: String): List<Pair<Int, String>> {
        var fenced = false
        val result = mutableListOf<Pair<Int, String>>()
        for (line in page.lines()) {
            if (line.startsWith("```")) fenced = !fenced
            if (!fenced) Regex("^(#{1,6}) (.+)$").find(line)?.let { result += it.groupValues[1].length to it.groupValues[2] }
        }
        return result
    }

    private fun assertContentsLinksNumbered(page: String, section: String) {
        val all = headings(page)
        val slugs = clientSlugs(all.map { it.second })
        val index = all.indexOfFirst { it.first == 2 && it.second == section }
        assertTrue(index > 0, "no section $section in $page")
        val link = Regex("- \\[$section\\]\\(#([^)]*)\\)").find(page)?.groupValues?.get(1)
        assertEquals(slugs[index], link, "the contents link of $section")
        assertTrue(slugs[index].endsWith("-1"), "the title takes the plain slug: ${slugs[index]}")
        // Every link on the page lands on the heading it was written for.
        for (anchor in Regex("\\]\\(#([^)]*)\\)").findAll(page).map { it.groupValues[1] }) assertTrue(anchor in slugs, "no heading for #$anchor")
    }

    @Test
    fun asyncApiAdapterTextCannotBringImagesOrScript() {
        val page = zdl(
            """
            @aggregate
            entity Order { id UUID }
            service S for (Order) {
                @asyncapi(channel: "c", api: "$image")
                run()
                @asyncapi(channel: "d", api: "$script")
                run2()
            }
            """.trimIndent(),
        )

        assertTrue(page.contains("run"), page)
        assertFalse(active.containsMatchIn(prose(page)), page)
        assertTrue(page.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), page)
        assertTrue(page.contains("!\\[x\\](https\\://evil.test/a.png)"), page)
    }

    @Test
    fun otherModelTextOnTheCommandsAndGlossaryPathsIsEscaped() {
        val page = zdl(
            """
            @aggregate
            entity Order { id UUID }
            enum Kind { A("$image"), B("$script") }
            service S for (Order) {
                @listener(event: "$image")
                @asyncapi(channel: "$script")
                run(Order) Order withEvents OrderPlaced
            }
            event OrderPlaced { id UUID }
            """.trimIndent(),
        )

        assertTrue(page.contains("## Glossary"), page)
        assertFalse(active.containsMatchIn(prose(page)), page)
    }

    @Test
    fun descriptionsStartingWithBlockMarkupCannotAddHeadingsOrFences() {
        val fence = "`".repeat(3)
        val page = zdl(
            """
            config { title "Order" }
            /** # Injected heading */
            service S for (Order) {
                /** ${fence}js */
                run()
            }
            /** ## Another */
            @aggregate
            entity Order { /** - item */ id UUID }
            """.trimIndent(),
        )

        val levels = headings(page)
        assertFalse(levels.any { it.second.contains("Injected") || it.second.contains("Another") }, page)
        assertEquals(page.lines().count { it.startsWith("```mermaid") }, page.lines().count { it == "```" }, page)
    }

    @Test
    fun zwPreviewEscapesTitlePluginNamesAndValues() {
        val page = zw("config { title \"$image\" plugins { P { x \"$script\" y \"$image\" } } }")

        assertFalse(active.containsMatchIn(prose(page)), page)
    }

    @Test
    fun theZwTitleEqualToASectionNameDoesNotTakeItsAnchor() {
        val page = zw("config { title \"Configuration\" basePackage \"io.example\" plugins { P { a 1 } } }")

        assertContentsLinksNumbered(page, "Configuration")
        assertTrue(page.contains("- [Configuration](#configuration-1)"), page)
    }

    @Test
    fun theZdlTitleEqualToASectionNameDoesNotTakeItsAnchor() {
        for (name in listOf("Aggregates", "Commands")) {
            val page = zdl(
                """
                config { title "$name" }
                @aggregate
                entity Order { id UUID }
                service S for (Order) { run(Order) Order }
                """.trimIndent(),
            )

            assertContentsLinksNumbered(page, name)
        }
    }

    /** Level and text of the heading a Commands row's input link lands on, and of the section it is under, by the client's numbering. */
    private fun commandInputTarget(page: String): Triple<Int, String, String> {
        val all = headings(page)
        val slugs = clientSlugs(all.map { it.second })
        val anchor = Regex("\\| run \\| \\[[^\\]]*\\]\\(#([^)]*)\\)").find(page)?.groupValues?.get(1)
        val index = slugs.indexOf(anchor)
        assertTrue(index >= 0, "no heading for #$anchor in $page")
        val section = all.take(index + 1).last { it.first == 2 }.second
        return Triple(all[index].first, all[index].second, section)
    }

    @Test
    fun aTypeNamedContentsLinksToItsDefinitionAndNotToTheTableOfContents() {
        val page = zdl(
            """
            config { title "Contents" }
            @aggregate
            entity Order { id UUID }
            entity Contents { id UUID }
            service S for (Order) { run(Contents) Contents }
            """.trimIndent(),
        )

        assertEquals(Triple(4, "Contents", "Glossary"), commandInputTarget(page), page)
    }

    @Test
    fun anAggregateNamedContentsLinksLikeAnyOtherAggregate() {
        fun page(name: String) = zdl("@aggregate\nentity $name { id UUID }\nservice S for ($name) { run($name) $name }")

        val contents = commandInputTarget(page("Contents"))
        val other = commandInputTarget(page("Basket"))

        assertEquals(other.first to other.third, contents.first to contents.third, page("Contents"))
    }

    @Test
    fun anAggregateNamedContentsIsStillInTheTableOfContents() {
        val page = zdl("@aggregate\nentity Contents { id UUID }\nservice S for (Contents) { run(Contents) Contents }")

        val list = page.substringAfter("## Contents\n\n").substringBefore("\n\n")
        assertTrue(list.contains(Regex("  - \\[Contents\\]\\(#")), page)
    }

    @Test
    fun aZwTitleNamedContentsDoesNotShiftTheSections() {
        val page = zw("config { title \"Contents\"\n  basePackage \"io.example\"\n}")

        assertEquals(1, Regex("- \\[Configuration\\]\\(#configuration\\)").findAll(page).count(), page)
        val all = headings(page)
        assertEquals(listOf(1 to "Contents", 2 to "Contents", 2 to "Configuration"), all, page)
        val contents = clientSlugs(all.map { it.second })
        assertEquals(listOf("contents", "contents-1", "configuration"), contents)
    }

    @Test
    fun aTitleWithAClosingHashSequenceKeepsItsSlug() {
        val page = zdl("config { title \"Commands #\" }\n@aggregate\nentity Order { id UUID }\nservice S for (Order) { run(Order) Order }")

        val all = headings(page)
        assertEquals(1, all.count { it.first == 1 }, page)
        assertFalse(all.first().second.endsWith(" #"), page)
    }

    @Test
    fun nestedLargeIntegersResolveAsTheClientDoes() {
        val page = zw(
            """
            config {
              plugins {
                P {
                  settings { "n": 9223372036854775807, "m": 9007199254740991, "k": 9007199254740992, "neg": -9007199254740992, "small": 2, "list": [1, 9223372036854775807] }
                }
              }
            }
            """.trimIndent(),
        )

        val expected = "{\"n\":\"9223372036854775807\",\"m\":9007199254740991,\"k\":\"9007199254740992\",\"neg\":\"-9007199254740992\",\"small\":2,\"list\":[1,\"9223372036854775807\"]}"
        val escaped = expected.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]").replace("_", "\\_").replace("*", "\\*")
        assertEquals(1, page.split(escaped).size - 1, page)
    }
}
