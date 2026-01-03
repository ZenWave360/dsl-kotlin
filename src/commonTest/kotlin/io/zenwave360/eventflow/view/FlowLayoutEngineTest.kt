package io.zenwave360.eventflow.view

import io.zenwave360.language.eventflow.ir.*
import io.zenwave360.language.eventflow.view.*
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.zdl.internal.readTestFile
import kotlin.test.*

class FlowLayoutEngineTest {

    @Test
    fun testLayout_EmptyFlow() {
        val flowIR = FlowIR(nodes = emptyList(), edges = emptyList())
        val layoutEngine = FlowLayoutEngine()
        val viewModel = layoutEngine.layout(flowIR)

        assertEquals(0, viewModel.nodes.size)
        assertEquals(0, viewModel.edges.size)
        assertEquals(0, viewModel.systemGroups.size)
        assertEquals(FlowBounds(0.0, 0.0, 0.0, 0.0), viewModel.bounds)
    }

    @Test
    fun testLayout_SimpleFlow() {
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

        val model = ZflParser().parseModel(zflContent)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        val transformer = ZflToFlowIrTransformer()
        val flowIR = transformer.transform(semanticModel)

        val layoutEngine = FlowLayoutEngine()
        val viewModel = layoutEngine.layout(flowIR)

        // Verify nodes are created
        assertEquals(2, viewModel.nodes.size, "Should have 2 nodes")

        // Verify all nodes have positions
        viewModel.nodes.forEach { node ->
            assertNotNull(node.position, "Node ${node.id} should have a position")
            assertTrue(node.position.x >= 0, "Node ${node.id} x position should be >= 0")
            assertTrue(node.position.y >= 0, "Node ${node.id} y position should be >= 0")
        }

        // Verify all nodes have dimensions
        viewModel.nodes.forEach { node ->
            assertNotNull(node.dimensions, "Node ${node.id} should have dimensions")
            assertTrue(node.dimensions.width > 0, "Node ${node.id} width should be > 0")
            assertTrue(node.dimensions.height > 0, "Node ${node.id} height should be > 0")
        }

        // Verify edges are created
        assertEquals(2, viewModel.edges.size, "Should have 2 edges")

        // Verify bounds are calculated
        assertTrue(viewModel.bounds.width > 0, "Bounds width should be > 0")
        assertTrue(viewModel.bounds.height > 0, "Bounds height should be > 0")
    }

    @Test
    fun testLayout_NodeDimensions() {
        val zflContent = """
            flow TestFlow {
                systems {
                    TestSystem {
                        service TestService {
                            commands: testCommand
                        }
                    }
                }
                
                start TestEvent {
                }
                
                @if("condition")
                when TestEvent {
                    command testCommand
                    event ResultEvent
                }
            }
        """.trimIndent()

        val model = ZflParser().parseModel(zflContent)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        val transformer = ZflToFlowIrTransformer()
        val flowIR = transformer.transform(semanticModel)

        val layoutEngine = FlowLayoutEngine()
        val viewModel = layoutEngine.layout(flowIR)

        // Find nodes by type
        val commandNode = viewModel.nodes.find { it.type == FlowNodeType.COMMAND }
        val eventNode = viewModel.nodes.find { it.type == FlowNodeType.EVENT }
        val policyNode = viewModel.nodes.find { it.type == FlowNodeType.POLICY }

        // Verify COMMAND dimensions
        assertNotNull(commandNode, "Should have a command node")
        assertEquals(180.0, commandNode.dimensions.width, "Command width should be 180")
        assertEquals(56.0, commandNode.dimensions.height, "Command height should be 56")

        // Verify EVENT dimensions
        assertNotNull(eventNode, "Should have an event node")
        assertEquals(160.0, eventNode.dimensions.width, "Event width should be 160")
        assertEquals(48.0, eventNode.dimensions.height, "Event height should be 48")

        // Verify POLICY dimensions
        assertNotNull(policyNode, "Should have a policy node")
        assertEquals(220.0, policyNode.dimensions.width, "Policy width should be 220")
        assertEquals(64.0, policyNode.dimensions.height, "Policy height should be 64")
    }

    @Test
    fun testLayout_SubscriptionsFlow() {
        // Parse and analyze
        val content = readTestFile("flow/subscriptions.zfl")
        val model = ZflParser().parseModel(content)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)

        // Transform to IR
        val transformer = ZflToFlowIrTransformer()
        val flowIR = transformer.transform(semanticModel)

        // Layout
        val layoutEngine = FlowLayoutEngine()
        val viewModel = layoutEngine.layout(flowIR)

        // Verify nodes
        assertEquals(16, viewModel.nodes.size, "Should have 16 nodes (7 commands + 7 events + 2 policies)")

        // Verify all nodes have valid positions and dimensions
        viewModel.nodes.forEach { node ->
            assertTrue(node.position.x >= 0, "Node ${node.id} x should be >= 0")
            assertTrue(node.position.y >= 0, "Node ${node.id} y should be >= 0")
            assertTrue(node.dimensions.width > 0, "Node ${node.id} width should be > 0")
            assertTrue(node.dimensions.height > 0, "Node ${node.id} height should be > 0")
        }

        // Verify edges
        assertTrue(viewModel.edges.isNotEmpty(), "Should have edges")

        // Verify system groups
        assertTrue(viewModel.systemGroups.isNotEmpty(), "Should have system groups")
        val systemNames = viewModel.systemGroups.map { it.systemName }.toSet()
        assertTrue(systemNames.contains("Subscription"), "Should have Subscription system group")
        assertTrue(systemNames.contains("Payments"), "Should have Payments system group")
        assertTrue(systemNames.contains("Billing"), "Should have Billing system group")

        // Verify bounds
        assertTrue(viewModel.bounds.width > 0, "Bounds width should be > 0")
        assertTrue(viewModel.bounds.height > 0, "Bounds height should be > 0")
    }

    @Test
    fun testLayout_StableOrdering() {
        // Run layout multiple times and verify the order is stable
        val zflContent = """
            flow TestFlow {
                systems {
                    TestSystem {
                        service TestService {
                            commands: cmd1, cmd2, cmd3
                        }
                    }
                }

                start Event1 {
                }

                when Event1 {
                    command cmd1
                    event Event2
                }

                when Event2 {
                    command cmd2
                    event Event3
                }

                when Event3 {
                    command cmd3
                    event Event4
                }
            }
        """.trimIndent()

        val model = ZflParser().parseModel(zflContent)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        val transformer = ZflToFlowIrTransformer()
        val flowIR = transformer.transform(semanticModel)

        val layoutEngine = FlowLayoutEngine()

        // Run layout multiple times
        val viewModel1 = layoutEngine.layout(flowIR)
        val viewModel2 = layoutEngine.layout(flowIR)
        val viewModel3 = layoutEngine.layout(flowIR)

        // Verify node order is stable
        assertEquals(viewModel1.nodes.size, viewModel2.nodes.size)
        assertEquals(viewModel1.nodes.size, viewModel3.nodes.size)

        for (i in viewModel1.nodes.indices) {
            assertEquals(viewModel1.nodes[i].id, viewModel2.nodes[i].id, "Node order should be stable")
            assertEquals(viewModel1.nodes[i].id, viewModel3.nodes[i].id, "Node order should be stable")
            assertEquals(viewModel1.nodes[i].position, viewModel2.nodes[i].position, "Node position should be stable")
            assertEquals(viewModel1.nodes[i].position, viewModel3.nodes[i].position, "Node position should be stable")
        }
    }
}

