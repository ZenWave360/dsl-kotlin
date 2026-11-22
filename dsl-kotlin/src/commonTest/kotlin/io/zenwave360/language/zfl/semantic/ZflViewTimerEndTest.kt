package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.lint.ZflLinter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Experimental `view`, `timer` and expected end states, and the single-file checks on them. */
class ZflViewTimerEndTest {

    private fun analyze(zfl: String) = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zfl))

    private fun warnings(zfl: String) = analyze(zfl).diagnostics.filter { it.severity == Severity.WARNING }.map { it.message }

    private fun assertWarns(expected: String, actual: List<String>) =
        assertTrue(expected in actual, "Expected warning:\n  $expected\nActual:\n  ${actual.joinToString("\n  ")}")

    private val steps = """
        start PlaceOrder {
            customerId String
        }

        when PlaceOrder do placeOrder {
            service Orders.OrdersService
            emits OrderCreated
        }

        when OrderCreated do authorizePayment {
            service Payments.PaymentsService
            emits PaymentAuthorized
            @failure
            emits PaymentDeclined
        }

        when PaymentAuthorized do confirmOrder {
            service Orders.OrdersService
            emits OrderConfirmed
        }

        when PaymentDeclined do cancelOrder {
            service Orders.OrdersService
            emits OrderCancelled
        }
    """

    private val flow = """
        flow OrderFlow {
            view OrderView by Order.id {
                Order
                Payment.orderId
                Notification[].orderId
                Fulfillment.order.id
            }
            $steps

            /** Gives up on an order nobody paid. */
            @compensates(placeOrder)
            timer after OrderCreated "10 minutes" unless PaymentAuthorized, PaymentDeclined
                do expireOrder {
                    service Orders.OrdersService
                    emits OrderExpired
                }

            timer at Order.expiresAt do archiveOrder

            do archiveOrder {
                service Orders.OrdersService
            }

            rules (OrderView) {
                noChargeOnCancel "A cancelled order has no authorized payment"
            }

            end {
                completed: OrderConfirmed
                    { Order: CONFIRMED, Payment: AUTHORIZED }
                cancelled: OrderCancelled
                    { Order: CANCELLED, Payment: [DECLINED, none], }
                cancelled: OrderExpired { Order: CANCELLED, Payment: none }
            }
        }
    """

    @Test
    fun viewTimersAndEndStatesBuildTheModel() {
        val model = analyze(flow)
        val orderFlow = model.flows.single()

        val view = assertNotNull(orderFlow.view)
        assertEquals("OrderView", view.name)
        assertEquals("Order", view.keyAggregate)
        assertEquals(listOf("id"), view.keyPath)
        assertEquals(listOf("Order", "Payment", "Notification", "Fulfillment"), view.members.map { it.aggregate })
        assertEquals(listOf(emptyList(), listOf("orderId"), listOf("orderId"), listOf("order", "id")), view.members.map { it.path })
        assertEquals(listOf(false, false, true, false), view.members.map { it.many })

        val (expire, archive) = orderFlow.timers
        assertEquals("expireOrder", expire.command)
        assertEquals("OrderCreated", expire.after)
        assertEquals("10 minutes", expire.duration)
        assertEquals(listOf("PaymentAuthorized", "PaymentDeclined"), expire.unless)
        assertEquals("Gives up on an order nobody paid.", expire.description)
        assertEquals("placeOrder", expire.options["compensates"])
        assertEquals("archiveOrder", archive.command)
        assertEquals("Order.expiresAt", archive.at)
        // a timer body defines its command
        assertEquals(listOf("OrderExpired"), orderFlow.commands.single { it.name == "expireOrder" }.emits.map { it.eventName })

        assertEquals("OrderView", orderFlow.rules.single().aggregate)

        // one outcome per name, as the diagrams draw them; one variant per line
        assertEquals(mapOf("completed" to listOf("OrderConfirmed"), "cancelled" to listOf("OrderCancelled", "OrderExpired")), orderFlow.end.endOutcomes)
        val cancelled = orderFlow.end.outcomes.single { it.name == "cancelled" }
        assertEquals(listOf(listOf("OrderCancelled"), listOf("OrderExpired")), cancelled.variants.map { it.events })
        assertEquals(mapOf("Order" to listOf("CANCELLED"), "Payment" to listOf("DECLINED", ZflEndVariant.NONE)), cancelled.variants[0].expectedStates)
        assertEquals(listOf("OrderCancelled", "OrderExpired"), cancelled.events)

        assertEquals(emptyList(), model.diagnostics.map { it.message })
    }

    @Test
    fun flowsWithoutViewOrTimerAndEndWithoutStatesStayValid() {
        val model = analyze(
            """
            flow OrderFlow {
                $steps

                @time("whenever the moon is full")
                when OrderConfirmed do archiveOrder {
                    service Orders.OrdersService
                }

                end {
                    completed: OrderConfirmed
                    cancelled: OrderCancelled
                }
            }
            """
        )
        assertEquals(emptyList(), model.diagnostics.map { it.message })
        assertEquals("whenever the moon is full", model.flows.single().policies.single { it.command == "archiveOrder" }.timer)
        val completed = model.flows.single().end.outcomes.first().variants.single()
        assertEquals(listOf("OrderConfirmed"), completed.events)
        assertEquals(emptyMap(), completed.expectedStates)
    }

    @Test
    fun viewIsFirstAndOnceWithItsRootAndAPathForEveryOtherMember() {
        val actual = warnings(
            """
            flow OrderFlow {
                $steps
                view OrderView by Order.id {
                    Order.id
                    Payment
                    Payment.orderId
                }
                view Other by Payment.id { Payment }
                end { completed: OrderConfirmed }
            }
            """
        )
        assertWarns("View 'OrderView' must be the first block of flow 'OrderFlow'.", actual)
        assertWarns("Flow 'OrderFlow' declares more than one view; only 'OrderView' is used.", actual)
        assertWarns("Root 'Order' of view 'OrderView' takes no path: it holds the key.", actual)
        assertWarns("Member 'Payment' of view 'OrderView' needs the path of a field pointing to the key.", actual)
        assertWarns("Aggregate 'Payment' is listed more than once in view 'OrderView'.", actual)

        assertWarns("View 'V' must list its root 'Order' as a member.", warnings("flow F { view V by Order.id { Payment.orderId } }"))
        assertWarns("Root 'Order' of view 'V' is one per instance; it cannot be '[]'.", warnings("flow F { view V by Order.id { Order[] } }"))
    }

    @Test
    fun timersUseFlowEventsADefinedCommandAndAViewDatum() {
        val actual = warnings(
            """
            flow OrderFlow {
                $steps
                timer after OrderShipped "1 day" unless PaymentAuthorized, PaymentLost do remind
                timer at Order.expiresAt do confirmOrder
                end { completed: OrderConfirmed }
            }
            """
        )
        assertWarns("Timer of 'remind' is armed by 'OrderShipped', which flow 'OrderFlow' does not emit.", actual)
        assertWarns("Timer of 'remind' is disarmed by 'PaymentLost', which flow 'OrderFlow' does not emit.", actual)
        assertWarns("Timer runs 'remind', which flow 'OrderFlow' does not define.", actual)
        assertWarns("Timer of 'confirmOrder' fires at 'Order.expiresAt', but flow 'OrderFlow' has no view.", actual)
        assertTrue(actual.none { "'remind' is never triggered" in it }, "a timer triggers its command")

        assertWarns(
            "Timer of 'confirmOrder' fires at 'Shipment.at', but 'Shipment' is not a member of view 'V'.",
            warnings("flow F { view V by Order.id { Order } $steps timer at Shipment.at do confirmOrder }"),
        )
    }

    @Test
    fun expectedStatesNameViewMembersAndEventsEndOneOutcomeLine() {
        val actual = warnings(
            """
            flow OrderFlow {
                view OrderView by Order.id { Order }
                $steps
                end {
                    completed: OrderConfirmed { Order: CONFIRMED, Order: CREATED, Invoice: SENT }
                    cancelled: OrderCancelled, OrderConfirmed
                    cancelled: OrderCancelled
                }
            }
            """
        )
        assertWarns("Outcome 'completed' declares the state of 'Order' more than once.", actual)
        assertWarns("Outcome 'completed' expects a state of 'Invoice', which is not a member of view 'OrderView'.", actual)
        assertWarns("Event 'OrderConfirmed' ends both outcome 'completed' and outcome 'cancelled'.", actual)
        assertWarns("Event 'OrderCancelled' ends more than one line of outcome 'cancelled'.", actual)

        assertWarns(
            "Outcome 'completed' declares expected states, but flow 'F' has no view.",
            warnings("flow F { $steps end { completed: OrderConfirmed { Order: CONFIRMED } cancelled: OrderCancelled } }"),
        )
    }

    @Test
    fun aFailureEventIsConsumedOrEndsTheFlow() {
        val actual = warnings(
            """
            flow OrderFlow {
                $steps
                when OrderConfirmed do shipOrder {
                    service Orders.OrdersService
                    @failure
                    emits ShippingFailed
                    @failure
                    emits ShippingLost
                    @failure
                    emits ShippingDelayed
                }
                timer after ShippingDelayed "1 day" do confirmOrder
                end {
                    completed: OrderConfirmed
                    lost: ShippingLost
                }
            }
            """
        )
        assertWarns("Failure event 'ShippingFailed' of flow 'OrderFlow' has no consumer and is not an end.", actual)
        assertTrue(actual.none { "'ShippingLost'" in it || "'ShippingDelayed'" in it || "'PaymentDeclined'" in it }, actual.joinToString("\n"))
    }

    @Test
    fun singleFileLinterReportsSyntaxErrorsAsErrorsAndChecksAsWarnings() {
        val result = ZflLinter().lint(
            """
            flow F {
                view V by Order { Order }
                end { completed: Done { Order: } }
            }
            """,
            "f.zfl",
        )
        val errors = result.diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isNotEmpty(), "syntax errors are errors")
        assertEquals("f.zfl", errors.first().sourceRef?.file)

        val clean = ZflLinter().lint(flow, "flow.zfl")
        assertEquals(emptyList(), clean.diagnostics.filter { it.severity == Severity.ERROR }.map { it.message })
        assertEquals("OrderView", clean.model.flows.single().view?.name)
    }
}
