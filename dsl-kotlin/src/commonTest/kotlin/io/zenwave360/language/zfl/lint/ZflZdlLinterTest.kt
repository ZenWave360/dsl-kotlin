package io.zenwave360.language.zfl.lint

import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zfl.semantic.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cross-file checks of a flow's view, timers and expected end states against the ZDLs of its systems. */
class ZflZdlLinterTest {

    private val ordersZdl = """
        @aggregate
        @lifecycle(field: status, initial: CREATED)
        entity Order {
            orderId String required unique
            customerId String
            status OrderStatus
            expiresAt Instant
            note String
        }

        enum OrderStatus {
            CREATED
            CONFIRMED
            CANCELLED
            ARCHIVED
        }

        input PlaceOrderInput {
            customerId String
        }

        input ConfirmOrderInput {
            orderId String
            couponCode String
        }

        service OrdersService for (Order) {
            @transition(to: CREATED)
            placeOrder(PlaceOrderInput) Order withEvents OrderCreated
            @transition(from: CREATED, to: CONFIRMED)
            confirmOrder(ConfirmOrderInput) Order withEvents OrderConfirmed
            @transition(from: [CREATED, CONFIRMED], to: CANCELLED)
            cancelOrder(ConfirmOrderInput) Order withEvents OrderCancelled
        }

        event OrderCreated {
            orderId String
            customerId String
        }

        event OrderConfirmed {
            orderId String
        }

        event OrderCancelled {
            orderId String
        }
    """

    private val paymentsZdl = """
        @aggregate
        @lifecycle(field: status, initial: PENDING)
        entity Payment {
            orderId String required unique
            status PaymentStatus
        }

        enum PaymentStatus {
            PENDING
            AUTHORIZED
            DECLINED
        }

        @aggregate
        entity Receipt {
            orderId Long
        }

        @aggregate
        entity Notification {
            orderId String
        }

        input AuthorizePaymentInput {
            orderId String
        }

        service PaymentsService for (Payment) {
            @transition(to: [AUTHORIZED, DECLINED])
            authorizePayment(AuthorizePaymentInput) Payment withEvents [PaymentAuthorized | PaymentDeclined]
            refundPayment(AuthorizePaymentInput) Payment withEvents PaymentRefunded
        }

        event PaymentAuthorized {
            paymentId Long
        }

        event PaymentDeclined {
            orderId String
            reason String
        }

        event PaymentRefunded {
            reason String
        }
    """

    private fun flow(view: String, extra: String = "", end: String = "completed: OrderConfirmed") = """
        systems {
            Orders { service OrdersService { commands: placeOrder, confirmOrder, cancelOrder } }
            Payments { service PaymentsService { commands: authorizePayment } }
            Shipping { service ShippingService { commands: ship } }
        }

        flow OrderFlow {
            $view

            start PlaceOrder {
                customerId String
                couponCode String
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
            $extra

            end {
                $end
                cancelled: OrderCancelled
            }
        }
    """

    private fun lint(zfl: String): List<String> {
        val zdls = mapOf("Orders" to ZdlParser().parseModel(ordersZdl), "Payments" to ZdlParser().parseModel(paymentsZdl))
        zdls.values.forEach { assertEquals(emptyList(), it.getProblems().filter { p -> p["severity"] != "WARNING" }.map { p -> p["message"] }) }
        val result = ZflLinter().lint(zfl, "flow.zfl")
        assertEquals(emptyList(), result.diagnostics.filter { it.severity == Severity.ERROR }.map { it.message })
        return ZflZdlLinter().lint(result.model, zdls).map { "${it.severity}: ${it.message}" }
    }

    private fun assertWarns(expected: String, actual: List<String>) =
        assertTrue("WARNING: $expected" in actual, "Expected warning:\n  $expected\nActual:\n  ${actual.joinToString("\n  ")}")

    private val goodView = """
        view OrderView by Order.orderId {
            Order
            Payment.orderId
            Notification[].orderId
        }
    """

    @Test
    fun aConsistentFlowHasNoWarnings() {
        val actual = lint(
            flow(
                goodView,
                extra = """timer at Order.expiresAt do cancelOrder""",
                end = "completed: OrderConfirmed { Order: CONFIRMED, Payment: AUTHORIZED, Notification: none }",
            ).replace("couponCode String", "")
        )
        assertEquals(listOf("INFO: System 'Shipping' has no ZDL: its aggregates, events and commands are not checked."), actual)
    }

    @Test
    fun membersExistAndPointToTheKeyWithItsType() {
        val actual = lint(
            flow(
                """
                view OrderView by Order.orderId {
                    Order
                    Payment.paymentRef
                    Receipt.orderId
                    Notification.orderId
                    Invoice.orderId
                }
                """
            )
        )
        assertWarns("Path 'Payment.paymentRef' of view 'OrderView' does not exist: 'Payment' has no field 'paymentRef'.", actual)
        assertWarns("Path 'Receipt.orderId' of view 'OrderView' is Long, but the key is String.", actual)
        assertWarns("'Notification.orderId' is not unique, so there may be several Notification per key: write 'Notification[]'.", actual)
        assertWarns("Aggregate 'Invoice' of view 'OrderView' is in no ZDL of the flow's systems.", actual)

        assertWarns(
            "Key 'Order.note' of view 'OrderView' is not unique.",
            lint(flow("view OrderView by Order.note { Order }")),
        )
        assertWarns(
            "Key 'Order.number' of view 'OrderView' does not exist: 'Order' has no field 'number'.",
            lint(flow("view OrderView by Order.number { Order }")),
        )
    }

    @Test
    fun eventsCorrelateByTheKeyOrAMembersId() {
        val actual = lint(flow(goodView))
        // PaymentAuthorized carries paymentId: Payment.id, which reaches the key through Payment.orderId
        assertTrue(actual.none { "Event 'PaymentAuthorized'" in it }, actual.joinToString("\n"))
        val refund = lint(flow(goodView, extra = "when OrderCancelled do refundPayment { service Payments.PaymentsService\n emits PaymentRefunded }"))
        assertWarns("Event 'PaymentRefunded' cannot be correlated with view 'OrderView': it carries none of 'orderId', 'paymentId', 'notificationId'.", refund)

        val undeclared = lint(flow(goodView, extra = "when OrderConfirmed do ship { service Shipping.ShippingService\n emits OrderShipped }"))
        assertWarns("Event 'OrderShipped' cannot be correlated: no ZDL of the flow's systems declares it.", undeclared)
    }

    @Test
    fun aStartFieldThatALaterCommandNeedsReachesIt() {
        val actual = lint(flow(goodView))
        assertWarns(
            "Start field 'couponCode' is lost after 'authorizePayment': 'confirmOrder' needs it, but 'PaymentAuthorized' does not carry it and no aggregate of view 'OrderView' has it.",
            actual,
        )
        // customerId is in Order, a view aggregate
        assertTrue(actual.none { "'customerId' is lost" in it }, actual.joinToString("\n"))
    }

    @Test
    fun aTimerFiresAtADateOrTimeOfAViewMember() {
        val notADate = lint(flow(goodView, extra = "timer at Order.note do cancelOrder"))
        assertWarns("Timer of 'cancelOrder' fires at 'Order.note', which is String, not a date or time.", notADate)
        val missing = lint(flow(goodView, extra = "timer at Order.closesAt do cancelOrder"))
        assertWarns("Timer of 'cancelOrder' fires at 'Order.closesAt', which does not exist: 'Order' has no field 'closesAt'.", missing)
    }

    @Test
    fun expectedStatesBelongToTheLifecycleAndAreReached() {
        val actual = lint(
            flow(
                goodView,
                end = "completed: OrderConfirmed { Order: [CONFIRMED, SHIPPED], Payment: [AUTHORIZED, none], Notification: SENT }\n" +
                    "archived: OrderCreated { Order: ARCHIVED }",
            )
        )
        assertWarns("Outcome 'completed' expects 'Order' to be SHIPPED, which is not a state of its lifecycle (CREATED, CONFIRMED, CANCELLED, ARCHIVED).", actual)
        assertWarns("Outcome 'completed' expects 'Notification' to be SENT, but 'Notification' has no @lifecycle.", actual)
        assertWarns("Outcome 'archived' expects 'Order' to be ARCHIVED, but no command of its ZDL transitions to ARCHIVED.", actual)
        assertTrue(actual.none { "AUTHORIZED" in it || "'none'" in it || "be CONFIRMED" in it }, actual.joinToString("\n"))
    }
}
