package io.zenwave360.language.eventflow.application

import io.zenwave360.language.readTestFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GenerateFlowVariantFromZflTest {

    @Test
    fun variantsHaveStableSequenceIds() {
        val generator = GenerateFlowViewFromZfl()
        val content = readTestFile("flow/place-order-flow.zfl")

        assertEquals(generator.variants(content), generator.variants(content))
        assertTrue(generator.variants(content).all { it.id.startsWith("sequence:") })
    }

    @Test
    fun variantContainsOnlyItsPathAndFullViewIsUnchanged() = runTest {
        val generator = GenerateFlowViewFromZfl()
        val content = readTestFile("flow/book-slot-flow.zfl")
        val full = generator.execute(content)
        val variant = assertIs<FlowVariantViewResult.Found>(
            generator.variant(content, generator.variants(content).first().id)
        ).view

        assertTrue(variant.nodes.size < full.nodes.size)
        assertTrue(variant.edges.all { edge ->
            variant.nodes.any { it.id == edge.source } && variant.nodes.any { it.id == edge.target }
        })
        assertEquals(full, generator.execute(content))
    }

    @Test
    fun unknownVariantIsNotAnEmptyGraph() = runTest {
        val result = GenerateFlowViewFromZfl().variant(
            readTestFile("flow/place-order-flow.zfl"),
            "sequence:missing:0"
        )

        assertEquals("sequence:missing:0", assertIs<FlowVariantViewResult.UnknownVariant>(result).id)
    }
}
