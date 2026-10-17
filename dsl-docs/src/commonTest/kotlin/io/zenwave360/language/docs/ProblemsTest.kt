package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A source with problems never stops the run: its page names the problem and the index lists it. */
class ProblemsTest {

    private fun generate(vararg sources: DocsSource) = DslDocs().generate(DocsInput(sources.toList()))

    @Test
    fun invalidYaml() {
        val output = generate(DocsSource(SourceKind.OPENAPI, "broken.yml", "openapi: [unclosed"))

        assertEquals(1, output.problems.size)
        assertTrue(output.problems[0].startsWith("broken.yml: cannot read the file as YAML"), output.problems[0])
        val page = output.files.first { it.path == "apis.md" }.content
        assertTrue(page.contains("## broken.yml\n"), page)
        assertTrue(page.contains("> **Problems**"), page)
        assertTrue(page.contains("No operations."), page)
        assertTrue(output.files.first { it.path == "README.md" }.content.contains("## Problems\n\n- broken.yml: cannot read"))
    }

    @Test
    fun zdlSyntaxError() {
        val output = generate(DocsSource(SourceKind.ZDL, "broken.zdl", "entity Order { name String \n entity"))

        assertTrue(output.problems.isNotEmpty())
        assertTrue(output.problems.all { it.startsWith("broken.zdl: ") }, output.problems.toString())
        assertTrue(output.files.first { it.path == "domain.md" }.content.contains("\nclassDiagram\n"))
    }

    @Test
    fun zflSyntaxError() {
        val output = generate(DocsSource(SourceKind.ZFL, "broken.zfl", "flow Broken {"))

        assertTrue(output.problems.isNotEmpty())
        assertTrue(output.problems.all { it.startsWith("broken.zfl: ") }, output.problems.toString())
        assertTrue(output.files.first { it.path == "flows.md" }.content.contains("> **Problems**"))
    }
}
