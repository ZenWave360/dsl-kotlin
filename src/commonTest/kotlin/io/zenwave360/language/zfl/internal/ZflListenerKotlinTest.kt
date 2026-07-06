package io.zenwave360.language.zfl.internal

import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.zfl.ZflParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.steps[2].endOutcome")
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
            JSONPath.get(model, "$.flows.CheckoutFlow.actions.startOrderCheckout.steps[3].events[0]")
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
                        @failure emits StockFailed
                        response StockUnavailable
                        emits response ReservationAccepted
                    }
                }
            """.trimIndent()
        )

        assertEquals(
            listOf("StockReserved", "StockFailed", "ReservationAccepted"),
            JSONPath.get(model, "$.flows.TestFlow.actions.reserveStock.emits")
        )
        assertEquals(
            listOf("StockUnavailable", "ReservationAccepted"),
            JSONPath.get(model, "$.flows.TestFlow.actions.reserveStock.responses")
        )
        assertEquals(
            true,
            JSONPath.get(model, "$.flows.TestFlow.actions.reserveStock.steps[3].response")
        )
        assertEquals(
            true,
            JSONPath.get(model, "$.flows.TestFlow.actions.reserveStock.steps[4].emits")
        )
        assertEquals(
            true,
            JSONPath.get(model, "$.flows.TestFlow.actions.reserveStock.steps[4].response")
        )
        @Suppress("UNCHECKED_CAST")
        val steps = JSONPath.get<List<Map<String, Any?>>>(model, "$.flows.TestFlow.actions.reserveStock.steps").orEmpty()
        @Suppress("UNCHECKED_CAST")
        val failureOptions = steps[2]["options"] as Map<String, Any?>
        assertTrue(failureOptions.containsKey("failure"))
    }

    @Test
    fun parseZfl_AsyncCall_IsStoredOnCallStep() {
        val model = ZflParser().parseModel(
            """
                flow TestFlow {
                    when OrderCreated do authorizePayment {
                        service PaymentsProcessing.PaymentsProcessingService
                        async call authorizePayment
                    }
                }
            """.trimIndent()
        )

        assertEquals(
            "call",
            JSONPath.get(model, "$.flows.TestFlow.actions.authorizePayment.steps[1].type")
        )
        assertEquals(
            true,
            JSONPath.get(model, "$.flows.TestFlow.actions.authorizePayment.steps[1].async")
        )
        assertEquals(
            "authorizePayment",
            JSONPath.get(model, "$.flows.TestFlow.actions.authorizePayment.steps[1].action")
        )
    }

    @Test
    fun parseZfl_SignalAnnotations_AreStoredOnSignalSteps() {
        val model = ZflParser().parseModel(
            """
                flow TestFlow {
                    do authorizePayment {
                        service PaymentsProcessing.PaymentsProcessingService
                        emits PaymentAuthorized
                        @failure emits PaymentDeclined
                        @failure emits PaymentFailed
                    }
                }
            """.trimIndent()
        )

        @Suppress("UNCHECKED_CAST")
        val steps = JSONPath.get<List<Map<String, Any?>>>(model, "$.flows.TestFlow.actions.authorizePayment.steps").orEmpty()
        @Suppress("UNCHECKED_CAST")
        val firstFailureOptions = steps[2]["options"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val secondFailureOptions = steps[3]["options"] as Map<String, Any?>

        assertTrue(firstFailureOptions.containsKey("failure"))
        assertTrue(secondFailureOptions.containsKey("failure"))
    }

    @Test
    fun parseZfl_OnEmitsSupportsAnnotationsAndMultipleSignalEvents() {
        val model = ZflParser().parseModel(
            """
                flow TestFlow {
                    do startOrderCheckout {
                        service OrdersCheckout.OrdersCheckoutService
                        call reserveStock
                        @outcome("created") on StockReserved emits response OrderCreated
                        on StockConfirmed emits OrderConfirmed, EventB
                    }
                }
            """.trimIndent()
        )

        assertEquals(
            "signal",
            JSONPath.get(model, "$.flows.TestFlow.actions.startOrderCheckout.steps[2].kind")
        )
        assertEquals(
            listOf("OrderCreated"),
            JSONPath.get(model, "$.flows.TestFlow.actions.startOrderCheckout.steps[2].events")
        )
        assertEquals(
            true,
            JSONPath.get(model, "$.flows.TestFlow.actions.startOrderCheckout.steps[2].emits")
        )
        assertEquals(
            true,
            JSONPath.get(model, "$.flows.TestFlow.actions.startOrderCheckout.steps[2].response")
        )
        assertEquals(
            "created",
            JSONPath.get(model, "$.flows.TestFlow.actions.startOrderCheckout.steps[2].outcome")
        )
        assertEquals(
            listOf("OrderConfirmed", "EventB"),
            JSONPath.get(model, "$.flows.TestFlow.actions.startOrderCheckout.steps[3].events")
        )
        assertEquals(
            "StockConfirmed",
            JSONPath.get(model, "$.flows.TestFlow.actions.startOrderCheckout.steps[3].outcome")
        )
        assertTrue(model.getProblems().isEmpty())
    }

    @Test
    fun parseZfl_IncompleteFlow_DoesNotThrowAndRecordsProblem() {
        val model = ZflParser().parseModel(
            """
                flow
            """.trimIndent()
        )

        assertNotNull(model)
        assertTrue(
            model.getProblems().isNotEmpty(),
            "Expected parser problems for incomplete flow input"
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
