package io.zenwave360.language.eventflow.view

import io.zenwave360.language.eventflow.view.*
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.language.readTestFile
import kotlin.test.*

class ZflToFlowViewModelTransformerTest {

    @Test
    fun testTransform_SubscriptionsFlow() {
        val content = readTestFile("flow/subscriptions.zfl")
        val model = ZflParser().parseModel(content)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)

        val viewModel = ZflToFlowViewModelTransformer().transform(semanticModel)

        assertNotNull(viewModel.nodes)
        assertTrue(viewModel.nodes.isNotEmpty(), "Should have nodes")

        val commandNodes = viewModel.nodes.filter { it.type == FlowNodeType.COMMAND }
        val eventNodes = viewModel.nodes.filter { it.type == FlowNodeType.EVENT }
        val policyNodes = viewModel.nodes.filter { it.type == FlowNodeType.POLICY }
        assertEquals(6, commandNodes.size, "Should have 6 command nodes")
        assertEquals(7, eventNodes.size, "Should have 7 event nodes")
        assertEquals(5, policyNodes.size, "Actor starts should connect directly to the first command")

        val renewCommand = commandNodes.find { it.id == "command:renewSubscription" }
        assertNotNull(renewCommand)
        assertEquals("renewSubscription", renewCommand.label)
        assertEquals("Subscription", renewCommand.system)

        val chargeCommand = commandNodes.find { it.id == "command:chargePayment" }
        assertNotNull(chargeCommand)
        assertEquals("chargePayment", chargeCommand.label)
        assertEquals("Payments", chargeCommand.system)

        val renewedEvent = eventNodes.find { it.id == "event:SubscriptionRenewed" }
        assertNotNull(renewedEvent)
        assertEquals("SubscriptionRenewed", renewedEvent.label)
        assertEquals("Subscription", renewedEvent.system)

        val failedEvent = eventNodes.find { it.id == "event:PaymentFailed" }
        assertNotNull(failedEvent)
        assertEquals("PaymentFailed", failedEvent.label)
        assertEquals("Payments", failedEvent.system)

        val policy1 = policyNodes.find { it.id == "policy:PaymentFailed:retryPayment" }
        assertNotNull(policy1)
        assertTrue(policy1.label.contains("less than 3 attempts"))

        val policy2 = policyNodes.find { it.id == "policy:PaymentFailed:suspendSubscription" }
        assertNotNull(policy2)
        assertTrue(policy2.label.contains("3 or more attempts"))

        assertNotNull(viewModel.edges)
        assertTrue(viewModel.edges.isNotEmpty(), "Should have edges")

        val causationEdges = viewModel.edges.filter { it.type == FlowEdgeType.CAUSATION }
        val triggerEdges = viewModel.edges.filter { it.type == FlowEdgeType.TRIGGER }
        val conditionalEdges = viewModel.edges.filter { it.type == FlowEdgeType.CONDITIONAL }

        assertTrue(causationEdges.isNotEmpty(), "Should have causation edges")
        assertTrue(triggerEdges.isNotEmpty(), "Should have trigger edges")
        assertTrue(conditionalEdges.isNotEmpty(), "Should have conditional edges")

        val startToEvent = viewModel.edges.find {
            it.source == "event:CustomerRequestsSubscriptionRenewal" &&
            it.target == "event:CustomerRequestsSubscriptionRenewal" &&
            it.type == FlowEdgeType.TRIGGER
        }
        assertNotNull(startToEvent, "Should have trigger edge from start to event")

        val actorStartToCommand = viewModel.edges.find {
            it.source == "event:CustomerRequestsSubscriptionRenewal" &&
            it.target == "command:renewSubscription" &&
            it.type == FlowEdgeType.TRIGGER
        }
        assertNotNull(actorStartToCommand, "Actor start should connect directly to the first command")

        assertNull(
            viewModel.nodes.find { it.id == "policy:CustomerRequestsSubscriptionRenewal:renewSubscription" },
            "Actor starts should not render an intermediate policy node"
        )

        val causationEdge = viewModel.edges.find {
            it.source == "command:renewSubscription" &&
            it.target == "event:SubscriptionRenewed" &&
            it.type == FlowEdgeType.CAUSATION
        }
        assertNotNull(causationEdge, "Should have causation edge from command to event")

        val eventToPolicyConditional = viewModel.edges.find {
            it.source == "event:PaymentFailed" &&
            it.target == "policy:PaymentFailed:retryPayment" &&
            it.type == FlowEdgeType.CONDITIONAL
        }
        assertNotNull(eventToPolicyConditional, "Should have conditional edge from event to policy")

        val policyToCommandConditional = viewModel.edges.find {
            it.source == "policy:PaymentFailed:retryPayment" &&
            it.target == "command:retryPayment" &&
            it.type == FlowEdgeType.CONDITIONAL
        }
        assertNotNull(policyToCommandConditional, "Should have conditional edge from policy to command")

        val completedEvent = viewModel.nodes.find { it.id == "event:PaymentRecorded" }
        assertNotNull(completedEvent)
        assertEquals(listOf("completed"), completedEvent.endOutcomeLabels)
    }



    @Test
    fun testTransform_EmptyModel() {
        val viewModel = ZflToFlowViewModelTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel("")))

        assertEquals(0, viewModel.nodes.size)
        assertEquals(0, viewModel.edges.size)
    }

    @Test
    fun testTransform_SimpleFlow() {
        val zflContent = """
            systems {
                TestSystem {
                    service TestService {
                        commands: doSomething
                    }
                }
            }
            flow SimpleFlow {
                start UserAction {
                }
                when UserAction do doSomething {
                    emits SomethingDone
                }
                end {
                    completed: SomethingDone
                }
            }
        """.trimIndent()

        val viewModel = ZflToFlowViewModelTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        assertEquals(4, viewModel.nodes.size)
        assertEquals(4, viewModel.edges.size)

        val triggerEdge = viewModel.edges.find { it.type == FlowEdgeType.TRIGGER }
        assertNotNull(triggerEdge)
        assertEquals("event:UserAction", triggerEdge.source)
        assertEquals("event:UserAction", triggerEdge.target)

        val causationEdge = viewModel.edges.find { it.type == FlowEdgeType.CAUSATION }
        assertNotNull(causationEdge)
        assertEquals("command:doSomething", causationEdge.source)
        assertEquals("event:SomethingDone", causationEdge.target)

        val doneEvent = viewModel.nodes.find { it.id == "event:SomethingDone" }
        assertNotNull(doneEvent)
        assertEquals(listOf("completed"), doneEvent.endOutcomeLabels)
    }

    @Test
    fun testTransform_ActorStartCollapsesPolicyNode() {
        val zflContent = """
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

        val viewModel = ZflToFlowViewModelTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        assertNull(viewModel.nodes.find { it.id == "policy:MyStart:oneCommand" })
        assertNotNull(
            viewModel.edges.find {
                it.source == "event:MyStart" &&
                    it.target == "command:oneCommand" &&
                    it.type == FlowEdgeType.TRIGGER
            }
        )
    }

    @Test
    fun testTransform_TimedStartKeepsPolicyNode() {
        val zflContent = """
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

        val viewModel = ZflToFlowViewModelTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        assertNotNull(viewModel.nodes.find { it.id == "policy:Scheduled:oneCommand" })
        assertNotNull(
            viewModel.edges.find {
                it.source == "event:Scheduled" &&
                    it.target == "policy:Scheduled:oneCommand" &&
                    it.type == FlowEdgeType.TRIGGER
            }
        )
    }

    @Test
    fun testTransform_PlaceOrderFlow_DeduplicatesSharedCausationAndConnectsEndOutcomes() {
        val content = readTestFile("flow/place-order-flow.zfl")
        val model = ZflParser().parseModel(content)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)

        val viewModel = ZflToFlowViewModelTransformer().transform(semanticModel)

        val releaseStockEdges = viewModel.edges.filter {
            it.source == "command:releaseStock" &&
            it.target == "event:StockReleased" &&
            it.type == FlowEdgeType.CAUSATION
        }
        assertEquals(1, releaseStockEdges.size, "Shared command causation should be deduplicated")

        val orderConfirmationSent = viewModel.nodes.find { it.id == "event:OrderConfirmationSent" }
        val stockUnavailableNotificationSent = viewModel.nodes.find { it.id == "event:StockUnavailableNotificationSent" }
        val orderCancelledNotificationSent = viewModel.nodes.find { it.id == "event:OrderCancelledNotificationSent" }
        assertEquals(listOf("completed"), orderConfirmationSent?.endOutcomeLabels)
        assertEquals(listOf("stockGone"), stockUnavailableNotificationSent?.endOutcomeLabels)
        assertEquals(listOf("orderCancelled"), orderCancelledNotificationSent?.endOutcomeLabels)
    }

    @Test
    fun testTransform_PlaceOrderFlow_EmitsResponseKeepsPublishedOutcomeConnectedToCommand() {
        val content = readTestFile("flow/place-order-flow.zfl")
        val model = ZflParser().parseModel(content)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)

        val viewModel = ZflToFlowViewModelTransformer().transform(semanticModel)

        assertNotNull(
            viewModel.edges.find {
                it.source == "command:reserveStock" &&
                    it.target == "event:StockReserved" &&
                    it.type == FlowEdgeType.CAUSATION
            }
        )
        assertNotNull(
            viewModel.edges.find {
                it.source == "command:startOrderCheckout" &&
                    it.target == "event:OrderCreated" &&
                    it.type == FlowEdgeType.OUTCOME_HANDLER &&
                    it.label == "on StockReserved"
            }
        )
    }

    @Test
    fun testTransform_DirectCallProducesCallEdge() {
        val zflContent = """
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
                    emits StockReserved
                    emits StockUnavailable
                }

                end {
                    completed: OrderCreated
                    stockGone: StockUnavailable
                }
            }
        """.trimIndent()

        val viewModel = ZflToFlowViewModelTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        val callEdge = viewModel.edges.find {
            it.source == "command:startOrderCheckout" &&
                it.target == "command:reserveStock" &&
                it.type == FlowEdgeType.CALL
        }
        assertNotNull(callEdge)
    }

    @Test
    fun testTransform_OutcomeHandlersProduceOutcomeHandlerEdges() {
        val zflContent = """
            flow CallFlow {
                when StartOrderCheckout do startOrderCheckout

                do startOrderCheckout {
                    service OrdersCheckout.OrdersCheckoutService
                    call reserveStock
                    on StockReserved call createOrder
                    emits OrderCreated
                }

                do reserveStock {
                    service CatalogProducts.CatalogProductsService
                    emits StockReserved
                }

                do createOrder {
                    service OrdersCheckout.OrdersCheckoutService
                    emits OrderCreated
                }

                end {
                    completed: OrderCreated
                }
            }
        """.trimIndent()

        val viewModel = ZflToFlowViewModelTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        assertNotNull(
            viewModel.edges.find {
                it.source == "command:startOrderCheckout" &&
                    it.target == "command:createOrder" &&
                    it.type == FlowEdgeType.OUTCOME_HANDLER &&
                    it.label == "on StockReserved"
            }
        )
    }

    @Test
    fun testTransform_ResponseOnlyOutcomesDoNotCreateEventNodes() {
        val zflContent = """
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

        val viewModel = ZflToFlowViewModelTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        assertNull(viewModel.nodes.find { it.id == "event:StockUnavailable" })
        assertNotNull(
            viewModel.edges.find {
                it.source == "command:startOrderCheckout" &&
                    it.target == "event:OrderRejected" &&
                    it.type == FlowEdgeType.OUTCOME_HANDLER &&
                    it.label == "on StockUnavailable"
            }
        )
    }
}
