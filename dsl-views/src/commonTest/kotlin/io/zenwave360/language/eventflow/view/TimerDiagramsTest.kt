package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Experimental `timer` steps: drawn as a policy with a timer, armed by its event. */
class TimerDiagramsTest {

    private val source = """
        flow OrderFlow {
            start PlaceOrder {}
            when PlaceOrder do placeOrder {
                service Orders.OrdersService
                emits OrderCreated
            }
            when OrderCreated do payOrder {
                service Payments.PaymentsService
                emits OrderPaid
            }
            timer after OrderCreated "10 minutes" unless OrderPaid do expireOrder {
                service Orders.OrdersService
                emits OrderExpired
            }
            timer at Order.closesAt do closeOrder {
                service Orders.OrdersService
                emits OrderClosed
            }
            end {
                completed: OrderPaid
                expired: OrderExpired
                closed: OrderClosed
            }
        }
    """.trimIndent()

    private val diagrams = ZflToMermaidDiagramsTransformer().transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(source)))

    @Test
    fun flowchartDrawsTheTimerBetweenItsEventAndItsCommand() {
        val flowchart = diagrams.flowchart
        val policyId = "policy_when_OrderCreated_do_expireOrder"
        assertTrue(
            "    $policyId{{\"⏳ 10 minutes after OrderCreated, unless OrderPaid<br/>when OrderCreated do expireOrder\"}}" in flowchart.lines(),
            flowchart,
        )
        assertTrue("    event_OrderCreated --> $policyId" in flowchart.lines(), flowchart)
        assertTrue("    $policyId --> action_expireOrder" in flowchart.lines(), flowchart)
        assertTrue("    action_expireOrder --> event_OrderExpired" in flowchart.lines(), flowchart)
        // a timer at a view datum has no arming event: its node still leads to its command
        assertTrue("    policy_when__do_closeOrder --> action_closeOrder" in flowchart.lines(), flowchart)
        assertTrue("⏳ at Order.closesAt" in flowchart, flowchart)
    }

    @Test
    fun sequenceOfTheTimerOutcomeNotesTheTimerBeforeItsCommand() {
        val expired = diagrams.sequences.single { it.endOutcome == "expired" }.mermaid
        assertEquals(
            listOf(
                "sequenceDiagram",
                "%% OrderFlow: from PlaceOrder → expireOrder to expired",
                "participant PlaceOrder as PlaceOrder",
                "participant OrdersService as OrdersService",
                "PlaceOrder->>OrdersService: placeOrder",
                "OrdersService-->>OrdersService: OrderCreated",
                "Note over OrdersService: ⏳ 10 minutes after OrderCreated, unless OrderPaid",
                "OrdersService->>OrdersService: expireOrder",
                "OrdersService-->>OrdersService: OrderExpired",
                "Note over OrdersService: end expired",
            ),
            expired.lines().map { it.trim() },
            expired,
        )
    }
}
