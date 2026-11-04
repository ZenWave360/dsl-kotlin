package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.FlowViewModel
import io.zenwave360.language.eventflow.view.ZflToFlowGraphTransformer
import io.zenwave360.language.readTestFile
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import java.io.File
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
        assertEquals(
            actualModel.edges.size,
            actualModel.edges.map { it.id }.distinct().size,
            "Generated flow edges must have unique identities"
        )
        assertTrue(actualModel.edges.all { (it.sourceRef?.line ?: 0) > 1 },
            "Generated flow edges must retain their real ZFL locations")
        val expectedNodeRefs = expectedModel.nodes.associate { it.id to it.sourceRef }
        assertEquals(expectedModel, actualModel.copy(
            nodes = actualModel.nodes.map { node ->
                node.copy(sourceRef = expectedNodeRefs.getValue(node.id))
            },
            edges = actualModel.edges.mapIndexed { index, edge ->
                edge.copy(sourceRef = expectedModel.edges[index].sourceRef)
            },
        ))
    }

    @Test
    fun testAllZflFixturesHaveUniqueSemanticEdgeIds() {
        val root = generateSequence(File(".").canonicalFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").exists() }
        val fixtures = listOf("dsl-kotlin", "dsl-views", "dsl-docs").flatMap { module ->
            File(root, "$module/src/commonTest/resources").walkTopDown()
                .filter { it.extension == "zfl" }
                .toList()
        }

        fixtures.forEach { fixture ->
            val graph = ZflToFlowGraphTransformer().transform(
                ZflSemanticAnalyzer().analyze(ZflParser().parseModel(fixture.readText()))
            )
            assertEquals(
                graph.edges.size,
                graph.edges.map { it.id }.distinct().size,
                "${fixture.relativeTo(root)} has duplicate edge IDs"
            )
        }
    }
}
