package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.application.GenerateFlowViewFromZfl
import io.zenwave360.language.eventflow.view.FlowNodeType
import io.zenwave360.language.readTestFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class GenerateFlowViewFromZflTest {

    @Test
    fun testGenerateFlowView() = runTest {
        val zflContent = readTestFile("flow/place-order-flow.zfl")
        val generator = GenerateFlowViewFromZfl()
        val viewModel = generator.execute(zflContent)
        println(viewModel.toJsonString())
    }

    @Test
    fun testGenerateFlowViewModelsResponsesAndStartMetadata() = runTest {
        val zflContent = """
            flow ResponseFlow {
                @actor(Scheduler)
                @time("every day at 09:00")
                start RefreshDue {}

                when RefreshDue do refreshStatus {
                    service Test.StatusService
                    emits response StatusReady
                }

                end { completed: StatusReady }
            }
        """.trimIndent()

        val viewModel = GenerateFlowViewFromZfl().execute(zflContent)

        assertNotNull(viewModel.nodes.find { it.id == "event:StatusReady" && it.type == FlowNodeType.EVENT })
        assertNotNull(viewModel.nodes.find {
            it.id == "readModel:StatusReady" && it.type == FlowNodeType.READ_MODEL
        })
        val start = assertNotNull(viewModel.nodes.find { it.id == "event:RefreshDue" })
        assertEquals("timer", start.startKind)
        assertEquals("Scheduler", start.actor)
        assertNull(viewModel.nodes.find { it.id == "event:StatusReady" }?.startKind)
    }
}
