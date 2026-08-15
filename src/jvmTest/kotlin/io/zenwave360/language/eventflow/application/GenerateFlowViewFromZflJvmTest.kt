package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.FlowViewModel
import io.zenwave360.language.readTestFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GenerateFlowViewFromZflJvmTest {

    @Test
    fun testGeneratePlaceOrderFlowMatchesFixture() = runTest {
        val zflContent = readTestFile("flow/place-order-flow.zfl")
        val viewModel = GenerateFlowViewFromZfl().execute(zflContent)
        println("=== place-order-flow.flow.json ===")
        println(viewModel.toJson(pretty = true))
        println("=== /place-order-flow.flow.json ===")
        val expected = readTestFile("flow/place-order-flow.flow.json")

        val expectedModel = FlowViewModel.fromJson(expected)
        val actualModel = FlowViewModel.fromJson(viewModel.toJson(pretty = true))
        assertTrue(actualModel.nodes.any { it.sourceRef.line > 1 },
            "Generated flow nodes must retain their real ZFL locations")
        val expectedNodeRefs = expectedModel.nodes.associate { it.id to it.sourceRef }
        val expectedEdgeRefs = expectedModel.edges.associate { it.id to it.sourceRef }
        assertEquals(expectedModel, actualModel.copy(
            nodes = actualModel.nodes.map { node ->
                node.copy(sourceRef = expectedNodeRefs.getValue(node.id))
            },
            edges = actualModel.edges.map { edge ->
                edge.copy(sourceRef = expectedEdgeRefs[edge.id])
            },
        ))
    }
}
