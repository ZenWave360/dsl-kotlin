package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DocsConfigTest {

    @Test
    fun readsTheArcadiaConfig() {
        val config = DocsConfig.parse(readTestFile("arcadia-editions/orders-checkout-api/dsl-docs.yml"))

        assertEquals(null, config.title)
        assertEquals(
            listOf(
                SourceKind.ZDL to "domain-model.zdl",
                SourceKind.ZFL to "../business-flows/place-order-flow.zfl",
                SourceKind.OPENAPI to "openapi.yml",
                SourceKind.ASYNCAPI to "asyncapi.yml",
                SourceKind.ASYNCAPI to "asyncapi-client.yml",
            ),
            config.sources,
        )
        assertEquals(listOf(DocsLink("Service summary", "SUMMARY.md")), config.pages)
    }

    @Test
    fun readsJsonAndScalars() {
        val config = DocsConfig.parse("""{"title": "Orders", "version": "1.2", "date": "", "zdl": ["a.zdl"], "pages": [{"path": "x.md"}]}""")

        assertEquals(DocsConfig("Orders", "1.2", null, listOf(SourceKind.ZDL to "a.zdl"), listOf(DocsLink("x.md", "x.md"))), config)
        assertEquals(DocsConfig(), DocsConfig.parse(""))
    }

    @Test
    fun rejectsInvalidConfigs() {
        fun message(text: String) = assertFailsWith<IllegalArgumentException> { DocsConfig.parse(text) }.message.orEmpty()

        assertTrue(message("zdls: [a.zdl]").startsWith("Unknown config keys: zdls."))
        assertEquals("zdl must be a list", message("zdl: a.zdl"))
        assertEquals("zdl[0] must be a file path", message("zdl: [[a.zdl]]"))
        assertEquals("title must be a single value", message("title: [a]"))
        assertEquals("pages[0] needs a path", message("pages: [{ title: x }]"))
        assertTrue(message("title: [unclosed").startsWith("Config is not valid YAML or JSON"))
    }
}
