package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.zfl.ZflParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ZflSemanticAnalyzerTest {

    @Test
    fun testAnalyze_ActionsCallsAndOutcomeHandlers() {
        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(sampleFlow()))

        assertEquals(1, semanticModel.flows.size)
        val flow = semanticModel.flows.first()
        assertEquals("CheckoutFlow", flow.name)

        assertEquals(3, flow.commands.size)
        val startOrderCheckout = flow.commands.first { it.name == "startOrderCheckout" }
        assertEquals("OrdersCheckout", startOrderCheckout.system)
        assertEquals(listOf("OrderCreated", "StockUnavailable"), startOrderCheckout.emits)
        assertEquals(emptyList(), startOrderCheckout.responses)

        assertEquals(4, startOrderCheckout.steps.size)
        assertIs<ZflServiceStep>(startOrderCheckout.steps[0])
        val reserveStockCall = assertIs<ZflCallStep>(startOrderCheckout.steps[1])
        assertEquals("reserveStock", reserveStockCall.action)
        assertEquals(2, reserveStockCall.handlers.size)
        assertEquals("StockReserved", reserveStockCall.handlers[0].outcome)
        assertEquals("createOrder", reserveStockCall.handlers[0].action)
        assertEquals("StockUnavailable", reserveStockCall.handlers[1].outcome)
        assertEquals("StockUnavailable", reserveStockCall.handlers[1].emits)

        assertEquals(1, flow.policies.size)
        assertEquals("startOrderCheckout", flow.policies.first().command)
        assertEquals(listOf("OrderCreated", "StockUnavailable"), flow.policies.first().events)

        val emittedNames = flow.events.map { it.name }.toSet()
        assertEquals(
            setOf("StockReserved", "StockUnavailable", "OrderCreated"),
            emittedNames
        )
        assertTrue(semanticModel.diagnostics.isEmpty())
    }

    @Test
    fun testAnalyze_OnWithoutPrecedingCallAddsDiagnostic() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow TestFlow {
                        when Start do testAction
                        do testAction {
                            service Test.TestService
                            on Failed emits Failed
                            emits Done
                        }
                        end {
                            completed: Done
                        }
                    }
                """.trimIndent()
            )
        )

        assertTrue(semanticModel.diagnostics.any { it.message.contains("without a preceding call") })
    }

    @Test
    fun testAnalyze_UnknownCallTargetAddsDiagnostic() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow TestFlow {
                        when Start do testAction
                        do testAction {
                            service Test.TestService
                            call missingAction
                            emits Done
                        }
                        end {
                            completed: Done
                        }
                    }
                """.trimIndent()
            )
        )

        assertTrue(semanticModel.diagnostics.any { it.message.contains("calls unknown action 'missingAction'") })
    }

    @Test
    fun testAnalyze_HandlerForUnknownCallOutcomeAddsDiagnostic() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow TestFlow {
                        when Start do testAction
                        do testAction {
                            service Test.TestService
                            call reserveStock
                            on UnknownOutcome emits Failed
                            emits Failed
                        }
                        do reserveStock {
                            service Stock.StockService
                            emits Reserved
                        }
                        end {
                            completed: Failed
                        }
                    }
                """.trimIndent()
            )
        )

        assertTrue(semanticModel.diagnostics.any { it.message.contains("handles unknown outcome 'UnknownOutcome'") })
    }

    @Test
    fun testAnalyze_ResponseOutcomesAreCallableButNotPublishedAsEvents() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow TestFlow {
                        start Start {}
                        when Start do placeOrder
                        do placeOrder {
                            service OrdersCheckout.TestService
                            call reserveStock
                            on StockUnavailable emits OrderRejected
                            emits OrderAccepted
                            emits OrderRejected
                        }
                        do reserveStock {
                            service Stock.StockService
                            response StockUnavailable
                            emits response StockReserved
                        }
                        end {
                            completed: OrderAccepted
                            rejected: OrderRejected
                        }
                    }
                """.trimIndent()
            )
        )

        val reserveStock = semanticModel.flows.first().commands.first { it.name == "reserveStock" }
        assertEquals(listOf("StockReserved"), reserveStock.emits)
        assertEquals(listOf("StockUnavailable", "StockReserved"), reserveStock.responses)

        val eventNames = semanticModel.flows.first().events.map { it.name }.toSet()
        assertEquals(setOf("StockReserved", "OrderAccepted", "OrderRejected"), eventNames)
        assertTrue(semanticModel.diagnostics.isEmpty())
    }

    @Test
    fun testAnalyze_WhenCannotTriggerFromResponseOnlyOutcome() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow TestFlow {
                        when StockUnavailable do rejectOrder
                        do reserveStock {
                            service Stock.StockService
                            response StockUnavailable
                        }
                        do rejectOrder {
                            service OrdersCheckout.TestService
                            emits OrderRejected
                        }
                        end {
                            completed: OrderRejected
                        }
                    }
                """.trimIndent()
            )
        )

        assertTrue(semanticModel.diagnostics.any { it.message.contains("unknown trigger 'StockUnavailable'") })
    }

    private fun sampleFlow() = """
        systems {
            CatalogProducts {
                service CatalogProductsService
            }
            OrdersCheckout {
                service OrdersCheckoutService
            }
        }
        flow CheckoutFlow {
            @actor(Customer)
            start StartOrderCheckout {
                items SKU[]
            }

            when StartOrderCheckout do startOrderCheckout

            do startOrderCheckout {
                service OrdersCheckout.OrdersCheckoutService
                call reserveStock
                on StockReserved call createOrder
                on StockUnavailable emits StockUnavailable
                emits OrderCreated
                emits StockUnavailable
            }

            do reserveStock {
                service CatalogProducts.CatalogProductsService
                emits StockReserved
                emits StockUnavailable
            }

            do createOrder {
                service OrdersCheckout.OrdersCheckoutService
                emits OrderCreated
            }

            end {
                completed: OrderCreated
                stockGone: StockUnavailable
            }
        }
    """.trimIndent()
}
