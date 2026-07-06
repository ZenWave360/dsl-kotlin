package io.zenwave360.language.eventflow.view

import io.zenwave360.language.eventflow.view.*
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.language.readTestFile
import kotlin.collections.get
import kotlin.test.*
import kotlin.text.iterator

class FlowLayoutEngineTest {

    @Test
    fun testLayout_EmptyFlow() {
        val flowViewModel = FlowViewModel(nodes = emptyList(), edges = emptyList())
        val layoutEngine = FlowLayoutEngine()
        val viewModel = layoutEngine.layout(flowViewModel)

        assertEquals(0, viewModel.nodes.size)
        assertEquals(0, viewModel.edges.size)
        assertTrue(viewModel.systemGroups.isNullOrEmpty())
        assertEquals(FlowBounds(0.0, 0.0, 0.0, 0.0), viewModel.bounds)
    }

    @Test
    fun testLayout_SimpleFlow() {
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
            }
        """.trimIndent()

        val model = ZflParser().parseModel(zflContent)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        val transformer = ZflToFlowViewModelTransformer()
        val flowViewModel = transformer.transform(semanticModel)

        val layoutEngine = FlowLayoutEngine()
        val viewModel = layoutEngine.layout(flowViewModel)

        // Verify nodes are created
        assertEquals(4, viewModel.nodes.size, "Should have 4 nodes")

        // Verify all nodes have positions
        viewModel.nodes.forEach { node ->
            assertNotNull(node.position, "Node ${node.id} should have a position")
            assertTrue(node.position!!.x >= 0, "Node ${node.id} x position should be >= 0")
            assertTrue(node.position!!.y >= 0, "Node ${node.id} y position should be >= 0")
        }

        // Verify all nodes have dimensions
        viewModel.nodes.forEach { node ->
            assertNotNull(node.dimensions, "Node ${node.id} should have dimensions")
            assertTrue(node.dimensions!!.width > 0, "Node ${node.id} width should be > 0")
            assertTrue(node.dimensions!!.height > 0, "Node ${node.id} height should be > 0")
        }

        // Verify edges are created
        assertEquals(4, viewModel.edges.size, "Should have 4 edges")

        // Verify bounds are calculated
        assertTrue(viewModel.bounds!!.width > 0, "Bounds width should be > 0")
        assertTrue(viewModel.bounds!!.height > 0, "Bounds height should be > 0")
    }

    @Test
    fun testLayout_NodeDimensions() {
        val zflContent = """
            systems {
                TestSystem {
                    service TestService {
                        commands: testCommand
                    }
                }
            }
            flow TestFlow {
                
                start TestEvent {
                }
                
                @if("condition")
                when TestEvent do testCommand {
                    emits ResultEvent
                }
            }
        """.trimIndent()

        val model = ZflParser().parseModel(zflContent)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        val transformer = ZflToFlowViewModelTransformer()
        val flowViewModel = transformer.transform(semanticModel)

        val layoutEngine = FlowLayoutEngine()
        val viewModel = layoutEngine.layout(flowViewModel)

        // Find nodes by type
        val commandNode = viewModel.nodes.find { it.type == FlowNodeType.COMMAND }
        val eventNode = viewModel.nodes.find { it.type == FlowNodeType.EVENT }
        val policyNode = viewModel.nodes.find { it.type == FlowNodeType.POLICY }

        // Verify COMMAND dimensions
        assertNotNull(commandNode, "Should have a command node")
        assertEquals(180.0, commandNode.dimensions!!.width, "Command width should be 180")
        assertEquals(56.0, commandNode.dimensions!!.height, "Command height should be 56")

        // Verify event dimensions
        assertNotNull(eventNode, "Should have an event node")
        assertEquals(160.0, eventNode.dimensions!!.width, "Event width should be 160")
        assertEquals(48.0, eventNode.dimensions!!.height, "Event height should be 48")

        // Verify POLICY dimensions
        assertNotNull(policyNode, "Should have a policy node")
        assertEquals(220.0, policyNode.dimensions!!.width, "Policy width should be 220")
        assertEquals(64.0, policyNode.dimensions!!.height, "Policy height should be 64")
    }

    @Test
    fun testLayout_SubscriptionsFlow() {
        // Parse and analyze
        val content = readTestFile("flow/subscriptions.zfl")
        val model = ZflParser().parseModel(content)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)

        // Transform to FlowViewModel (without layout)
        val transformer = ZflToFlowViewModelTransformer()
        val flowViewModel = transformer.transform(semanticModel)

        // Layout
        val layoutEngine = FlowLayoutEngine()
        val viewModel = layoutEngine.layout(flowViewModel)

        // Verify nodes
        assertEquals(21, viewModel.nodes.size, "Should have 21 nodes (6 commands + 7 events + 3 starts + 5 policies)")

        // Verify all nodes have valid positions and dimensions
        viewModel.nodes.forEach { node ->
            assertTrue(node.position!!.x >= 0, "Node ${node.id} x should be >= 0")
            assertTrue(node.position!!.y >= 0, "Node ${node.id} y should be >= 0")
            assertTrue(node.dimensions!!.width > 0, "Node ${node.id} width should be > 0")
            assertTrue(node.dimensions!!.height > 0, "Node ${node.id} height should be > 0")
        }

        // Verify edges
        assertTrue(viewModel.edges.isNotEmpty(), "Should have edges")

        // Verify system groups
        assertTrue(viewModel.systemGroups?.isNotEmpty() ?: false, "Should have system groups")
        val systemNames = viewModel.systemGroups!!.map { it.systemName }.toSet()
        assertTrue(systemNames.contains("Subscription"), "Should have Subscription system group")
        assertTrue(systemNames.contains("Payments"), "Should have Payments system group")
//        assertTrue(systemNames.contains("Billing"), "Should have Billing system group")

        // Verify bounds
        assertTrue(viewModel.bounds!!.width > 0, "Bounds width should be > 0")
        assertTrue(viewModel.bounds!!.height > 0, "Bounds height should be > 0")
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

                when Event1 do cmd1 {
                    emits Event2
                }

                when Event2 do cmd2 {
                    emits Event3
                }

                when Event3 do cmd3 {
                    emits Event4
                }
            }
        """.trimIndent()

        val model = ZflParser().parseModel(zflContent)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        val transformer = ZflToFlowViewModelTransformer()
        val flowViewModel = transformer.transform(semanticModel)

        val layoutEngine = FlowLayoutEngine()

        // Run layout multiple times
        val viewModel1 = layoutEngine.layout(flowViewModel)
        val viewModel2 = layoutEngine.layout(flowViewModel)
        val viewModel3 = layoutEngine.layout(flowViewModel)

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

    @Test
    fun testLayout_DirectCallPlacesCalleeToTheRight() {
        val zflContent = """
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

        val model = ZflParser().parseModel(zflContent)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        val transformer = ZflToFlowViewModelTransformer()
        val flowViewModel = transformer.transform(semanticModel)

        val viewModel = FlowLayoutEngine().layout(flowViewModel)

        val caller = viewModel.nodes.first { it.id == "command:startOrderCheckout" }
        val callee = viewModel.nodes.first { it.id == "command:reserveStock" }
        assertTrue(callee.position!!.x >= caller.position!!.x, "Direct call target should not be placed to the left of the caller")
    }

    @Test
    fun testLayout_SiblingTargetsPreferCommandsAboveEvents() {
        val zflContent = """
            flow MixedSiblingFlow {
                start StartCheckout {
                }

                when StartCheckout do beginCheckout {
                    service OrdersCheckout.OrdersCheckoutService
                    call reserveStock
                    emits CheckoutStarted
                }

                do reserveStock {
                    service CatalogProducts.CatalogProductsService
                    emits StockReserved
                }
            }
        """.trimIndent()

        val model = ZflParser().parseModel(zflContent)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        val flowViewModel = ZflToFlowViewModelTransformer().transform(semanticModel)

        val viewModel = FlowLayoutEngine().layout(flowViewModel)

        val source = viewModel.nodes.first { it.id == "command:beginCheckout" }
        val callee = viewModel.nodes.first { it.id == "command:reserveStock" }
        val emittedEvent = viewModel.nodes.first { it.id == "event:CheckoutStarted" }

        assertTrue(callee.position!!.x >= source.position!!.x, "Callee should be to the right of the source command")
        assertTrue(emittedEvent.position!!.x >= source.position!!.x, "Emitted event should be to the right of the source command")
        assertTrue(callee.position!!.y < emittedEvent.position!!.y, "Sibling command targets should be placed above sibling event targets")
    }

    @Test
    fun testLayout_OutcomeAnnotatedFlowsIncreaseHorizontalSpacing() {
        val zflContent = """
            flow PaymentsFlow {
                start CheckoutStarted {
                }

                when CheckoutStarted do authorizePayment {
                    service PaymentsProcessing.PaymentsProcessingService
                    @outcome("authorized") emits PaymentAuthorized, OrderUpdated
                    @outcome("declined") emits PaymentDeclined
                }
            }
        """.trimIndent()

        val model = ZflParser().parseModel(zflContent)
        val semanticModel = ZflSemanticAnalyzer().analyze(model)
        val flowViewModel = ZflToFlowViewModelTransformer().transform(semanticModel)

        val laidOut = FlowLayoutEngine().layout(flowViewModel)

        assertEquals(240.0, laidOut.layout!!.rankSpacing)
        val command = laidOut.nodes.first { it.id == "command:authorizePayment" }
        val event = laidOut.nodes.first { it.id == "event:PaymentAuthorized" }
        assertEquals(240.0, event.position!!.x - command.position!!.x)
    }
}

