package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.FlowViewModel
import io.zenwave360.language.readTestFile
import kotlin.test.Test
import kotlin.test.assertEquals
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

        assertEquals(
            FlowViewModel.fromJson(expected),
            FlowViewModel.fromJson(viewModel.toJson(pretty = true))
        )
    }
}
