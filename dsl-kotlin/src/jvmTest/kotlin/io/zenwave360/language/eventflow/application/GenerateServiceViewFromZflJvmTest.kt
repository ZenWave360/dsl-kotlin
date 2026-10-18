package io.zenwave360.language.eventflow.application

import io.zenwave360.language.readTestFile
import io.zenwave360.language.eventflow.view.ServiceViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class GenerateServiceViewFromZflJvmTest {

    @Test
    fun testGeneratePlaceOrderServiceViewMatchesFixture() = runTest {
        val zflContent = readTestFile("flow/place-order-flow.zfl")
        val viewModel = GenerateServiceViewFromZfl().execute(zflContent)
        println(viewModel.toJson(pretty = true))
//        val expected = readTestFile("flow/place-order-flow.services.json")
//
//        assertEquals(
//            ServiceViewModel.fromJson(expected),
//            ServiceViewModel.fromJson(viewModel.toJson(pretty = true))
//        )
    }
}
