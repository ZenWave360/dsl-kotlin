package io.zenwave360.zfl.internal

import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.zfl.ZflModel
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.zdl.internal.readTestFile
import kotlin.test.*

class ZflListenerKotlinTest {

    @Test
    fun parseZfl_Subscriptions() {
        val model = parseZfl("flow/subscriptions.zfl")

        // Print the model for debugging
        // println(printAsJson(model))

        // Test imports
        assertEquals(2, (JSONPath.get(model, "$.imports") as? List<*>)?.size ?: 0)
        assertEquals("subscriptions", JSONPath.get(model, "$.imports[0].key"))
        assertEquals(
            "http://localhost:8080/subscription/model.zdl",
            JSONPath.get(model, "$.imports[0].value")
        )
        assertEquals("payments", JSONPath.get(model, "$.imports[1].key"))
        assertEquals("com.example.domain:payments:RELEASE", JSONPath.get(model, "$.imports[1].value"))

        // Test flow
        assertEquals(1, (JSONPath.get(model, "$.flows") as? Map<*, *>)?.size ?: 0)
        assertEquals("PaymentsFlow", JSONPath.get(model, "$.flows.PaymentsFlow.name"))
        assertEquals("PaymentsFlow", JSONPath.get(model, "$.flows.PaymentsFlow.className"))
        assertNotNull(JSONPath.get(model, "$.flows.PaymentsFlow.javadoc"))

        // Test systems
        assertEquals(3, (JSONPath.get(model, "$.systems") as? Map<*, *>)?.size ?: 0)

        // Test Subscription system
        assertEquals("Subscription", JSONPath.get(model, "$.systems.Subscription.name"))
        assertEquals(
            "subscription/model.zdl",
            JSONPath.get(model, "$.systems.Subscription.options.zdl")
        )
        assertEquals(
            1,
            (JSONPath.get(model, "$.systems.Subscription.services") as? Map<*, *>)?.size
                ?: 0
        )
        assertEquals(
            "SubscriptionService",
            JSONPath.get(model, "$.systems.Subscription.services.SubscriptionService.name")
        )
        assertEquals(
            setOf("renewSubscription", "suspendSubscription", "cancelRenewal"),
            JSONPath.get(
                model,
                "$.systems.Subscription.services.SubscriptionService.commands"
            )
        )

        // Test Payments system
        assertEquals("Payments", JSONPath.get(model, "$.systems.Payments.name"))
        assertNull(JSONPath.get(model, "$.systems.Payments.zdl"))

        // Test Billing system
        assertEquals("Billing", JSONPath.get(model, "$.systems.Billing.name"))

        // Test start events
        assertEquals(3, (JSONPath.get(model, "$.flows.PaymentsFlow.starts") as? Map<*, *>)?.size ?: 0)

        // Test CustomerRequestsSubscriptionRenewal start
        assertEquals(
            "CustomerRequestsSubscriptionRenewal",
            JSONPath.get(model, "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.name")
        )
        assertEquals(
            "Customer",
            JSONPath.get(
                model,
                "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.options.actor"
            )
        )
        assertEquals(
            3,
            (JSONPath.get(
                model,
                "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.fields"
            ) as? Map<*, *>)?.size ?: 0
        )
        assertEquals(
            "String",
            JSONPath.get(
                model,
                "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.fields.subscriptionId.type"
            )
        )
        assertEquals(
            "String",
            JSONPath.get(
                model,
                "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.fields.customerId.type"
            )
        )
        assertEquals(
            "String",
            JSONPath.get(
                model,
                "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.fields.paymentMethodId.type"
            )
        )

        // Test BillingCycleEnded start
        assertEquals(
            "BillingCycleEnded",
            JSONPath.get(model, "$.flows.PaymentsFlow.starts.BillingCycleEnded.name")
        )
        assertEquals(
            "end of month",
            JSONPath.get(model, "$.flows.PaymentsFlow.starts.BillingCycleEnded.options.time")
        )
        assertEquals(
            1,
            (JSONPath.get(model, "$.flows.PaymentsFlow.starts.BillingCycleEnded.fields") as? Map<*, *>)?.size
                ?: 0
        )

        // Test PaymentTimeout start
        assertEquals("PaymentTimeout", JSONPath.get(model, "$.flows.PaymentsFlow.starts.PaymentTimeout.name"))
        assertEquals(
            "5 minutes after SubscriptionRenewed and not PaymentSucceeded or PaymentFailed",
            JSONPath.get(model, "$.flows.PaymentsFlow.starts.PaymentTimeout.options.time")
        )

        // Test when blocks
        val whens =
            JSONPath.get(model, "$.flows.PaymentsFlow.whens", emptyList<Map<String, Any?>>()) as? List<*>
                ?: emptyList<Any?>()
        assertEquals(6, whens.size)

        // Test first when block
        assertEquals(listOf("CustomerRequestsSubscriptionRenewal"), JSONPath.get(whens[0], "$.triggers"))
        assertEquals("renewSubscription", JSONPath.get(whens[0], "$.command"))
        assertEquals(listOf("SubscriptionRenewed"), JSONPath.get(whens[0], "$.events"))

        // Test second when block
        assertEquals(listOf("SubscriptionRenewed"), JSONPath.get(whens[1], "$.triggers"))
        assertEquals("chargePayment", JSONPath.get(whens[1], "$.command"))
        assertEquals(listOf("PaymentSucceeded", "PaymentFailed"), JSONPath.get(whens[1], "$.events"))

        // Test third when block with if/else
        assertEquals(listOf("PaymentFailed"), JSONPath.get(whens[2], "$.triggers"))
        val ifText = JSONPath.get(whens[2], "$.options.if") as String?
        assertEquals("less than 3 attempts", ifText)
        assertEquals("retryPayment", JSONPath.get(whens[2], "$.command"))
        assertEquals(listOf("PaymentRetryScheduled"), JSONPath.get(whens[2], "$.events"))

        assertEquals(listOf("PaymentFailed"), JSONPath.get(whens[3], "$.triggers"))
        val ifText2 = JSONPath.get(whens[3], "$.options.if") as String?
        assertEquals("3 or more attempts", ifText2)
        assertEquals("suspendSubscription", JSONPath.get(whens[3], "$.command"))
        assertEquals(listOf("SubscriptionSuspended"), JSONPath.get(whens[3], "$.events"))

        // Test fourth when block with AND trigger
        assertEquals(listOf("PaymentSucceeded", "BillingCycleEnded"), JSONPath.get(whens[4], "$.triggers"))
        assertEquals("recordPayment", JSONPath.get(whens[4], "$.command"))
        assertEquals(listOf("PaymentRecorded"), JSONPath.get(whens[4], "$.events"))

        // Test fifth when block
        assertEquals(listOf("PaymentTimeout"), JSONPath.get(whens[5], "$.triggers"))
        assertEquals("cancelRenewal", JSONPath.get(whens[5], "$.command"))
        assertEquals(listOf("RenewalCancelled"), JSONPath.get(whens[5], "$.events"))

        // Test end block
        @Suppress("UNCHECKED_CAST")
        val outcomes = JSONPath.get(
            model,
            "$.flows.PaymentsFlow.end.outcomes",
            emptyMap<String, List<String>>()
        ) as? Map<String, List<String>> ?: emptyMap()
        assertEquals(3, outcomes.size)
        assertEquals("PaymentRecorded", outcomes["completed"]?.get(0))
        assertEquals("SubscriptionSuspended", outcomes["suspended"]?.get(0))
        assertEquals("RenewalCancelled", outcomes["cancelled"]?.get(0))
    }

    private fun parseZfl(fileName: String): ZflModel {
        val content = readTestFile(fileName)
        return ZflParser().parseModel(content)
    }

    private fun printMapAsJson(map: Map<String, Any?>, indent: String = ""): String {
        return buildString {
            append("{\n")
            map.entries.forEachIndexed { index, (key, value) ->
                append("$indent  \"$key\": ")
                when (value) {
                    is IntArray -> append(value.contentToString())
                    is Map<*, *> -> append(printMapAsJson(value as Map<String, Any?>, "$indent  "))
                    is List<*> -> append(printListAsJson(value, "$indent  "))
                    is String -> append("\"$value\"")
                    null -> append("null")
                    else -> append("\"$value\"")
                }
                if (index < map.size - 1) append(",")
                append("\n")
            }
            append("$indent}")
        }
    }

    private fun printListAsJson(list: List<*>, indent: String = ""): String {
        return buildString {
            append("[\n")
            list.forEachIndexed { i, item ->
                append("$indent  ")
                when (item) {
                    is Map<*, *> -> append(printMapAsJson(item as Map<String, Any?>, "$indent  "))
                    is List<*> -> append(printListAsJson(item, "$indent  "))
                    is String -> append("\"$item\"")
                    null -> append("null")
                    else -> append("\"$item\"")
                }
                if (i < list.size - 1) append(",")
                append("\n")
            }
            append("$indent]")
        }
    }
}
