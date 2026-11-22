package io.zenwave360.language.eventflow.application

import io.zenwave360.language.readTestFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GenerateFlowVariantFromZflTest {
    private val generator = GenerateFlowViewFromZfl()


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
    fun variantsCarryTheirOutcomeAndStepsAndTheTitleIsWrittenFromThem() {
        generator.variants(readTestFile("flow/place-order-flow.zfl")).forEach {
            assertTrue(it.outcome.isNotBlank() && it.steps.isNotEmpty())
            assertEquals(io.zenwave360.language.eventflow.view.variantTitle(it.steps, it.outcome), it.title)
        }
    }

    @Test
    fun aTreeLineLaysOutAllItsVariantsTogether() = runTest {
        val content = readTestFile("flow/place-order-flow.zfl")
        val tree = generator.variantTree(content)
        val line = tree.flatMap { it.nodes }.first { it.children.isNotEmpty() }
        val single = assertIs<FlowVariantViewResult.Found>(generator.variant(content, line.variantIds.first())).view
        val together = assertIs<FlowVariantViewResult.Found>(generator.variant(content, line.id)).view
        val full = generator.execute(content)

        assertTrue(together.nodes.size >= single.nodes.size)
        assertTrue(together.nodes.size <= full.nodes.size)
        assertTrue(together.nodes.all { it.position != null })
        assertTrue(together.nodes.map { it.id }.containsAll(single.nodes.map { it.id }))
        assertTrue(together.edges.all { e -> together.nodes.any { it.id == e.source } && together.nodes.any { it.id == e.target } })
        val siblings = line.variantIds.drop(1).map { assertIs<FlowVariantViewResult.Found>(generator.variant(content, it)).view }
        assertTrue(siblings.all { s -> together.nodes.map { it.id }.containsAll(s.nodes.map { it.id }) })
    }

    @Test
    fun unknownVariantIsNotAnEmptyGraph() = runTest {
        val result = GenerateFlowViewFromZfl().variant(
            readTestFile("flow/place-order-flow.zfl"),
            "sequence:missing:0"
        )

        assertEquals("sequence:missing:0", assertIs<FlowVariantViewResult.UnknownVariant>(result).id)
    }

    private suspend fun positioned(): Pair<String, Map<String, io.zenwave360.language.eventflow.view.Point>> {
        val content = readTestFile("flow/place-order-flow.zfl")
        val view = generator.execute(content)
        return content to view.nodes.mapIndexed { i, n -> n.id to io.zenwave360.language.eventflow.view.Point(100.0 + i * 10, 50.0 + i * 7) }.toMap()
    }

    @Test
    fun withPositionsAppliesAllAndRecomputesBounds() = runTest {
        val (content, positions) = positioned()
        val result = assertIs<FlowPositionsResult.Applied>(generator.withPositions(content, positions + ("ghost" to io.zenwave360.language.eventflow.view.Point(1.0, 1.0))))
        assertTrue(result.view.nodes.all { positions[it.id] == it.position })
        val auto = generator.execute(content)
        assertEquals(auto.nodes.map { it.dimensions }, result.view.nodes.map { it.dimensions })
        val maxX = result.view.nodes.maxOf { it.position!!.x + it.dimensions!!.width }
        val maxY = result.view.nodes.maxOf { it.position!!.y + it.dimensions!!.height }
        assertEquals(maxX + 20.0, result.view.bounds!!.width)
        assertEquals(maxY + 20.0, result.view.bounds!!.height)
        assertEquals(auto.layout, result.view.layout)
    }

    @Test
    fun withPositionsMissingNodeFallsBackToAutomaticLayout() = runTest {
        val (content, positions) = positioned()
        val dropped = positions.keys.first()
        val result = assertIs<FlowPositionsResult.NotApplied>(generator.withPositions(content, positions - dropped))
        assertEquals(FlowPositionsReason.MISSING_NODES, result.reason)
        assertEquals(listOf(dropped), result.missing)
        assertEquals(generator.execute(content), result.view)
    }

    @Test
    fun withPositionsNonFiniteIsInvalid() = runTest {
        val (content, positions) = positioned()
        val bad = positions.keys.first()
        val result = assertIs<FlowPositionsResult.NotApplied>(
            generator.withPositions(content, positions + (bad to io.zenwave360.language.eventflow.view.Point(Double.NaN, 1.0)))
        )
        assertEquals(FlowPositionsReason.INVALID, result.reason)
        assertEquals(listOf(bad), result.missing)
        assertEquals(generator.execute(content), result.view)
    }
}
