package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.FlowNodeType
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DisconnectedActionFlowViewTest {

    @Test
    fun testGenerateFlowView_ActionNothingTriggersIsDrawnWithoutThrowing() = runTest {
        val zfl = """
            flow Booking {
                do recordGuestCall {
                    service Bookings.SlotBlockService
                    emits GuestCallCompleted
                }
                end { completed: GuestCallCompleted }
            }
        """.trimIndent()

        val viewModel = GenerateFlowViewFromZfl().execute(zfl)

        assertNotNull(viewModel.nodes.find { it.id == "command:recordGuestCall" && it.type == FlowNodeType.COMMAND })
        assertTrue(viewModel.edges.none { it.target == "command:recordGuestCall" })
    }

    @Test
    fun testGenerateFlowView_EmptyFlowDoesNotThrow() = runTest {
        val viewModel = GenerateFlowViewFromZfl().execute("flow Booking { }")
        assertTrue(viewModel.nodes.isEmpty())
    }
}
