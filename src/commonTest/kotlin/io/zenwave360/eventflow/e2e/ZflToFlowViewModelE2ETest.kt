package io.zenwave360.eventflow.e2e

import io.zenwave360.language.eventflow.ir.ZflToFlowIrTransformer
import io.zenwave360.language.eventflow.view.*
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.zdl.internal.readTestFile
import kotlin.test.*

/**
 * End-to-end test that validates the complete ZFL processing pipeline:
 * ZFL file → Parser → Semantic Analyzer → IR Transformer → Layout Engine → FlowViewModel → JSON
 */
class ZflToFlowViewModelE2ETest {

    @Test
    fun testE2E_SimpleFlow() {
        // Step 1: Define a simple ZFL file content
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
            }
        """.trimIndent()

        // Step 2: Parse the ZFL file
        val parser = ZflParser()
        val zflModel = parser.parseModel(zflContent)
        assertNotNull(zflModel, "ZFL model should be parsed successfully")

        // Step 3: Perform semantic analysis
        val analyzer = ZflSemanticAnalyzer()
        val semanticModel = analyzer.analyze(zflModel)
        assertNotNull(semanticModel, "Semantic model should be created")
        assertEquals(1, semanticModel.flows.size, "Should have 1 flow")

        // Step 4: Transform to IR
        val transformer = ZflToFlowIrTransformer()
        val flowIR = transformer.transform(semanticModel)
        assertNotNull(flowIR, "FlowIR should be created")
        assertEquals(2, flowIR.nodes.size, "Should have 2 nodes (1 command + 1 event)")

        // Step 5: Apply layout to create FlowViewModel
        val layoutEngine = FlowLayoutEngine()
        val viewModel = layoutEngine.layout(flowIR)
        assertNotNull(viewModel, "FlowViewModel should be created")

        // Step 6: Convert to JSON string
        val jsonOutput = viewModel.toJsonString()
        assertNotNull(jsonOutput, "JSON output should be generated")
        assertTrue(jsonOutput.isNotEmpty(), "JSON output should not be empty")

        // Step 7: Print JSON output for inspection
        println("=== Simple Flow JSON Output ===")
        println(jsonOutput)
        println("================================")

        // Step 8: Validate JSON structure
        assertTrue(jsonOutput.contains("\"schema\""), "JSON should contain schema field")
        assertTrue(jsonOutput.contains("zfl.eventflow.view@1"), "JSON should contain schema version")
        assertTrue(jsonOutput.contains("\"nodes\""), "JSON should contain nodes array")
        assertTrue(jsonOutput.contains("\"edges\""), "JSON should contain edges array")
        assertTrue(jsonOutput.contains("\"systemGroups\""), "JSON should contain systemGroups array")
        assertTrue(jsonOutput.contains("\"layout\""), "JSON should contain layout metadata")
        assertTrue(jsonOutput.contains("\"bounds\""), "JSON should contain bounds")

        // Step 9: Validate FlowViewModel content
        assertEquals("zfl.eventflow.view@1", viewModel.schema)
        assertEquals(2, viewModel.nodes.size)
        assertEquals(2, viewModel.edges.size)
        assertTrue(viewModel.bounds.width > 0)
        assertTrue(viewModel.bounds.height > 0)
    }

    @Test
    fun testE2E_SubscriptionsFlow() {
        // Step 1: Read the subscriptions.zfl test file
        val zflContent = readTestFile("flow/subscriptions.zfl")
        assertNotNull(zflContent, "ZFL file should be read successfully")

        // Step 2: Parse the ZFL file
        val parser = ZflParser()
        val zflModel = parser.parseModel(zflContent)
        assertNotNull(zflModel, "ZFL model should be parsed successfully")

        // Step 3: Perform semantic analysis
        val analyzer = ZflSemanticAnalyzer()
        val semanticModel = analyzer.analyze(zflModel)
        assertNotNull(semanticModel, "Semantic model should be created")
        assertEquals(1, semanticModel.flows.size, "Should have 1 flow")
        assertEquals("PaymentsFlow", semanticModel.flows.first().name)

        // Step 4: Transform to IR
        val transformer = ZflToFlowIrTransformer()
        val flowIR = transformer.transform(semanticModel)
        assertNotNull(flowIR, "FlowIR should be created")
        assertEquals(16, flowIR.nodes.size, "Should have 16 nodes (7 commands + 7 events + 2 policies)")

        // Step 5: Apply layout to create FlowViewModel
        val layoutEngine = FlowLayoutEngine()
        val viewModel = layoutEngine.layout(flowIR)
        assertNotNull(viewModel, "FlowViewModel should be created")

        // Step 6: Convert to JSON string
        val jsonOutput = viewModel.toJsonString()
        assertNotNull(jsonOutput, "JSON output should be generated")
        assertTrue(jsonOutput.isNotEmpty(), "JSON output should not be empty")

        // Step 7: Print JSON output for inspection
        println("=== Subscriptions Flow JSON Output ===")
        println(jsonOutput)
        println("=======================================")

        // Step 8: Validate JSON structure and content
        assertTrue(jsonOutput.contains("\"schema\""), "JSON should contain schema field")
        assertTrue(jsonOutput.contains("zfl.eventflow.view@1"), "JSON should contain schema version")
        assertTrue(jsonOutput.contains("\"nodes\""), "JSON should contain nodes array")
        assertTrue(jsonOutput.contains("\"edges\""), "JSON should contain edges array")
        assertTrue(jsonOutput.contains("\"systemGroups\""), "JSON should contain systemGroups array")
        assertTrue(jsonOutput.contains("\"layout\""), "JSON should contain layout metadata")
        assertTrue(jsonOutput.contains("\"bounds\""), "JSON should contain bounds")

        // Step 9: Validate FlowViewModel content
        assertEquals("zfl.eventflow.view@1", viewModel.schema)
        assertEquals(16, viewModel.nodes.size)
        assertTrue(viewModel.edges.isNotEmpty())
        assertTrue(viewModel.systemGroups.isNotEmpty())
        assertEquals(3, viewModel.systemGroups.size, "Should have 3 system groups")

        // Verify system groups
        val systemNames = viewModel.systemGroups.map { it.systemName }.toSet()
        assertTrue(systemNames.contains("Subscription"), "Should have Subscription system")
        assertTrue(systemNames.contains("Payments"), "Should have Payments system")
        assertTrue(systemNames.contains("Billing"), "Should have Billing system")

        // Verify layout metadata
        assertEquals("zfl-layered", viewModel.layout.engine)
        assertTrue(viewModel.layout.rankSpacing > 0)
        assertTrue(viewModel.layout.nodeSpacing > 0)

        // Verify bounds
        assertTrue(viewModel.bounds.width > 0)
        assertTrue(viewModel.bounds.height > 0)

        // Verify all nodes have valid positions and dimensions
        viewModel.nodes.forEach { node ->
            assertTrue(node.position.x >= 0, "Node ${node.id} x should be >= 0")
            assertTrue(node.position.y >= 0, "Node ${node.id} y should be >= 0")
            assertTrue(node.dimensions.width > 0, "Node ${node.id} width should be > 0")
            assertTrue(node.dimensions.height > 0, "Node ${node.id} height should be > 0")
        }
    }

    @Test
    fun testE2E_FlowWithPolicy() {
        // Step 1: Define a ZFL file with conditional policy
        val zflContent = """
            flow PolicyFlow {
                systems {
                    PaymentSystem {
                        service PaymentService {
                            commands: processPayment, retryPayment, cancelPayment
                        }
                    }
                }

                start PaymentRequested {
                }

                when PaymentRequested {
                    command processPayment
                    event PaymentSucceeded
                    event PaymentFailed
                }

                @if("retry count < 3")
                when PaymentFailed {
                    command retryPayment
                    event PaymentRetried
                }

                @if("retry count >= 3")
                when PaymentFailed {
                    command cancelPayment
                    event PaymentCancelled
                }
            }
        """.trimIndent()

        // Step 2-5: Complete pipeline
        val parser = ZflParser()
        val zflModel = parser.parseModel(zflContent)
        val semanticModel = ZflSemanticAnalyzer().analyze(zflModel)
        val flowIR = ZflToFlowIrTransformer().transform(semanticModel)
        val viewModel = FlowLayoutEngine().layout(flowIR)

        // Step 6: Convert to JSON
        val jsonOutput = viewModel.toJsonString()

        // Step 7: Print JSON output
        println("=== Policy Flow JSON Output ===")
        println(jsonOutput)
        println("================================")

        // Step 8: Validate
        assertNotNull(jsonOutput)
        assertTrue(jsonOutput.contains("POLICY"), "JSON should contain POLICY node type")

        // Verify we have policy nodes
        val policyNodes = viewModel.nodes.filter { it.type.name == "POLICY" }
        assertEquals(2, policyNodes.size, "Should have 2 policy nodes")

        // Verify policy labels
        val policyLabels = policyNodes.map { it.label }.toSet()
        assertTrue(policyLabels.contains("retry count < 3"), "Should have first policy")
        assertTrue(policyLabels.contains("retry count >= 3"), "Should have second policy")
    }

    @Test
    fun testE2E_JsonOutput() {
        // Test JSON output format
        val zflContent = """
            flow JsonTestFlow {
                systems {
                    TestSystem {
                        service TestService {
                            commands: testCommand
                        }
                    }
                }

                start TestEvent {
                }

                when TestEvent {
                    command testCommand
                    event ResultEvent
                }
            }
        """.trimIndent()

        // Create FlowViewModel through the pipeline
        val viewModel = ZflParser().parseModel(zflContent)
            .let { ZflSemanticAnalyzer().analyze(it) }
            .let { ZflToFlowIrTransformer().transform(it) }
            .let { FlowLayoutEngine().layout(it) }

        // Convert to JSON
        val jsonString = viewModel.toJsonString()

        // Print for inspection
        println("=== JSON Output Test ===")
        println(jsonString)
        println("========================")

        // Verify JSON structure
        assertTrue(jsonString.contains("\"schema\""))
        assertTrue(jsonString.contains("\"nodes\""))
        assertTrue(jsonString.contains("\"edges\""))
        assertTrue(jsonString.contains("\"systemGroups\""))
        assertTrue(jsonString.contains("\"layout\""))
        assertTrue(jsonString.contains("\"bounds\""))
    }
}


