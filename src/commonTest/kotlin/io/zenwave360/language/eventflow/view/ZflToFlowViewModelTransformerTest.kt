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
        val endNodes = viewModel.nodes.filter { it.type == FlowNodeType.END }

        assertEquals(6, commandNodes.size, "Should have 6 command nodes")
        assertEquals(7, eventNodes.size, "Should have 7 event nodes")
        assertEquals(6, policyNodes.size, "Should have 6 policy nodes")
        assertEquals(1, endNodes.size, "Should have 1 end node")

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

        val eventToPolicy = viewModel.edges.find {
            it.source == "event:CustomerRequestsSubscriptionRenewal" &&
            it.target == "policy:CustomerRequestsSubscriptionRenewal:renewSubscription" &&
            it.type == FlowEdgeType.TRIGGER
        }
        assertNotNull(eventToPolicy, "Should have trigger edge from event to policy")

        val policyToCommand = viewModel.edges.find {
            it.source == "policy:CustomerRequestsSubscriptionRenewal:renewSubscription" &&
            it.target == "command:renewSubscription" &&
            it.type == FlowEdgeType.TRIGGER
        }
        assertNotNull(policyToCommand, "Should have trigger edge from policy to command")

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

        val completedEndEdge = viewModel.edges.find {
            it.source == "event:PaymentRecorded" &&
            it.target == "end:end" &&
            it.type == FlowEdgeType.TRIGGER &&
            it.label == "completed"
        }
        assertNotNull(completedEndEdge, "Should connect completed outcome event to the shared end node")
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
                when UserAction {
                    command doSomething
                    event SomethingDone
                }
                end {
                    completed: SomethingDone
                }
            }
        """.trimIndent()

        val viewModel = ZflToFlowViewModelTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        assertEquals(5, viewModel.nodes.size)
        assertEquals(5, viewModel.edges.size)

        val triggerEdge = viewModel.edges.find { it.type == FlowEdgeType.TRIGGER }
        assertNotNull(triggerEdge)
        assertEquals("event:UserAction", triggerEdge.source)
        assertEquals("event:UserAction", triggerEdge.target)

        val causationEdge = viewModel.edges.find { it.type == FlowEdgeType.CAUSATION }
        assertNotNull(causationEdge)
        assertEquals("command:doSomething", causationEdge.source)
        assertEquals("event:SomethingDone", causationEdge.target)

        val endEdge = viewModel.edges.find {
            it.source == "event:SomethingDone" &&
            it.target == "end:end" &&
            it.label == "completed"
        }
        assertNotNull(endEdge)
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

        val endEdges = viewModel.edges.filter { it.target == "end:end" }
        assertEquals(3, endEdges.size, "Shared end node should have one edge per end outcome event")
        assertTrue(endEdges.any { it.source == "event:OrderConfirmationSent" && it.label == "completed" })
        assertTrue(endEdges.any { it.source == "event:StockUnavailableNotificationSent" && it.label == "stockGone" })
        assertTrue(endEdges.any { it.source == "event:PaymentFailedNotificationSent" && it.label == "paymentDeclined" })
    }
}
