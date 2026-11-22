package io.zenwave360.language.eventflow.view

import io.zenwave360.language.eventflow.application.GenerateFlowViewFromZfl
import io.zenwave360.language.eventflow.application.GenerateMermaidFromZfl
import io.zenwave360.language.readViewsTestFile
import kotlin.test.Test
import kotlin.test.assertEquals

class ZflFlowVariantsTest {

    @Test
    fun variantsMatchSequenceDiagramsForExistingFixtures() {
        listOf("book-slot-flow.zfl", "place-order-flow.zfl").forEach { fixture ->
            val content = readViewsTestFile("flow/$fixture")
            val variants = GenerateFlowViewFromZfl().variants(content)
            val sequences = GenerateMermaidFromZfl()
                .execute(content, MermaidSequenceRenderMode.SEPARATE_VARIANTS)
                .sequences

            assertEquals(
                sequences.mapIndexed { index, sequence ->
                    "sequence:${sequence.endOutcome}:$index" to sequence.title
                },
                variants.map { it.id to it.title }
            )
        }
    }

    @Test
    fun aTreeLineIsOneSequenceWithAltBlocksAndALeafIsItsVariant() {
        val content = readViewsTestFile("flow/place-order-flow.zfl")
        val generator = GenerateMermaidFromZfl()
        val tree = GenerateFlowViewFromZfl().variantTree(content)
        val line = tree.flatMap { it.nodes }.first { it.children.isNotEmpty() }

        val set = generator.variants(content, line.id)!!
        assertEquals(line.title, set.title)
        assertEquals(true, set.mermaid.contains("alt "))
        assertEquals(line.children.size >= 2, set.mermaid.contains("else "))

        val leaf = generateSequence(line) { it.children.firstOrNull() }.last()
        val single = generator.variants(content, leaf.id)!!
        assertEquals(false, single.mermaid.contains("alt "))
        assertEquals(null, generator.variants(content, "branch:missing:0"))
        val (diagram, node) = generator.variantsWithTree(content, line.id)!!
        assertEquals(line.id, node.id)
        assertEquals(line.variantIds.size, node.variantIds.size)
        assertEquals(set.mermaid, diagram.mermaid)
    }
}
