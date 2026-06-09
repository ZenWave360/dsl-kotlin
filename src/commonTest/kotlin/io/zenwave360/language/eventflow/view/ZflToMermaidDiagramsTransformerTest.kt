package io.zenwave360.language.eventflow.view

import io.zenwave360.language.readTestFile
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZflToMermaidDiagramsTransformerTest {

    @Test
    fun testTransform_DefaultModeUsesAltBlocksCompatibility() {
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
        assertEquals(
            MermaidSequenceRenderMode.ALT_BLOCKS,
            diagrams.sequenceRenderMode
        )

        val completed = diagrams.sequences.single { it.endOutcome == "completed" }
        assertTrue(completed.mermaid.contains("sequenceDiagram"))
        assertTrue(completed.mermaid.contains("actor Customer"))
        assertTrue(completed.mermaid.contains("Customer->>OrderService: createOrder"))
        assertTrue(completed.mermaid.contains("PaymentService-->>NotificationService: PaymentAuthorized"))
        assertTrue(completed.mermaid.contains("Note over NotificationService: end completed"))

        val cancelled = diagrams.sequences.single { it.endOutcome == "cancelled" }
        assertTrue(cancelled.mermaid.contains("PaymentDeclined"))
        assertTrue(cancelled.mermaid.contains("end cancelled"))
    }

    @Test
    fun testTransform_AltBlocksModeMergesVariantsAtForkPoint() {
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
                    done: ConfirmationSent, CancellationSent
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(
                ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)),
                MermaidSequenceRenderMode.ALT_BLOCKS
            )

        assertEquals(MermaidSequenceRenderMode.ALT_BLOCKS, diagrams.sequenceRenderMode)

        val done = diagrams.sequences.single { it.endOutcome == "done" }
        assertEquals("done: from CheckoutStarted", done.title)
        assertEquals("CheckoutStarted", done.startLabel)
        assertEquals(listOf("via PaymentAuthorized", "via PaymentDeclined"), done.branchLabels)
        assertTrue(done.mermaid.contains("alt via PaymentAuthorized"))
        assertTrue(done.mermaid.contains("else via PaymentDeclined"))
        assertTrue(done.mermaid.contains("Customer->>OrderService: createOrder"))
        assertTrue(done.mermaid.contains("PaymentService-->>NotificationService: PaymentAuthorized"))
        assertTrue(done.mermaid.contains("PaymentService-->>NotificationService: PaymentDeclined"))
    }

    @Test
    fun testTransform_PlaceOrderFlow_DefaultModeSplitsScenarioFamiliesBeforeMerging() {
        val zflContent = readTestFile("flow/place-order-flow.zfl")

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        val orderCancelled = diagrams.sequences.filter { it.endOutcome == "orderCancelled" }
        assertTrue(orderCancelled.size >= 2, "Expected distinct scenario families for orderCancelled")
        assertTrue(orderCancelled.any { it.mermaid.contains("via PaymentDeclined") })
        assertTrue(orderCancelled.any { it.title == "orderCancelled: from ReservationExpired" })
    }

    @Test
    fun testTransform_AutoModeUsesAltBlocksForMultipleVariants() {
        val zflContent = readTestFile("flow/place-order-flow.zfl")

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(
                ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)),
                MermaidSequenceRenderMode.AUTO
            )

        val orderCancelled = diagrams.sequences.filter { it.endOutcome == "orderCancelled" }
        assertTrue(orderCancelled.any { it.mermaid.contains("via PaymentDeclined") })
    }

    @Test
    fun testTransform_SeparateVariantsModeUsesForkLabelsInMetadata() {
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

                end {
                    done: PaymentAuthorized, PaymentDeclined
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(
                ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)),
                MermaidSequenceRenderMode.SEPARATE_VARIANTS
            )

        val done = diagrams.sequences.filter { it.endOutcome == "done" }
        assertEquals(2, done.size)
        assertTrue(done.any { it.title == "done: from CheckoutStarted via PaymentAuthorized" })
        assertTrue(done.any { it.title == "done: from CheckoutStarted via PaymentDeclined" })
        assertTrue(done.any { it.branchLabels == listOf("via PaymentAuthorized") })
        assertTrue(done.any { it.branchLabels == listOf("via PaymentDeclined") })
    }
}
