package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.MermaidSequenceRenderMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
        assertEquals(MermaidSequenceRenderMode.ALT_BLOCKS, diagrams.sequenceRenderMode)
        assertTrue(diagrams.sequences.single().mermaid.startsWith("sequenceDiagram"))
    }

    @Test
    fun testExecute_OverloadSupportsAltBlocksWithoutBreakingOldExecuteSignature() {
        val zflContent = """
            flow SimpleFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do createOrder {
                    service Orders.OrderService
                    emits OrderCreated
                }

                when OrderCreated do authorizePayment {
                    service Payments.PaymentService
                    emits PaymentAuthorized
                    emits PaymentDeclined
                }

                end {
                    done: PaymentAuthorized, PaymentDeclined
                }
            }
        """.trimIndent()

        val diagrams = GenerateMermaidFromZfl().execute(zflContent, MermaidSequenceRenderMode.ALT_BLOCKS)

        assertEquals(MermaidSequenceRenderMode.ALT_BLOCKS, diagrams.sequenceRenderMode)
        assertTrue(diagrams.sequences.single().mermaid.contains("alt via PaymentAuthorized"))
    }

    @Test
    fun testExecute_IncompleteInput_DoesNotThrow() {
        val diagrams = GenerateMermaidFromZfl().execute("flow")

        assertNotNull(diagrams)
        assertTrue(diagrams.flowchart.startsWith("flowchart TD"))
    }
}
