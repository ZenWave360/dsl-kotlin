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
        assertEquals(listOf("OrderCreated", "StockUnavailable"), startOrderCheckout.emits.map { it.eventName })
        assertEquals(emptyList(), startOrderCheckout.responses)

        assertEquals(4, startOrderCheckout.steps.size)
        assertIs<ZflServiceStep>(startOrderCheckout.steps[0])
        val reserveStockCall = assertIs<ZflCallStep>(startOrderCheckout.steps[1])
        assertEquals("reserveStock", reserveStockCall.action)
        assertEquals(2, reserveStockCall.handlers.size)
        assertEquals("StockReserved", reserveStockCall.handlers[0].endOutcome)
        assertEquals("createOrder", reserveStockCall.handlers[0].action)
        assertEquals("StockUnavailable", reserveStockCall.handlers[1].endOutcome)
        assertEquals(listOf("StockUnavailable"), reserveStockCall.handlers[1].signal?.events)
        assertEquals(true, reserveStockCall.handlers[1].signal?.emits)
        assertEquals("StockUnavailable", reserveStockCall.handlers[1].signal?.outcome)

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

        assertTrue(semanticModel.diagnostics.any { it.message.contains("handles unknown endOutcome 'UnknownOutcome'") })
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
        assertEquals(listOf("StockReserved"), reserveStock.emits.map { it.eventName })
        assertEquals(listOf("StockUnavailable", "StockReserved"), reserveStock.responses)

        val eventNames = semanticModel.flows.first().events.map { it.name }.toSet()
        assertEquals(setOf("StockReserved", "OrderAccepted", "OrderRejected"), eventNames)
        assertTrue(semanticModel.diagnostics.isEmpty())
    }

    @Test
    fun testAnalyze_AsyncCall_UsesEmitsForPublishedContinuations() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow TestFlow {
                        when OrderCreated do authorizePayment {
                            service PaymentsProcessing.PaymentsProcessingService
                            async call authorizePayment
                            emits PaymentAuthorized
                        }
                        when PaymentAuthorized do confirmOrder {
                            service OrdersCheckout.OrdersCheckoutService
                            emits OrderConfirmed
                        }
                        end {
                            completed: OrderConfirmed
                        }
                    }
                """.trimIndent()
            )
        )

        val authorizePayment = semanticModel.flows.first().commands.first { it.name == "authorizePayment" }
        val asyncCall = assertIs<ZflCallStep>(authorizePayment.steps[1])
        assertEquals(true, asyncCall.async)
        assertEquals("authorizePayment", asyncCall.action)
        assertEquals(listOf("PaymentAuthorized"), authorizePayment.emits.map { it.eventName })
        assertTrue(semanticModel.flows.first().events.any { it.name == "PaymentAuthorized" })
        assertTrue(semanticModel.diagnostics.none { it.message.contains("unknown trigger 'PaymentAuthorized'") })
    }

    @Test
    fun testAnalyze_AsyncCallToLocalResponseOnlyCommandAddsWarning() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow TestFlow {
                        when Start do placeOrder
                        do placeOrder {
                            service OrdersCheckout.TestService
                            async call reserveStock
                            emits OrderAccepted
                        }
                        do reserveStock {
                            service Stock.StockService
                            response StockReserved
                        }
                        end {
                            completed: OrderAccepted
                        }
                    }
                """.trimIndent()
            )
        )

        assertTrue(
            semanticModel.diagnostics.any {
                it.severity == Severity.WARNING &&
                    it.message.contains("only declares response outcomes")
            }
        )
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

    @Test
    fun testAnalyze_SignalAnnotations_ArePreservedOnSemanticSteps() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow PaymentsFlow {
                        do authorizePayment {
                            service PaymentsProcessing.PaymentsProcessingService
                            emits PaymentAuthorized
                            @failure emits PaymentDeclined
                            @failure emits PaymentFailed
                        }
                        end {
                            completed: PaymentAuthorized
                        }
                    }
                """.trimIndent()
            )
        )

        val authorizePayment = semanticModel.flows.first().commands.first { it.name == "authorizePayment" }
        val signalSteps = authorizePayment.steps.filterIsInstance<ZflSignalStep>()

        assertEquals(3, signalSteps.size)
        assertTrue(signalSteps[0].options.isEmpty())
        assertTrue(signalSteps[1].options.containsKey("failure"))
        assertTrue(signalSteps[2].options.containsKey("failure"))
        assertEquals(
            listOf("PaymentAuthorized", "PaymentDeclined", "PaymentFailed"),
            authorizePayment.emits.map { it.eventName }
        )
    }

    @Test
    fun testAnalyze_OutcomeAnnotatedEmitsAreStoredAsEmissionMetadata() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow PaymentsFlow {
                        do authorizePayment {
                            service PaymentsProcessing.PaymentsProcessingService
                            @outcome("authorized") emits PaymentAuthorized, OrderUpdated
                            @outcome("declined") emits PaymentDeclined
                        }
                        end {
                            completed: PaymentAuthorized
                        }
                    }
                """.trimIndent()
            )
        )

        val authorizePayment = semanticModel.flows.first().commands.first { it.name == "authorizePayment" }
        assertEquals(
            listOf(
                ZflEmission("PaymentAuthorized", "authorized"),
                ZflEmission("OrderUpdated", "authorized"),
                ZflEmission("PaymentDeclined", "declined")
            ),
            authorizePayment.emits
        )
    }

    @Test
    fun testAnalyze_OnEmitsSupportsMultipleEventsAndOutcomeMetadata() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow CheckoutFlow {
                        do startOrderCheckout {
                            service OrdersCheckout.OrdersCheckoutService
                            call reserveStock
                            @outcome("created") on StockReserved emits response OrderCreated
                            on StockConfirmed emits OrderConfirmed, EventB
                        }
                        do reserveStock {
                            service CatalogProducts.CatalogProductsService
                            emits StockReserved
                            emits StockConfirmed
                        }
                        end {
                            completed: OrderCreated, OrderConfirmed, EventB
                        }
                    }
                """.trimIndent()
            )
        )

        val startOrderCheckout = semanticModel.flows.first().commands.first { it.name == "startOrderCheckout" }
        val reserveStockCall = startOrderCheckout.steps.filterIsInstance<ZflCallStep>().single()
        val created = reserveStockCall.handlers[0].signal
        val confirmed = reserveStockCall.handlers[1].signal

        assertEquals(listOf("OrderCreated"), created?.events)
        assertEquals(true, created?.emits)
        assertEquals(true, created?.response)
        assertEquals("created", created?.outcome)
        assertEquals(listOf("OrderConfirmed", "EventB"), confirmed?.events)
        assertEquals(true, confirmed?.emits)
        assertEquals(false, confirmed?.response)
        assertEquals("StockConfirmed", confirmed?.outcome)
        assertTrue(semanticModel.diagnostics.isEmpty())
    }

    @Test
    fun testAnalyze_ResponseSignalsRejectMultipleEvents() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow CheckoutFlow {
                        do startOrderCheckout {
                            service OrdersCheckout.OrdersCheckoutService
                            call reserveStock
                            on StockReserved emits response OrderCreated, EventB
                            response Done, Failed
                        }
                        do reserveStock {
                            service CatalogProducts.CatalogProductsService
                            emits StockReserved
                        }
                        end {
                            completed: OrderCreated
                        }
                    }
                """.trimIndent()
            )
        )

        assertEquals(
            2,
            semanticModel.diagnostics.count { it.message.contains("Response signals must declare exactly one event") }
        )
    }

    @Test
    fun testAnalyze_MixedOutcomeAnnotatedAndPlainEmitsAddsWarning() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow PaymentsFlow {
                        do authorizePayment {
                            service PaymentsProcessing.PaymentsProcessingService
                            @outcome("authorized") emits PaymentAuthorized
                            emits PaymentDeclined
                        }
                        end {
                            completed: PaymentAuthorized
                        }
                    }
                """.trimIndent()
            )
        )

        assertTrue(
            semanticModel.diagnostics.any {
                it.severity == Severity.WARNING &&
                    it.message.contains("mixes @outcome annotated and unannotated emits")
            }
        )
    }

    @Test
    fun testAnalyze_PreservesOccurrencesFailureAndSourceLocations() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(
                """
                    flow PaymentsFlow {
                        /** authorize a new payment */
                        when OrderCreated do authorizePayment {
                            service Payments.PaymentsService
                            emits PaymentAuthorized
                        }
                        /** authorize a retry */
                        when PaymentRetried do authorizePayment {
                            service Payments.PaymentsService
                            @failure emits PaymentFailed
                        }
                        end { completed: PaymentAuthorized }
                    }
                """.trimIndent(),
                "payments.zfl",
            )
        )

        val operation = semanticModel.flows.single().commands.single()
        assertEquals(2, operation.occurrences.size)
        assertEquals("authorizePayment@when[OrderCreated]", operation.occurrences[0].key)
        assertEquals("authorize a new payment", operation.occurrences[0].description)
        assertEquals("authorizePayment@when[PaymentRetried]", operation.occurrences[1].key)
        assertTrue(operation.occurrences[1].emissions.single().failure)
        assertEquals("payments.zfl", operation.occurrences[0].sourceRef.file)
        assertTrue(operation.occurrences[0].sourceRef.line > 1)
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
