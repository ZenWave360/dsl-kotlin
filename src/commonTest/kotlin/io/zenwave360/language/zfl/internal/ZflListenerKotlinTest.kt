package io.zenwave360.language.zfl.internal

import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.zfl.ZflParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZflListenerKotlinTest {

    @Test
    fun parseZfl_ActionsCallsAndOutcomes() {
        val model = ZflParser().parseModel(sampleFlow())

        assertEquals(1, (JSONPath.get(model, "$.flows") as? Map<*, *>)?.size ?: 0)
        assertEquals(
            "startOrderCheckout",
            JSONPath.get(model, "$.flows.CheckoutFlow.whens[0].action")
        )
        assertEquals(
            listOf("StartOrderCheckout"),
            JSONPath.get(model, "$.flows.CheckoutFlow.whens[0].triggers")
        )

        assertEquals(
            "OrdersCheckout",
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.system")
        )
        assertEquals(
            "OrdersCheckoutService",
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.service")
        )
        assertEquals(
            listOf("OrderCreated", "StockUnavailable"),
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.emits")
        )

        assertEquals(
            "service",
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.steps[0].type")
        )
        assertEquals(
            "call",
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.steps[1].type")
        )
        assertEquals(
            "reserveStock",
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.steps[1].action")
        )
        assertEquals(
            "on",
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.steps[2].type")
        )
        assertEquals(
            "StockReserved",
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.steps[2].outcome")
        )
        assertEquals(
            "call",
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.steps[2].kind")
        )
        assertEquals(
            "createOrder",
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.steps[2].action")
        )
        assertEquals(
            "StockUnavailable",
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.steps[3].emits")
        )

        assertEquals(
            listOf("StockReserved", "StockUnavailable"),
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.reserveStock.emits")
        )
        assertTrue(model.getProblems().isEmpty())
    }

    @Test
    fun parseZfl_WhenTriggerList_ReportsMixedAndTrailingSeparators() {
        val mixedModel = ZflParser().parseModel(
            """
                flow TestFlow {
                    when PaymentDeclined, PaymentFailed | PaymentVoided do releaseStock
                }
            """.trimIndent()
        )
        assertTrue(mixedModel.getProblems().any { it["message"] == "Mixed separators are not allowed in when triggers" })

        val trailingModel = ZflParser().parseModel(
            """
                flow TestFlow {
                    when PaymentDeclined, do releaseStock
                }
            """.trimIndent()
        )
        assertTrue(trailingModel.getProblems().any { it["message"] == "Trailing separators are not allowed in when triggers" })
    }

    @Test
    fun parseZfl_SignalDeclarationsTrackEmitAndResponseChannels() {
        val model = ZflParser().parseModel(
            """
                flow TestFlow {
                    do reserveStock {
                        service CatalogProducts.CatalogProductsService
                        emits StockReserved
                        response StockUnavailable
                        emits response ReservationAccepted
                    }
                }
            """.trimIndent()
        )

        assertEquals(
            listOf("StockReserved", "ReservationAccepted"),
            JSONPath.get(model, "$.flows.TestFlow.actions.reserveStock.emits")
        )
        assertEquals(
            listOf("StockUnavailable", "ReservationAccepted"),
            JSONPath.get(model, "$.flows.TestFlow.actions.reserveStock.responses")
        )
        assertEquals(
            true,
            JSONPath.get(model, "$.flows.TestFlow.actions.reserveStock.steps[2].response")
        )
        assertEquals(
            true,
            JSONPath.get(model, "$.flows.TestFlow.actions.reserveStock.steps[3].emits")
        )
        assertEquals(
            true,
            JSONPath.get(model, "$.flows.TestFlow.actions.reserveStock.steps[3].response")
        )
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

            when StockReserved do createOrder {
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
