package io.zenwave360.eventflow.ir

import io.zenwave360.language.eventflow.ir.*
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.zdl.internal.readTestFile
import kotlin.test.*

class ZflToFlowIrTransformerTest {

    @Test
    fun testTransform_SubscriptionsFlow() {
        // Parse and analyze
        val content = readTestFile("flow/subscriptions.zfl")
        val model = ZflParser().parseModel(content)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        
        // Transform to IR
        val transformer = ZflToFlowIrTransformer()
        val flowIR = transformer.transform(semanticModel)
        
        // Verify nodes
        assertNotNull(flowIR.nodes)
        assertTrue(flowIR.nodes.isNotEmpty(), "Should have nodes")
        
        // Count node types
        val commandNodes = flowIR.nodes.filter { it.type == FlowNodeType.COMMAND }
        val eventNodes = flowIR.nodes.filter { it.type == FlowNodeType.EVENT }
        val policyNodes = flowIR.nodes.filter { it.type == FlowNodeType.POLICY }
        
        assertEquals(7, commandNodes.size, "Should have 7 command nodes")
        assertEquals(7, eventNodes.size, "Should have 7 event nodes")
        assertEquals(6, policyNodes.size, "Should have 6 policy nodes")
        
        // Verify specific command nodes
        val renewCommand = commandNodes.find { it.id == "command:renewSubscription" }
        assertNotNull(renewCommand)
        assertEquals("renewSubscription", renewCommand.label)
        assertEquals("Subscription", renewCommand.system)
        
        val chargeCommand = commandNodes.find { it.id == "command:chargePayment" }
        assertNotNull(chargeCommand)
        assertEquals("chargePayment", chargeCommand.label)
        assertEquals("Payments", chargeCommand.system)
        
        // Verify specific event nodes
        val renewedEvent = eventNodes.find { it.id == "event:SubscriptionRenewed" }
        assertNotNull(renewedEvent)
        assertEquals("SubscriptionRenewed", renewedEvent.label)
        assertEquals("Subscription", renewedEvent.system)
        
        val failedEvent = eventNodes.find { it.id == "event:PaymentFailed" }
        assertNotNull(failedEvent)
        assertEquals("PaymentFailed", failedEvent.label)
        assertEquals("Payments", failedEvent.system)
        
        // Verify policy nodes
        val policy1 = policyNodes.find { it.id == "policy:PaymentFailed:retryPayment" }
        assertNotNull(policy1)
        assertTrue(policy1.label.contains("less than 3 attempts"))
        
        val policy2 = policyNodes.find { it.id == "policy:PaymentFailed:suspendSubscription" }
        assertNotNull(policy2)
        assertTrue(policy2.label.contains("3 or more attempts"))
        
        // Verify edges
        assertNotNull(flowIR.edges)
        assertTrue(flowIR.edges.isNotEmpty(), "Should have edges")
        
        // Count edge types
        val causationEdges = flowIR.edges.filter { it.type == FlowEdgeType.CAUSATION }
        val triggerEdges = flowIR.edges.filter { it.type == FlowEdgeType.TRIGGER }
        val conditionalEdges = flowIR.edges.filter { it.type == FlowEdgeType.CONDITIONAL }
        
        assertTrue(causationEdges.isNotEmpty(), "Should have causation edges")
        assertTrue(triggerEdges.isNotEmpty(), "Should have trigger edges")
        assertTrue(conditionalEdges.isNotEmpty(), "Should have conditional edges")
        
        // Verify specific edges
        // start:CustomerRequestsSubscriptionRenewal → event:CustomerRequestsSubscriptionRenewal
        val startToEvent = flowIR.edges.find {
            it.source == "start:CustomerRequestsSubscriptionRenewal" &&
            it.target == "event:CustomerRequestsSubscriptionRenewal" &&
            it.type == FlowEdgeType.TRIGGER
        }
        assertNotNull(startToEvent, "Should have trigger edge from start to event")

        // event:CustomerRequestsSubscriptionRenewal → policy → command:renewSubscription
        val eventToPolicy = flowIR.edges.find {
            it.source == "event:CustomerRequestsSubscriptionRenewal" &&
            it.target == "policy:CustomerRequestsSubscriptionRenewal:renewSubscription" &&
            it.type == FlowEdgeType.TRIGGER
        }
        assertNotNull(eventToPolicy, "Should have trigger edge from event to policy")

        val policyToCommand = flowIR.edges.find {
            it.source == "policy:CustomerRequestsSubscriptionRenewal:renewSubscription" &&
            it.target == "command:renewSubscription" &&
            it.type == FlowEdgeType.TRIGGER
        }
        assertNotNull(policyToCommand, "Should have trigger edge from policy to command")

        // command:renewSubscription → event:SubscriptionRenewed
        val causationEdge = flowIR.edges.find {
            it.source == "command:renewSubscription" &&
            it.target == "event:SubscriptionRenewed" &&
            it.type == FlowEdgeType.CAUSATION
        }
        assertNotNull(causationEdge, "Should have causation edge from command to event")

        // event:PaymentFailed → policy → command:retryPayment (conditional)
        val eventToPolicyConditional = flowIR.edges.find {
            it.source == "event:PaymentFailed" &&
            it.target == "policy:PaymentFailed:retryPayment" &&
            it.type == FlowEdgeType.CONDITIONAL
        }
        assertNotNull(eventToPolicyConditional, "Should have conditional edge from event to policy")

        val policyToCommandConditional = flowIR.edges.find {
            it.source == "policy:PaymentFailed:retryPayment" &&
            it.target == "command:retryPayment" &&
            it.type == FlowEdgeType.CONDITIONAL
        }
        assertNotNull(policyToCommandConditional, "Should have conditional edge from policy to command")
    }
    
    @Test
    fun testTransform_EmptyModel() {
        val model = ZflParser().parseModel("")
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        val transformer = ZflToFlowIrTransformer()
        val flowIR = transformer.transform(semanticModel)
        
        assertEquals(0, flowIR.nodes.size)
        assertEquals(0, flowIR.edges.size)
    }
    
    @Test
    fun testTransform_SimpleFlow() {
        val zflContent = """
            flow SimpleFlow {
                systems {
                    TestSystem {
                        service TestService {
                            commands: doSomething
                        }
                    }
                }
                
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
        
        val model = ZflParser().parseModel(zflContent)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        val transformer = ZflToFlowIrTransformer()
        val flowIR = transformer.transform(semanticModel)
        
        // Should have 3 nodes: 1 command, 2 events (UserAction + SomethingDone)
        assertEquals(4, flowIR.nodes.size)
        
        // Should have 2 edges: UserAction → doSomething, doSomething → SomethingDone
        assertEquals(4, flowIR.edges.size)
        
        val triggerEdge = flowIR.edges.find { it.type == FlowEdgeType.TRIGGER }
        assertNotNull(triggerEdge)
        assertEquals("start:UserAction", triggerEdge.source)
        assertEquals("event:UserAction", triggerEdge.target)
        
        val causationEdge = flowIR.edges.find { it.type == FlowEdgeType.CAUSATION }
        assertNotNull(causationEdge)
        assertEquals("command:doSomething", causationEdge.source)
        assertEquals("event:SomethingDone", causationEdge.target)
    }
}

