package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ZflToFlowGraphTransformerTest {

    @Test
    fun testTransform_ActorStartCollapsesPolicyNode() {
        val graph = ZflToFlowGraphTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(actorStartFlow())))

        assertNull(graph.nodes.find { it.id == "policy:MyStart:oneCommand" })
        assertNotNull(
            graph.edges.find {
                it.source == "event:MyStart" &&
                    it.target == "command:oneCommand" &&
                    it.type == FlowGraphEdgeType.TRIGGER
            }
        )
    }

    @Test
    fun testTransform_TimedStartKeepsPolicyNode() {
        val graph = ZflToFlowGraphTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(timedStartFlow())))

        assertNotNull(graph.nodes.find { it.id == "policy:Scheduled:oneCommand" })
        assertNotNull(
            graph.edges.find {
                it.source == "event:Scheduled" &&
                    it.target == "policy:Scheduled:oneCommand" &&
                    it.type == FlowGraphEdgeType.TRIGGER
            }
        )
    }

    @Test
    fun testTransform_DirectCallProducesCallEdge() {
        val graph = ZflToFlowGraphTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(callFlow())))

        val callEdge = graph.edges.find {
            it.source == "command:startOrderCheckout" &&
                it.target == "command:reserveStock" &&
                it.type == FlowGraphEdgeType.CALL
        }
        assertNotNull(callEdge)
    }

    @Test
    fun testTransform_AsyncCallProducesLabelledCallEdge() {
        val graph = ZflToFlowGraphTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(asyncCallFlow())))

        val callEdge = graph.edges.find {
            it.source == "command:authorizePayment" &&
                it.target == "command:authorizePayment" &&
                it.type == FlowGraphEdgeType.CALL
        }
        assertNotNull(callEdge)
        assertEquals("async", callEdge.label)
        assertNotNull(graph.nodes.find { it.id == "event:PaymentAuthorized" })
    }

    @Test
    fun testTransform_OutcomeHandlersProduceRoutingEdges() {
        val graph = ZflToFlowGraphTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(callFlow())))

        assertNotNull(graph.nodes.find { it.id == "event:OrderCreated" })
        assertNotNull(graph.nodes.find { it.id == "event:StockUnavailable" })
        assertNotNull(
            graph.edges.find {
                it.source == "command:startOrderCheckout" &&
                    it.target == "event:OrderCreated" &&
                    it.type == FlowGraphEdgeType.OUTCOME_HANDLER &&
                    it.label == "on StockReserved"
            }
        )
        assertNotNull(
            graph.edges.find {
                it.source == "command:startOrderCheckout" &&
                    it.target == "event:StockUnavailable" &&
                    it.type == FlowGraphEdgeType.OUTCOME_HANDLER
            }
        )
    }

    @Test
    fun testTransform_EndOutcomesAttachToOutcomeNodes() {
        val graph = ZflToFlowGraphTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(callFlow())))

        val completed = graph.nodes.find { it.id == "event:OrderCreated" }
        val stockGone = graph.nodes.find { it.id == "event:StockUnavailable" }
        assertEquals(listOf("completed"), completed?.endOutcomeLabels)
        assertEquals(listOf("stockGone"), stockGone?.endOutcomeLabels)
    }

    @Test
    fun testTransform_ResponseOnlyOutcomesDoNotCreateOutcomeNodes() {
        val graph = ZflToFlowGraphTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(responseCallFlow())))

        assertNull(graph.nodes.find { it.id == "event:StockUnavailable" })
        assertNotNull(
            graph.edges.find {
                it.source == "command:startOrderCheckout" &&
                    it.target == "event:OrderRejected" &&
                    it.type == FlowGraphEdgeType.OUTCOME_HANDLER &&
                    it.label == "on StockUnavailable"
            }
        )
    }

    @Test
    fun testTransform_EmitsResponseCreatesPublishedOutcomeNodeAndCausationEdge() {
        val graph = ZflToFlowGraphTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(emitsResponseCallFlow())))

        assertNotNull(graph.nodes.find { it.id == "event:StockReserved" })
        assertNotNull(
            graph.edges.find {
                it.source == "command:reserveStock" &&
                    it.target == "event:StockReserved" &&
                    it.type == FlowGraphEdgeType.CAUSATION
            }
        )
        assertNotNull(
            graph.edges.find {
                it.source == "command:startOrderCheckout" &&
                    it.target == "event:OrderCreated" &&
                    it.type == FlowGraphEdgeType.OUTCOME_HANDLER &&
                    it.label == "on StockReserved"
            }
        )
    }

    @Test
    fun testTransform_CalledCommandDirectEmitsRemainConnected() {
        val graph = ZflToFlowGraphTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(calledCommandEmitsFlow())))

        assertNotNull(graph.nodes.find { it.id == "command:confirmStockReservation" })
        assertNotNull(graph.nodes.find { it.id == "event:StockReservationConfirmed" })
        assertNotNull(
            graph.edges.find {
                it.source == "command:confirmOrder" &&
                    it.target == "command:confirmStockReservation" &&
                    it.type == FlowGraphEdgeType.CALL
            }
        )
        assertNotNull(
            graph.edges.find {
                it.source == "command:confirmStockReservation" &&
                    it.target == "event:StockReservationConfirmed" &&
                    it.type == FlowGraphEdgeType.CAUSATION
            }
        )
    }

    @Test
    fun testTransform_OutcomeAnnotatedEmitsSetCausationEdgeOutcome() {
        val graph = ZflToFlowGraphTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(outcomeAnnotatedEmitsFlow())))

        val authorizedEdge = graph.edges.single {
            it.source == "command:authorizePayment" &&
                it.target == "event:PaymentAuthorized" &&
                it.type == FlowGraphEdgeType.CAUSATION
        }
        val updatedEdge = graph.edges.single {
            it.source == "command:authorizePayment" &&
                it.target == "event:OrderUpdated" &&
                it.type == FlowGraphEdgeType.CAUSATION
        }
        assertEquals("authorized", authorizedEdge.outcome)
        assertEquals("authorized", updatedEdge.outcome)
        assertNull(graph.nodes.find { it.id == "outcome:authorized" })
    }

    @Test
    fun testTransform_OnEmitsMultipleEventsSetsOutcomeHandlerEdgeOutcome() {
        val graph = ZflToFlowGraphTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(onEmitsOutcomeFlow())))

        val createdEdge = graph.edges.single {
            it.source == "command:startOrderCheckout" &&
                it.target == "event:OrderCreated" &&
                it.type == FlowGraphEdgeType.OUTCOME_HANDLER
        }
        val eventBEdge = graph.edges.single {
            it.source == "command:startOrderCheckout" &&
                it.target == "event:EventB" &&
                it.type == FlowGraphEdgeType.OUTCOME_HANDLER
        }
        val confirmedEdge = graph.edges.single {
            it.source == "command:startOrderCheckout" &&
                it.target == "event:OrderConfirmed" &&
                it.type == FlowGraphEdgeType.OUTCOME_HANDLER
        }

        assertEquals("created", createdEdge.outcome)
        assertEquals("created", eventBEdge.outcome)
        assertEquals("StockConfirmed", confirmedEdge.outcome)
    }

    private fun actorStartFlow() = """
        flow ActorStartFlow {
            @actor(Customer)
            start MyStart {
                id String
            }

            when MyStart do oneCommand {
                service OrdersCheckout.OrdersCheckoutService
                emits Done
            }

            end {
                completed: Done
            }
        }
    """.trimIndent()

    private fun timedStartFlow() = """
        flow TimedStartFlow {
            @time("every day at 09:00")
            start Scheduled {
            }

            when Scheduled do oneCommand {
                service OrdersCheckout.OrdersCheckoutService
                emits Done
            }

            end {
                completed: Done
            }
        }
    """.trimIndent()

    private fun callFlow() = """
        flow CallFlow {
            @actor(Customer)
            start StartOrderCheckout {
            }

            when StartOrderCheckout do startOrderCheckout {
                service OrdersCheckout.OrdersCheckoutService
                call reserveStock
                on StockReserved emits OrderCreated
                on StockUnavailable emits StockUnavailable
                emits OrderCreated
                emits StockUnavailable
            }

            do reserveStock {
                service CatalogProducts.CatalogProductsService
                emits StockReserved
                emits StockUnavailable
            }

            end {
                completed: OrderCreated
                stockGone: StockUnavailable
            }
        }
    """.trimIndent()

    private fun responseCallFlow() = """
        flow CallFlow {
            when StartOrderCheckout do startOrderCheckout

            do startOrderCheckout {
                service OrdersCheckout.OrdersCheckoutService
                call reserveStock
                on StockUnavailable emits OrderRejected
                emits OrderRejected
            }

            do reserveStock {
                service CatalogProducts.CatalogProductsService
                response StockUnavailable
            }

            end {
                completed: OrderRejected
            }
        }
    """.trimIndent()

    private fun emitsResponseCallFlow() = """
        flow CallFlow {
            when StartOrderCheckout do startOrderCheckout

            do startOrderCheckout {
                service OrdersCheckout.OrdersCheckoutService
                call reserveStock
                on StockReserved emits OrderCreated
                on StockUnavailable emits StockUnavailable
                emits OrderCreated
                emits StockUnavailable
            }

            do reserveStock {
                service CatalogProducts.CatalogProductsService
                emits response StockReserved
                response StockUnavailable
            }

            end {
                completed: OrderCreated
                stockGone: StockUnavailable
            }
        }
    """.trimIndent()

    private fun calledCommandEmitsFlow() = """
        flow CalledCommandEmitsFlow {
            when PaymentAuthorized do confirmOrder

            do confirmOrder {
                service OrdersCheckout.OrdersCheckoutService
                call confirmStockReservation
                emits OrderConfirmed
            }

            do confirmStockReservation {
                service CatalogInventory.InventoryService
                emits StockReservationConfirmed
            }

            end {
                completed: OrderConfirmed
            }
        }
    """.trimIndent()

    private fun asyncCallFlow() = """
        flow AsyncCallFlow {
            when OrderCreated do authorizePayment {
                service PaymentsProcessing.PaymentsProcessingService
                async call authorizePayment
                emits PaymentAuthorized
            }

            when PaymentAuthorized do confirmOrder {
                service OrdersCheckout.OrdersCheckoutService
                emits OrderConfirmed
            }

            end {
                completed: OrderConfirmed
            }
        }
    """.trimIndent()

    private fun outcomeAnnotatedEmitsFlow() = """
        flow PaymentsFlow {
            do authorizePayment {
                service PaymentsProcessing.PaymentsProcessingService
                @outcome("authorized") emits PaymentAuthorized, OrderUpdated
                @outcome("declined") emits PaymentDeclined
            }

            end {
                completed: PaymentAuthorized
            }
        }
    """.trimIndent()

    private fun onEmitsOutcomeFlow() = """
        flow CheckoutFlow {
            do startOrderCheckout {
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
                completed: OrderCreated, EventB, OrderConfirmed
            }
        }
    """.trimIndent()
}
