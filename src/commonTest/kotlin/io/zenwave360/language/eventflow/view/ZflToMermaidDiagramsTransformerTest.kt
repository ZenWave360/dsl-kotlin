package io.zenwave360.language.eventflow.view

import io.zenwave360.language.readTestFile
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZflToMermaidDiagramsTransformerTest {

    @Test
    fun testTransform_RendersFlowchartAndSequenceVariants() {
        val zflContent = """
            flow CheckoutFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do createOrder {
                    service Orders.OrderService
                    emits OrderCreated
                }

                when OrderCreated do authorizePayment {
                    service Payments.PaymentService
                    emits PaymentAuthorized
                    emits PaymentDeclined
                }

                when PaymentAuthorized do notifySuccess {
                    service Notifications.NotificationService
                    emits ConfirmationSent
                }

                when PaymentDeclined do notifyFailure {
                    service Notifications.NotificationService
                    emits CancellationSent
                }

                end {
                    completed: ConfirmationSent
                    cancelled: CancellationSent
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        assertEquals("zfl.mermaid.view@1", diagrams.schema)
        assertEquals("CheckoutFlow", diagrams.flowName)
        assertTrue(diagrams.flowchart.contains("flowchart TD"))
        assertTrue(diagrams.flowchart.contains("createOrder"))
        assertTrue(diagrams.flowchart.contains("ConfirmationSent"))

        val completed = diagrams.sequences.single { it.outcome == "completed" }
        assertTrue(completed.mermaid.contains("sequenceDiagram"))
        assertTrue(completed.mermaid.contains("actor Customer"))
        assertTrue(completed.mermaid.contains("Customer->>OrderService: createOrder"))
        assertTrue(completed.mermaid.contains("PaymentService-->>NotificationService: PaymentAuthorized"))
        assertTrue(completed.mermaid.contains("Note over NotificationService: end completed"))

        val cancelled = diagrams.sequences.single { it.outcome == "cancelled" }
        assertTrue(cancelled.mermaid.contains("PaymentDeclined"))
        assertTrue(cancelled.mermaid.contains("end cancelled"))
    }

    @Test
    fun testTransform_PlaceOrderFlow_ProducesMultipleOrderCancelledVariants() {
        val zflContent = readTestFile("flow/place-order-flow.zfl")

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        val orderCancelled = diagrams.sequences.filter { it.outcome == "orderCancelled" }
        assertTrue(orderCancelled.size >= 2, "Expected multiple orderCancelled variants")
        assertTrue(orderCancelled.any { it.mermaid.contains("PaymentDeclined") })
        assertTrue(orderCancelled.any { it.mermaid.contains("PaymentRetryExhausted") })

        val completed = diagrams.sequences.filter { it.outcome == "completed" }
        assertTrue(completed.isNotEmpty(), "Expected at least one completed variant")
        assertTrue(completed.any { it.mermaid.contains("StockReserved") })
        assertTrue(completed.any { it.mermaid.contains("OrderConfirmationSent") })
    }
}
