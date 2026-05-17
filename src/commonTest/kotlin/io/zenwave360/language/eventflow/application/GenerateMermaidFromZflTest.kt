package io.zenwave360.language.eventflow.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateMermaidFromZflTest {

    @Test
    fun testExecute_ReturnsMermaidText() {
        val zflContent = """
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

        val diagrams = GenerateMermaidFromZfl().execute(zflContent)

        assertEquals("SimpleFlow", diagrams.flowName)
        assertTrue(diagrams.flowchart.startsWith("flowchart TD"))
        assertEquals(1, diagrams.sequences.size)
        assertTrue(diagrams.sequences.single().mermaid.startsWith("sequenceDiagram"))
    }
}
