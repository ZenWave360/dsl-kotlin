package io.zenwave360.eventflow.application

import io.zenwave360.language.eventflow.application.GenerateFlowViewFromZfl
import io.zenwave360.zdl.internal.readTestFile
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class GenerateFlowViewFromZflTest {

    @Test
    fun testGenerateFlowView() = runTest {
        val zflContent = readTestFile("flow/subscriptions.zfl")
        val generator = GenerateFlowViewFromZfl()
        val viewModel = generator.execute(zflContent)
        println(viewModel.toJsonString())
    }
}
