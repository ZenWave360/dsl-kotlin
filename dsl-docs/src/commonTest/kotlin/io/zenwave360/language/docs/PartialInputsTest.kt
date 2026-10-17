package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every page is optional: the documentation shows what it is given and links only to pages it wrote. */
class PartialInputsTest {

    private fun generate(vararg kinds: SourceKind) =
        DslDocs().generate(DocsInput(arcadiaOrdersCheckout().filter { it.kind in kinds }))

    private fun DocsOutput.readme() = files.first { it.path == "README.md" }.content

    @Test
    fun noSourcesGivesOnlyTheIndex() {
        val output = DslDocs().generate(DocsInput(emptyList()))

        assertEquals(listOf("README.md"), output.files.map { it.path })
        assertTrue(output.readme().contains("# Documentation\n"))
        assertTrue(output.readme().contains("No model or specification files were given."))
        assertFalse(output.readme().contains("## Sources"))
    }

    @Test
    fun withoutAsyncApiThereIsNoEventsPage() {
        val output = generate(SourceKind.ZDL, SourceKind.ZFL, SourceKind.OPENAPI)

        assertEquals(listOf("README.md", "apis.md", "architecture.md", "domain.md", "flows.md"), output.files.map { it.path })
        assertFalse(output.readme().contains("events.md"))
    }

    @Test
    fun onlyZdl() {
        val output = generate(SourceKind.ZDL)

        assertEquals(listOf("README.md", "architecture.md", "domain.md"), output.files.map { it.path })
        assertTrue(output.readme().contains("# Arcadia Editions - Orders Checkout\n\nVersion 0.1.0\n"))
    }

    @Test
    fun onlyAsyncApiTakesTheTitleFromTheSpec() {
        val output = generate(SourceKind.ASYNCAPI)

        assertEquals(listOf("README.md", "events.md"), output.files.map { it.path })
        assertTrue(output.readme().contains("# Arcadia Editions - Orders Checkout\n\nVersion 0.1.1\n"))
    }

    @Test
    fun onlyZfl() {
        val output = generate(SourceKind.ZFL)

        assertEquals(listOf("README.md", "flows.md"), output.files.map { it.path })
        assertTrue(output.files.last().content.contains("```mermaid\nflowchart"))
        assertTrue(output.readme().contains("# Documentation\n"))
    }
}
