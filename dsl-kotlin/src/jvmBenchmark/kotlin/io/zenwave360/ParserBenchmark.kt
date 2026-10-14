package io.zenwave360

import io.zenwave360.language.zfl.ZflParser
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1) // Warm up JIT
@Measurement(iterations = 5, time = 1)
open class ParserBenchmark {

    private lateinit var input: String

    @Setup
    fun setup() {
        input = """
            @import(subscriptions: "http://localhost:8080/subscription/model.zdl")
            @import(payments: "com.example.domain:payments:RELEASE")

            config {
                basePackage "io.zenwave360.example.subscriptions"
                persistence mongodb
            }

            systems {
                @zdl("subscription/model.zdl")
                Subscription {
                    service SubscriptionService
                }

                @zdl("payments/model.zdl")
                Payments {
                    service PaymentService
                }

                @zdl("billing/model.zdl")
                Billing {
                    service BillingService
                }
            }

            /**
             * Payment Flow for Subscription Renewals
             */
            flow PaymentsFlow {

                @actor(Customer)
                start CustomerRequestsSubscriptionRenewal {
                    subscriptionId String
                    customerId String
                    paymentMethodId String
                }

                @time("end of month")
                start BillingCycleEnded {
                    billingPeriod String
                }

                @time("5 minutes after SubscriptionRenewed and not PaymentSucceeded or PaymentFailed")
                start PaymentTimeout {
                }

                when CustomerRequestsSubscriptionRenewal {
                    service Subscription.SubscriptionService
                    command renewSubscription
                    event SubscriptionRenewed
                }

                when SubscriptionRenewed {
                    service Payments.PaymentService
                    command chargePayment
                    event PaymentSucceeded
                    event PaymentFailed
                }

                @if("less than 3 attempts")
                when PaymentFailed {
                    service Payments.PaymentService
                    command retryPayment
                    event PaymentRetryScheduled
                }

                @if("3 or more attempts")
                when PaymentFailed {
                    service Subscription.SubscriptionService
                    command suspendSubscription
                    event SubscriptionSuspended
                }

                when PaymentSucceeded and BillingCycleEnded {
                    service Payments.PaymentService
                    command recordPayment
                    event PaymentRecorded
                }

                when PaymentTimeout {
                    service Subscription.SubscriptionService
                    command cancelRenewal
                    event RenewalCancelled
                }

                end {
                    completed: PaymentRecorded
                    suspended: SubscriptionSuspended
                    cancelled: RenewalCancelled
                }
            }
        """.trimIndent()
    }

    @Benchmark
    fun testKotlinRuntime() {
        ZflParser().parseModel(input)
    }
}
