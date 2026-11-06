package io.zenwave360.language.eventflow.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DisconnectedActionMermaidTest {

    @Test
    fun testExecute_ActionNothingTriggersDoesNotThrow() {
        val zfl = """
            flow Booking {
                do recordGuestCall {
                    service Bookings.SlotBlockService
                    emits GuestCallCompleted
                }
                end { completed: GuestCallCompleted }
            }
        """.trimIndent()
        val diagrams = GenerateMermaidFromZfl().execute(zfl)
        assertTrue(diagrams.flowchart.contains("action_recordGuestCall --> event_GuestCallCompleted"))
        assertTrue(diagrams.flowchart.lines().none { it.trim().endsWith("--> action_recordGuestCall") })
        assertTrue(diagrams.sequences.isEmpty())
    }

    @Test
    fun testExecute_EmptyFlowAndNoServiceDoNotThrow() {
        val empty = GenerateMermaidFromZfl().execute("flow Booking { }")
        assertTrue(empty.flowchart.startsWith("flowchart TD"))
        assertEquals(0, empty.sequences.size)
        val noService = GenerateMermaidFromZfl().execute("flow F { start A {} when A do x { emits E } end { completed: E } }")
        assertTrue(noService.flowchart.contains("policy_when_A_do_x --> action_x"))
    }
}
