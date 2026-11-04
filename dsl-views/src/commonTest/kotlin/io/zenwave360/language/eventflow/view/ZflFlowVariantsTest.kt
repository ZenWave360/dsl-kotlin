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
                    FlowVariant("sequence:${sequence.endOutcome}:$index", sequence.title)
                },
                variants
            )
        }
    }
}
