package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeterminismTest {

    @Test
    fun sameInputGivesTheSameFiles() {
        val first = DslDocs().generate(DocsInput(arcadiaOrdersCheckout(), fileReader = arcadiaFileReader))
        val second = DslDocs().generate(DocsInput(arcadiaOrdersCheckout(), fileReader = arcadiaFileReader))

        assertEquals(first, second)
    }

    @Test
    fun crlfSourcesGiveTheSameFilesAndHashesAsLf() {
        val lf = arcadiaOrdersCheckout()
        val crlf = lf.map { it.copy(content = it.content.replace("\n", "\r\n")) }

        assertEquals(DslDocs().generate(DocsInput(lf, fileReader = arcadiaFileReader)), DslDocs().generate(DocsInput(crlf, fileReader = arcadiaFileReader)))
    }

    @Test
    fun printsTheDateOnlyWhenGiven() {
        val sources = arcadiaOrdersCheckout().take(1)
        fun readme(input: DocsInput) = DslDocs().generate(input).files.first { it.path == "README.md" }.content

        assertFalse(readme(DocsInput(sources)).contains("2026-10-13"))
        assertTrue(readme(DocsInput(sources, title = "Orders", version = "2.0.0", date = "2026-10-13")).contains("# Orders\n\nVersion 2.0.0 · 2026-10-13\n"))
    }
}
