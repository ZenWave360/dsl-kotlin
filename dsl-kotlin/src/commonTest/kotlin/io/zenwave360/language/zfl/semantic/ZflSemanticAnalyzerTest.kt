package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.readTestFile
import io.zenwave360.language.zfl.ZflParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        assertEquals(1, reserveStockCall.handlers.size)
        assertEquals("StockReserved", reserveStockCall.handlers[0].endOutcome)
        assertEquals("createOrder", reserveStockCall.handlers[0].action)
        val createOrderCall = assertNotNull(reserveStockCall.handlers[0].chainedCall)
        assertEquals("createOrder", createOrderCall.action)
        assertEquals("StockUnavailable", createOrderCall.handlers.single().endOutcome)
        assertEquals(listOf("StockUnavailable"), createOrderCall.handlers.single().signal?.events)
        assertEquals(true, createOrderCall.handlers.single().signal?.emits)
        assertEquals("StockUnavailable", createOrderCall.handlers.single().signal?.outcome)

        assertEquals(1, flow.policies.size)
        assertEquals("startOrderCheckout", flow.policies.first().command)
        assertEquals(listOf("OrderCreated", "StockUnavailable"), flow.policies.first().events)

        val emittedNames = flow.events.map { it.name }.toSet()
        assertEquals(
            setOf("StockReserved", "StockUnavailable", "OrderCreated"),
            emittedNames
        )
        assertTrue(semanticModel.diagnostics.none { it.severity == Severity.ERROR })
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

        assertTrue(semanticModel.diagnostics.any { it.message.contains("not a declared response") })
    }

    @Test
    fun testAnalyze_OnListsAreTerminalAndChainToTheNextCall() {
        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
            """
                flow TestFlow {
                    do bookSlot {
                        service Bookings.BookingService
                        call getBookingTerms
                        on SlotNotBookable call checkBookingHolder
                        on HolderNotEligible, HolderBlocked response BookingRejected
                        emits Completed
                    }
                    do getBookingTerms {
                        service Facilities.CatalogService
                        response BookingTermsResult
                        response SlotNotBookable
                    }
                    do checkBookingHolder {
                        service Accounts.AccountService
                        response HolderEligible
                        response HolderNotEligible
                        response HolderBlocked
                    }
                    end { completed: Completed }
                }
            """.trimIndent()
        ))

        val steps = semanticModel.flows.single().commands.first { it.name == "bookSlot" }.steps
        val getTerms = assertIs<ZflCallStep>(steps[1])
        assertEquals("SlotNotBookable", getTerms.handlers.single().endOutcome)
        assertEquals("checkBookingHolder", getTerms.handlers.single().action)
        val checkHolder = assertNotNull(getTerms.handlers.single().chainedCall)
        assertEquals(listOf("HolderNotEligible", "HolderBlocked"), checkHolder.handlers.map { it.endOutcome })
        assertTrue(checkHolder.handlers.all { it.terminal })
        assertTrue(semanticModel.diagnostics.none { it.severity == Severity.ERROR })
    }

    @Test
    fun testAnalyze_ChainedCallHandlersValidateTheChainedResponse() {
        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
            """
                flow TestFlow {
                    do bookSlot {
                        service Bookings.BookingService
                        call getBookingTerms
                        on SlotNotBookable call checkBookingHolder
                        on UnknownResponse response BookingRejected
                    }
                    do getBookingTerms {
                        service Facilities.CatalogService
                        response BookingTermsResult
                        response SlotNotBookable
                    }
                    do checkBookingHolder {
                        service Accounts.AccountService
                        response HolderNotEligible
                    }
                    end { completed: BookingRejected }
                }
            """.trimIndent()
        ))

        assertTrue(semanticModel.diagnostics.any {
            it.severity == Severity.ERROR &&
                it.message.contains("UnknownResponse") &&
                it.message.contains("checkBookingHolder")
        }, semanticModel.diagnostics.toString())
    }

    @Test
    fun testAnalyze_AllHandledResponsesBeforeFurtherLinesAddsWarning() {
        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
            """
                flow TestFlow {
                    do bookSlot {
                        service Bookings.BookingService
                        call getBookingTerms
                        on SlotNotBookable, BookingTermsResult response BookingRejected
                        emits Completed
                    }
                    do getBookingTerms {
                        service Facilities.CatalogService
                        response BookingTermsResult
                        response SlotNotBookable
                    }
                    end { completed: Completed }
                }
            """.trimIndent()
        ))

        assertTrue(semanticModel.diagnostics.any {
            it.severity == Severity.WARNING && it.message.contains("subsequent lines are unreachable")
        })
    }

    @Test
    fun testAnalyze_ExhaustiveNestedHandlersBeforeFurtherLinesAddWarning() {
        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
            """
                flow TestFlow {
                    do main {
                        service Test.Main
                        call first
                        on X call second
                        on W, Other response Rejected
                        call tail
                        emits Completed
                    }
                    do first { service Test.First response X }
                    do second { service Test.Second response W response Other }
                    do tail { service Test.Tail response TailDone }
                    end { completed: Completed rejected: Rejected }
                }
            """.trimIndent()
        ))

        assertTrue(semanticModel.diagnostics.any {
            it.severity == Severity.WARNING && it.message.contains("subsequent lines are unreachable")
        }, semanticModel.diagnostics.toString())

        val continuingOuterResponse = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
            """
                flow TestFlow {
                    do main {
                        service Test.Main
                        call first
                        on X call second
                        on W, Other response Rejected
                        call tail
                        emits Completed
                    }
                    do first { service Test.First response X response Happy }
                    do second { service Test.Second response W response Other }
                    do tail { service Test.Tail response TailDone }
                    end { completed: Completed rejected: Rejected }
                }
            """.trimIndent()
        ))
        assertTrue(continuingOuterResponse.diagnostics.none {
            it.severity == Severity.WARNING && it.message.contains("subsequent lines are unreachable")
        }, continuingOuterResponse.diagnostics.toString())
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
    fun testAnalyze_OnAfterAsyncCallAddsDiagnostic() {
        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
            """
                flow TestFlow {
                    when Start do main { service Test.Main async call first on Missing response Rejected emits Completed }
                    do first { service Test.First response X response Happy }
                    end { completed: Completed }
                }
            """.trimIndent()
        ))

        assertTrue(semanticModel.diagnostics.any {
            it.severity == Severity.ERROR && it.message.contains("cannot handle responses from async call")
        })
    }

    @Test
    fun testAnalyze_TerminalHandlerResponsesAreCallableAndValidEndOutcomes() {
        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
            """
                flow TestFlow {
                    start Start {}
                    when Start do main { service Test.Main call book on Rejected emits Done }
                    do book { service Test.Book call first on X response Rejected response Accepted }
                    do first { service Test.First response X response Happy }
                    end { completed: Done rejected: Rejected }
                }
            """.trimIndent()
        ))

        val book = semanticModel.flows.single().commands.first { it.name == "book" }
        assertEquals(listOf("Rejected", "Accepted"), book.responses)
        assertTrue(semanticModel.diagnostics.none { it.severity == Severity.ERROR }, semanticModel.diagnostics.toString())
    }

    @Test
    fun testAnalyze_OccurrenceResponsesMergeAnnotatedTerminalAndDirectResponses() {
        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
            """
                flow TestFlow {
                    when Start do main {
                        service Test.Main
                        call first
                        @outcome(early) on X response Rejected
                        @outcome(accepted) response Accepted
                    }
                    do first { service Test.First response X response Happy }
                    end { completed: Accepted rejected: Rejected }
                }
            """.trimIndent()
        ))

        val responses = semanticModel.flows.single().commands.single { it.name == "main" }
            .occurrences.single().responses
        assertEquals(listOf("Accepted", "Rejected"), responses.map { it.name })
        val rejected = responses.single { it.name == "Rejected" }
        assertEquals("early", rejected.outcome)
        assertEquals("early", rejected.options["outcome"])
        assertNotNull(rejected.sourceRef)
    }

    @Test
    fun testAnalyze_BookSlotFixtureAllowsResponseOnlyEndOutcomes() {
        val semanticModel = ZflSemanticAnalyzer().analyze(
            ZflParser().parseModel(readTestFile("flow/book-slot-flow.zfl"))
        )

        assertTrue(semanticModel.diagnostics.none { it.severity == Severity.ERROR }, semanticModel.diagnostics.toString())
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
                            emits response StockReserved
                            emits response StockConfirmed
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

    @Test
    fun testAnalyze_ScalarAnnotationValuesBecomeText() {
        val condition = analyze(
            """
            systems { S { service Svc } }
            flow F {
                @actor(Customer)
                start A {}
                @if(true)
                when A do x {
                    service S.Svc
                    emits E
                }
                end { completed: E }
            }
            """
        )
        assertEquals("Customer", condition.flows.single().starts.single().actor)
        assertEquals("true", condition.flows.single().policies.single().condition)
        assertTrue(condition.diagnostics.isEmpty(), condition.diagnostics.toString())

        val number = analyze(
            """
            systems { S { service Svc } }
            flow F {
                start A {}
                @if(3)
                when A do x {
                    service S.Svc
                    emits E
                }
                end { completed: E }
            }
            """
        )
        assertEquals("3", number.flows.single().policies.single().condition)
        assertTrue(number.diagnostics.isEmpty(), number.diagnostics.toString())

        val valueless = analyze(
            """
            systems { S { service Svc } }
            flow F {
                start A {}
                @if
                when A do x {
                    service S.Svc
                    emits E
                }
                end { completed: E }
            }
            """
        )
        assertEquals("true", valueless.flows.single().policies.single().condition)
        assertNull(valueless.flows.single().policies.single().compensates)
        assertTrue(valueless.diagnostics.isEmpty(), valueless.diagnostics.toString())
    }

    @Test
    fun testAnalyze_ListAndMapAnnotationValuesWarnAndBecomeNull() {
        val compensates = analyze(
            """
            systems { S { service Svc } }
            flow F {
                start A {}
                @compensates(x, y)
                when A do x {
                    service S.Svc
                    emits E
                }
                end { completed: E }
            }
            """
        )
        assertNull(compensates.flows.single().policies.single().compensates)
        assertSingleValueWarning(compensates, "@compensates")

        val startActor = analyze(
            """
            systems { S { service Svc } }
            flow F {
                @actor(a, b)
                start A {}
                when A do x {
                    service S.Svc
                    emits E
                }
                end { completed: E }
            }
            """
        )
        assertNull(startActor.flows.single().starts.single().actor)
        assertTrue(startActor.actors.isEmpty())
        assertSingleValueWarning(startActor, "@actor")

        val commandActor = analyze(
            """
            systems { S { service Svc } }
            flow F {
                @actor(a, b)
                do x {
                    service S.Svc
                    emits E
                }
                end { completed: E }
            }
            """
        )
        assertNull(commandActor.flows.single().commands.single().actor)
        assertSingleValueWarning(commandActor, "@actor")

        val mapCondition = analyze(
            """
            systems { S { service Svc } }
            flow F {
                start A {}
                @if(k: v)
                when A do x {
                    service S.Svc
                    emits E
                }
                end { completed: E }
            }
            """
        )
        assertNull(mapCondition.flows.single().policies.single().condition)
        assertSingleValueWarning(mapCondition, "@if")
    }

    private fun assertSingleValueWarning(model: ZflSemanticModel, annotation: String) {
        assertTrue(model.diagnostics.none { it.severity == Severity.ERROR }, model.diagnostics.toString())
        val warning = model.diagnostics.single { it.severity == Severity.WARNING }
        assertEquals("Annotation '$annotation' expects a single value.", warning.message)
    }

    private fun analyze(zfl: String): ZflSemanticModel =
        ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zfl.trimIndent()))

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
                emits response StockReserved
                emits response StockUnavailable
            }

            do createOrder {
                service OrdersCheckout.OrdersCheckoutService
                emits OrderCreated
                response StockUnavailable
            }

            end {
                completed: OrderCreated
                stockGone: StockUnavailable
            }
        }
    """.trimIndent()
}
