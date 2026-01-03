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
        assertEquals(2, policyNodes.size, "Should have 2 policy nodes")
        
        // Verify specific command nodes
        val renewCommand = commandNodes.find { it.id == "renewSubscription" }
        assertNotNull(renewCommand)
        assertEquals("renewSubscription", renewCommand.label)
        assertEquals("Subscription", renewCommand.system)
        
        val chargeCommand = commandNodes.find { it.id == "chargePayment" }
        assertNotNull(chargeCommand)
        assertEquals("chargePayment", chargeCommand.label)
        assertEquals("Payments", chargeCommand.system)
        
        // Verify specific event nodes
        val renewedEvent = eventNodes.find { it.id == "SubscriptionRenewed" }
        assertNotNull(renewedEvent)
        assertEquals("SubscriptionRenewed", renewedEvent.label)
        assertEquals("Subscription", renewedEvent.system)
        
        val failedEvent = eventNodes.find { it.id == "PaymentFailed" }
        assertNotNull(failedEvent)
        assertEquals("PaymentFailed", failedEvent.label)
        assertEquals("Payments", failedEvent.system)
        
        // Verify policy nodes
        val policy1 = policyNodes.find { it.label == "less than 3 attempts" }
        assertNotNull(policy1)
        assertEquals("Payments", policy1.system)
        
        val policy2 = policyNodes.find { it.label == "3 or more attempts" }
        assertNotNull(policy2)
        assertEquals("Subscription", policy2.system)
        
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
        // CustomerRequestsSubscriptionRenewal → renewSubscription
        val triggerEdge = flowIR.edges.find { 
            it.source == "CustomerRequestsSubscriptionRenewal" && 
            it.target == "renewSubscription" &&
            it.type == FlowEdgeType.TRIGGER
        }
        assertNotNull(triggerEdge, "Should have trigger edge from start event to command")
        
        // renewSubscription → SubscriptionRenewed
        val causationEdge = flowIR.edges.find {
            it.source == "renewSubscription" &&
            it.target == "SubscriptionRenewed" &&
            it.type == FlowEdgeType.CAUSATION
        }
        assertNotNull(causationEdge, "Should have causation edge from command to event")
        
        // PaymentFailed → policy → retryPayment (conditional)
        val policyTrigger = flowIR.edges.find {
            it.source == "PaymentFailed" &&
            it.target.startsWith("policy:") &&
            it.type == FlowEdgeType.TRIGGER
        }
        assertNotNull(policyTrigger, "Should have trigger edge to policy")
        
        val policyConditional = flowIR.edges.find {
            it.source.startsWith("policy:") &&
            it.target == "retryPayment" &&
            it.type == FlowEdgeType.CONDITIONAL
        }
        assertNotNull(policyConditional, "Should have conditional edge from policy to command")
        assertEquals("less than 3 attempts", policyConditional.label)
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
        assertEquals(2, flowIR.nodes.size)
        
        // Should have 2 edges: UserAction → doSomething, doSomething → SomethingDone
        assertEquals(2, flowIR.edges.size)
        
        val triggerEdge = flowIR.edges.find { it.type == FlowEdgeType.TRIGGER }
        assertNotNull(triggerEdge)
        assertEquals("UserAction", triggerEdge.source)
        assertEquals("doSomething", triggerEdge.target)
        
        val causationEdge = flowIR.edges.find { it.type == FlowEdgeType.CAUSATION }
        assertNotNull(causationEdge)
        assertEquals("doSomething", causationEdge.source)
        assertEquals("SomethingDone", causationEdge.target)
    }
}

