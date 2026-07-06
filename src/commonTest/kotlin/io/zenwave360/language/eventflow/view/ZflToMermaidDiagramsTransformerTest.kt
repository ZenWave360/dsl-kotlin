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
    fun testTransform_OutcomeAnnotatedFlowchartSynthesizesOutcomeNodes() {
        val zflContent = """
            flow CheckoutFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do authorizePayment {
                    service Payments.PaymentService
                    @outcome("authorized") emits PaymentAuthorized, OrderUpdated
                    @outcome("declined") emits PaymentDeclined
                }

                end {
                    done: PaymentAuthorized, OrderUpdated, PaymentDeclined
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        assertTrue(
            diagrams.flowchart.contains("action_authorizePayment --> outcome_authorizePayment_authorized"),
            diagrams.flowchart
        )
        assertTrue(
            diagrams.flowchart.contains("outcome_authorizePayment_authorized([\"authorized\"])"),
            diagrams.flowchart
        )
        assertTrue(diagrams.flowchart.contains("outcome_authorizePayment_authorized --> event_PaymentAuthorized"))
        assertTrue(diagrams.flowchart.contains("outcome_authorizePayment_authorized --> event_OrderUpdated"))
        assertTrue(diagrams.flowchart.contains("outcome_authorizePayment_declined([\"declined\"])"))
        assertTrue(diagrams.flowchart.contains("classDef outcomeNode"))
        assertTrue(diagrams.flowchart.contains("event_PaymentAuthorized[/PaymentAuthorized/]"))
        assertTrue(!diagrams.flowchart.contains("outcome_PaymentAuthorized[/PaymentAuthorized/]"))
    }

    @Test
    fun testTransform_OutcomeAnnotatedSequencesUseOutcomeBranchLabels() {
        val zflContent = """
            flow CheckoutFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do authorizePayment {
                    service Payments.PaymentService
                    @outcome("authorized") emits PaymentAuthorized, OrderUpdated
                    @outcome("declined") emits PaymentDeclined
                }

                end {
                    done: PaymentAuthorized, OrderUpdated, PaymentDeclined
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(
                ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)),
                MermaidSequenceRenderMode.ALT_BLOCKS
            )

        val done = diagrams.sequences.single { it.endOutcome == "done" }
        assertEquals(listOf("authorized", "declined"), done.branchLabels)
        assertTrue(done.mermaid.contains("alt authorized"), done.mermaid)
        assertTrue(done.mermaid.contains("else declined"), done.mermaid)
        assertTrue(done.mermaid.contains("PaymentService-->>PaymentService: PaymentAuthorized"), done.mermaid)
        assertTrue(done.mermaid.contains("PaymentService-->>PaymentService: OrderUpdated"), done.mermaid)
        assertTrue(done.mermaid.contains("PaymentService-->>PaymentService: PaymentDeclined"), done.mermaid)
    }

    @Test
    fun testTransform_OnEmitsOutcomesRenderInFlowchartAndSequences() {
        val zflContent = """
            flow CheckoutFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do startOrderCheckout {
                    service OrdersCheckout.OrdersCheckoutService
                    call reserveStock
                    @outcome("created") on StockReserved emits OrderCreated, EventB
                    on StockConfirmed emits OrderConfirmed
                }

                do reserveStock {
                    service CatalogProducts.CatalogProductsService
                    emits StockReserved
                    emits StockConfirmed
                }

                end {
                    done: OrderCreated, EventB, OrderConfirmed
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(
                ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)),
                MermaidSequenceRenderMode.ALT_BLOCKS
            )

        assertTrue(
            diagrams.flowchart.contains("action_startOrderCheckout -->|on StockReserved| outcome_startOrderCheckout_created"),
            diagrams.flowchart
        )
        assertTrue(diagrams.flowchart.contains("outcome_startOrderCheckout_created --> event_OrderCreated"), diagrams.flowchart)
        assertTrue(diagrams.flowchart.contains("outcome_startOrderCheckout_created --> event_EventB"), diagrams.flowchart)
        assertTrue(
            diagrams.flowchart.contains("action_startOrderCheckout -->|on StockConfirmed| outcome_startOrderCheckout_StockConfirmed"),
            diagrams.flowchart
        )

        val done = diagrams.sequences.single { it.endOutcome == "done" }
        assertTrue(done.branchLabels.contains("created"), done.branchLabels.toString())
        assertTrue(done.branchLabels.contains("StockConfirmed"), done.branchLabels.toString())
        assertTrue(done.mermaid.contains("alt created") || done.mermaid.contains("else created"), done.mermaid)
        assertTrue(done.mermaid.contains("OrderCreated"), done.mermaid)
        assertTrue(done.mermaid.contains("EventB"), done.mermaid)
    }

    @Test
    fun testTransform_PlaceOrderFlow_DefaultModeSplitsScenarioFamiliesBeforeMerging() {
        val zflContent = readTestFile("flow/place-order-flow.zfl")

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        val orderCancelled = diagrams.sequences.filter { it.endOutcome == "orderCancelled" }
        assertTrue(
            orderCancelled.size >= 2,
            orderCancelled.joinToString("\n---\n") { "${it.title}\n${it.mermaid}" }
        )
        assertTrue(
            orderCancelled.any { it.mermaid.contains("alt declined") || it.mermaid.contains("else declined") },
            orderCancelled.joinToString("\n---\n") { "${it.title}\n${it.mermaid}" }
        )
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
        assertTrue(
            orderCancelled.any { it.mermaid.contains("alt declined") || it.mermaid.contains("else declined") },
            orderCancelled.joinToString("\n---\n") { "${it.title}\n${it.mermaid}" }
        )
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

    @Test
    fun testTransform_AsyncCallRendersCommandAndEmittedContinuation() {
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
                    async call authorizePayment
                    emits PaymentAuthorized
                }

                when PaymentAuthorized do confirmOrder {
                    service Orders.OrderService
                    emits OrderConfirmed
                }

                end {
                    completed: OrderConfirmed
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        val completed = diagrams.sequences.single { it.endOutcome == "completed" }
        assertTrue(completed.mermaid.contains("PaymentService->>PaymentService: async call authorizePayment"), completed.mermaid)
        assertTrue(completed.mermaid.contains("PaymentService-->>OrderService: PaymentAuthorized"), completed.mermaid)
        assertTrue(completed.mermaid.contains("OrderService->>OrderService: confirmOrder"), completed.mermaid)
    }
}
