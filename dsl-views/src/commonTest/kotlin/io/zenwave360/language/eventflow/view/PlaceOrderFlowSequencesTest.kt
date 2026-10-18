package io.zenwave360.language.eventflow.view

import io.zenwave360.language.readViewsTestFile
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Paths of the real place-order flow (PLAN.md D20-D23), read from the live ZFL copy in dsl-views test resources. */
class PlaceOrderFlowSequencesTest {

    private fun diagrams(mode: MermaidSequenceRenderMode): MermaidDiagramsView =
        ZflToMermaidDiagramsTransformer().transform(
            ZflSemanticAnalyzer().analyze(ZflParser().parseModel(readViewsTestFile("flow/place-order-flow.zfl"))),
            mode
        )

    @Test
    fun separateVariantsListsEveryPathOnce() {
        val titles = diagrams(MermaidSequenceRenderMode.SEPARATE_VARIANTS).sequences.map { it.title }

        assertEquals(
            listOf(
                "completed: from StartOrderCheckout via StockReservedResult → authorized → FulfillmentScheduled → PaymentCaptured",
                "completed: from StartOrderCheckout via StockReservedResult → failed → retried → authorized → FulfillmentScheduled → PaymentCaptured",
                "stockGone: from StartOrderCheckout via StockUnavailableResult",
                "orderCancelled: from StartOrderCheckout via StockReservedResult → declined",
                "orderCancelled: from StartOrderCheckout via StockReservedResult → authorized → FulfillmentFailed",
                "orderCancelled: from StartOrderCheckout via StockReservedResult → failed → retry-exhausted",
                "orderCancelled: from StartOrderCheckout via StockReservedResult → authorized → FulfillmentScheduled → PaymentCaptureFailed",
                "orderCancelled: from StartOrderCheckout via StockReservedResult → failed → retried → declined",
                "orderCancelled: from StartOrderCheckout via StockReservedResult → failed → retried → authorized → FulfillmentFailed",
                "orderCancelled: from StartOrderCheckout via StockReservedResult → failed → retried → failed → retry-exhausted",
                "orderCancelled: from StartOrderCheckout via StockReservedResult → failed → retried → authorized → FulfillmentScheduled → PaymentCaptureFailed",
                "orderCancelled: from ReservationExpired"
            ),
            titles
        )
        assertEquals(titles.size, titles.toSet().size, "titles must be unique")
    }

    @Test
    fun separateVariantsHaveNoWarningsNothingAfterTerminalEventAndExactlyOneHappyPath() {
        val view = diagrams(MermaidSequenceRenderMode.SEPARATE_VARIANTS)

        view.sequences.forEach { sequence ->
            assertFalse(sequence.mermaid.contains("%%"), sequence.mermaid)
            val lines = sequence.mermaid.lines()
            assertTrue(lines.last().startsWith("    Note over "), sequence.mermaid)
            assertTrue(lines[lines.size - 2].contains("-->>"), sequence.mermaid)
        }
        val happy = view.sequences.filter { it.happy }
        assertEquals(1, happy.size)
        assertEquals("completed", happy.single().endOutcome)
        assertEquals(
            "completed: from StartOrderCheckout via StockReservedResult → authorized → FulfillmentScheduled → PaymentCaptured",
            happy.single().title
        )
        assertEquals(listOf("StockReservedResult", "authorized", "FulfillmentScheduled", "PaymentCaptured"), happy.single().branchLabels)
    }

    @Test
    fun happyPathMermaidExactText() {
        val happy = diagrams(MermaidSequenceRenderMode.SEPARATE_VARIANTS).sequences.single { it.happy }

        assertEquals(
            """
            sequenceDiagram
                actor Customer
                participant OrdersCheckoutService as OrdersCheckoutService
                participant InventoryService as InventoryService
                participant PaymentsProcessingService as PaymentsProcessingService
                participant FulfillmentShippingService as FulfillmentShippingService
                participant NotificationsConsumerService as NotificationsConsumerService
                Customer->>OrdersCheckoutService: startOrderCheckout
                OrdersCheckoutService->>InventoryService: reserveStock
                InventoryService-->>OrdersCheckoutService: StockReservedResult
                OrdersCheckoutService-->>PaymentsProcessingService: OrderCreated
                PaymentsProcessingService->>PaymentsProcessingService: authorizePayment
                PaymentsProcessingService-->>OrdersCheckoutService: PaymentAuthorized
                OrdersCheckoutService->>OrdersCheckoutService: confirmOrder
                OrdersCheckoutService->>InventoryService: confirmStockReservation
                InventoryService-->>OrdersCheckoutService: StockReservationConfirmed
                OrdersCheckoutService-->>FulfillmentShippingService: OrderConfirmed
                FulfillmentShippingService->>FulfillmentShippingService: scheduleFulfillment
                FulfillmentShippingService-->>PaymentsProcessingService: FulfillmentScheduled
                PaymentsProcessingService->>PaymentsProcessingService: capturePayment
                PaymentsProcessingService-->>NotificationsConsumerService: PaymentCaptured
                NotificationsConsumerService->>NotificationsConsumerService: sendOrderConfirmation
                NotificationsConsumerService-->>NotificationsConsumerService: OrderConfirmationSent
                Note over NotificationsConsumerService: end completed
            """.trimIndent(),
            happy.mermaid
        )
    }

    @Test
    fun stockGonePathDoesNotGoThroughPayments() {
        val stockGone = diagrams(MermaidSequenceRenderMode.SEPARATE_VARIANTS).sequences.single { it.endOutcome == "stockGone" }

        assertFalse(stockGone.mermaid.contains("authorizePayment"), stockGone.mermaid)
        assertTrue(stockGone.mermaid.contains("InventoryService-->>OrdersCheckoutService: StockUnavailableResult"), stockGone.mermaid)
    }

    @Test
    fun altBlocksKeepsOneDiagramPerOutcomeAndStartWithoutWarnings() {
        val view = diagrams(MermaidSequenceRenderMode.ALT_BLOCKS)

        assertEquals(
            listOf(
                "completed: from StartOrderCheckout",
                "stockGone: from StartOrderCheckout",
                "orderCancelled: from StartOrderCheckout",
                "orderCancelled: from ReservationExpired"
            ),
            view.sequences.map { it.title }
        )
        view.sequences.forEach { assertFalse(it.mermaid.contains("%%"), it.mermaid) }
        assertEquals(1, view.sequences.count { it.happy })
        assertTrue(view.sequences.single { it.title == "completed: from StartOrderCheckout" }.happy)
    }

    @Test
    fun actorsFirstThenParticipantsWithoutBoxes() {
        val mermaid = diagrams(MermaidSequenceRenderMode.SEPARATE_VARIANTS).sequences.first().mermaid.lines()

        assertEquals("sequenceDiagram", mermaid[0])
        assertEquals("    actor Customer", mermaid[1])
        assertEquals("    participant OrdersCheckoutService as OrdersCheckoutService", mermaid[2])
        assertTrue(mermaid.none { it.trimStart().startsWith("box ") })
    }
}
