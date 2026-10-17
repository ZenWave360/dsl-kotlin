package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reads nothing from a filesystem, so it runs on Node and in a headless browser.
 */
class JsBrowserSmokeTest {

    @Test
    fun generatesFromAnInlineModel() {
        val output = DslDocs().generate(DocsInput(listOf(DocsSource(SourceKind.ZDL, "model.zdl", "entity Order { name String }"))))

        assertEquals(listOf("README.md", "architecture.md", "domain.md"), output.files.map { it.path })
        assertTrue(output.files.last().content.contains("class Order"))
    }
}
