package io.zenwave360.language.docs.internal

import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.SourceKind
import kotlin.test.Test
import kotlin.test.assertEquals

class InternalsTest {

    @Test
    fun sha256TestVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc"))
        // Padding spills into a second 64-byte block.
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun markdownHelpers() {
        assertEquals("", Markdown.table(listOf("A"), emptyList()))
        assertEquals(
            "| A | B |\n| --- | --- |\n| x \\| y | line one line two |\n",
            Markdown.table(listOf("A", "B"), listOf(listOf("x | y", " line one\n  line two "))),
        )
        assertEquals("", Markdown.code(""))
        assertEquals("`a'b`", Markdown.code("a`b"))
        assertEquals("", Markdown.problems(emptyList()))
        assertEquals("orders-checkout-apiyml", Markdown.anchor("Orders Checkout-API.yml"))
        assertEquals(
            "<!-- Source: a-b.zdl sha256:${Sha256.hex("")} -->",
            Markdown.header(listOf(DocsSource(SourceKind.ZDL, "a--b.zdl", ""))).lines()[1],
        )
    }

    @Test
    fun yamlKeepsScalarsAsWrittenAndOrder() {
        val map = Yaml.read("b: 1.10\na: [x, { c: ~ }]\n? [complex]\n: ignored")

        assertEquals(listOf("b", "a"), map.keys.toList())
        assertEquals("1.10", map["b"])
        assertEquals(listOf("x", mapOf("c" to "~")), map["a"])
        assertEquals(emptyMap(), Yaml.read(""))
        assertEquals(emptyMap(), Yaml.read("- a list"))
    }
}
