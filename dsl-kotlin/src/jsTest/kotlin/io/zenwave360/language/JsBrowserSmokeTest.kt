package io.zenwave360.language

import io.zenwave360.language.eventflow.application.GenerateFlowViewFromZfl
import io.zenwave360.language.zfl.editing.ZflEditor
import io.zenwave360.language.zfl.editing.applyEdits
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
            --force
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
        val config = model.pluginsList[0].config
        assertEquals("number", jsTypeOf(config.maxDepth))
        assertEquals("3", JSON.stringify(config.maxDepth))
        assertEquals("-2", JSON.stringify(config.neg))
        assertTrue(model.cliOptions.force == null)
        assertEquals("Order", model.aggregates.OrderAggregate.aggregateRoot)
    }

    @Test
    fun parsesZfl() {
        val model = parseZfl(zfl).asDynamic()
        assertTrue(model.flows.SimpleFlow != undefined)
    }

    @Test
    fun laysOutFlowViewWithElk() = runTest {
        val view = GenerateFlowViewFromZfl().execute(zfl)
        assertTrue(view.nodes.isNotEmpty())
        assertTrue((view.bounds?.width ?: 0.0) > 0.0, "bounds: ${view.bounds}")
    }

    /**
     * Edit offsets are UTF-16 indexes into the source string, also when the text before the target holds
     * characters outside the BMP. Inline source, so the browser run covers it too.
     * spec:flows/zfl-visual-editing#zve-r8
     */
    @Test
    fun zflEditOffsetsSurviveAstralCharactersBeforeTheTarget() {
        val emoji = "😀"
        val source = "// $emoji$emoji$emoji\n" + zfl.replace("when CheckoutStarted", "// keep $emoji intact\n    when CheckoutStarted")
        val result = ZflEditor().renameCommand(source, "SimpleFlow", "createOrder", "placeOrder")
        assertTrue(result.accepted, "diagnostics: ${result.diagnostics}")
        assertEquals(source.replace("do createOrder", "do placeOrder"), applyEdits(source, result.edits))
    }
}
