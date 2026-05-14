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
}
