package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.application.GenerateFlowViewFromZfl
import io.zenwave360.language.readTestFile
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class GenerateFlowViewFromZflTest {

    @Test
    fun testGenerateFlowView() = runTest {
        val zflContent = readTestFile("flow/place-order-flow.zfl")
        val generator = GenerateFlowViewFromZfl()
        val viewModel = generator.execute(zflContent)
        println(viewModel.toJsonString())
    }
}
