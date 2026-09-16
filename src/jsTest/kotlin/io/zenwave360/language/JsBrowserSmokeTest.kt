package io.zenwave360.language

import generateMermaidFromZdl
import io.zenwave360.language.eventflow.application.GenerateFlowViewFromZfl
import io.zenwave360.language.eventflow.application.GenerateMermaidFromZfl
import kotlinx.coroutines.test.runTest
import parseZdl
import parseZfl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the JS artifact without reading anything from a filesystem, so it runs both on Node
 * (jsNodeTest) and in a headless browser (jsBrowserTest). In the browser it is the check that the
 * library loads and works with no Node runtime: a static Node import in the bundle fails the run.
 */
class JsBrowserSmokeTest {

    private val zdl = """
        config {
            plugins {
                ExamplePlugin {
                    maxDepth 3
                    neg -2
                }
            }
        }

        aggregate OrderAggregate(Order) {
            cancel(CancelInput) withEvents OrderCancelled
        }

        @aggregate
        entity Order {
            status OrderStatus
        }

        enum OrderStatus { OPEN, CLOSED }

        input CancelInput { reason String }

        event OrderCancelled { id String }
    """.trimIndent()

    private val zfl = """
        flow SimpleFlow {
            @actor(Customer)
            start CheckoutStarted {
            }

            when CheckoutStarted do createOrder {
                service Orders.OrderService
                emits OrderCreated
            }

            end {
                completed: OrderCreated
            }
        }
    """.trimIndent()

    @Test
    fun parsesZdlIntoPlainJsValues() {
        val model = parseZdl(zdl).asDynamic()
        val config = model.plugins.ExamplePlugin.config
        assertEquals("number", jsTypeOf(config.maxDepth))
        assertEquals("3", JSON.stringify(config.maxDepth))
        assertEquals("-2", JSON.stringify(config.neg))
        assertEquals("Order", model.aggregates.OrderAggregate.aggregateRoot)
    }

    @Test
    fun generatesMermaidClassDiagramFromZdl() {
        val mermaid = generateMermaidFromZdl(zdl)
        assertTrue(mermaid.startsWith("classDiagram\n"), mermaid)
        assertTrue(mermaid.contains("OrderAggregate *-- Order"), mermaid)
    }

    @Test
    fun parsesZflAndGeneratesMermaid() {
        val model = parseZfl(zfl).asDynamic()
        assertTrue(model.flows.SimpleFlow != undefined)
        val diagrams = GenerateMermaidFromZfl().execute(zfl)
        assertTrue(diagrams.flowchart.startsWith("flowchart TD"))
    }

    @Test
    fun laysOutFlowViewWithElk() = runTest {
        val view = GenerateFlowViewFromZfl().execute(zfl)
        assertTrue(view.nodes.isNotEmpty())
        assertTrue((view.bounds?.width ?: 0.0) > 0.0, "bounds: ${view.bounds}")
    }
}
