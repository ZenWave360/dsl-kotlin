package io.zenwave360.language

import generateMermaidFromZdl
import io.zenwave360.language.eventflow.application.GenerateMermaidFromZfl
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Reads nothing from a filesystem, so it runs on Node and in a headless browser.
 */
class JsBrowserSmokeTest {

    @Test
    fun generatesMermaidClassDiagramFromZdl() {
        val mermaid = generateMermaidFromZdl(
            """
            aggregate OrderAggregate(Order) { cancel(CancelInput) withEvents OrderCancelled }
            @aggregate entity Order { status String }
            input CancelInput { reason String }
            event OrderCancelled { id String }
            """.trimIndent(),
        )
        assertTrue(mermaid.startsWith("classDiagram\n"), mermaid)
        assertTrue(mermaid.contains("OrderAggregate *-- Order"), mermaid)
    }

    @Test
    fun generatesMermaidFromZfl() {
        val diagrams = GenerateMermaidFromZfl().execute(
            """
            flow SimpleFlow {
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
            """.trimIndent(),
        )
        assertTrue(diagrams.flowchart.startsWith("flowchart TD"), diagrams.flowchart)
    }
}
