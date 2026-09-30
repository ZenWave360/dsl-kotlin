package io.zenwave360.language.zdl.e2e

import io.zenwave360.language.readTestFile
import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises validation through the public ZDL parser, including listener and post-processing. */
class ZdlValidationE2ETest {

    @Test
    fun syntaxErrorsAreStructuredAndDoNotCascadeIntoSemanticProblems() {
        val model = ZdlParser().parseModel(
            """
                entity Broken {
                    name String
                    unexpected ???
                }
            """.trimIndent()
        )

        assertEquals(1, model.getProblems().size)
        val problem = model.getProblems().single()
        assertEquals("syntax", problem["path"])
        val location = problem["location"] as IntArray
        assertTrue(location[2] >= 1)
        assertTrue(location[3] >= 0)
        assertTrue((problem["message"] as String).isNotBlank())
    }

    @Test
    fun validServiceLifecyclePassesEndToEnd() {
        val model = ZdlParser().parseModel(readTestFile("service-lifecycle.zdl"))

        assertTrue(model.getProblems().isEmpty(), "Expected a valid lifecycle model: ${model.getProblems()}")
    }

    @Test
    fun serviceTransitionsMayDeclareOnlyFromOrOnlyTo() {
        val model = ZdlParser().parseModel(
            """
                @aggregate
                @lifecycle(field: status, initial: DRAFT)
                entity Order {
                    status OrderStatus required
                }

                enum OrderStatus { DRAFT, PLACED }
                input ChangeInput { note String }

                service OrderService for (Order) {
                    @transition(to: DRAFT)
                    createOrder(ChangeInput) Order

                    @transition(from: PLACED)
                    deleteOrder(id) Order
                }
            """.trimIndent()
        )

        assertTrue(model.getProblems().isEmpty(), "Expected one-sided transitions to be valid: ${model.getProblems()}")
    }

    @Test
    fun invalidServiceTransitionsReportEveryBusinessRule() {
        val model = ZdlParser().parseModel(
            """
                @aggregate
                @lifecycle(field: status, initial: DRAFT)
                entity Order {
                    status OrderStatus required
                }

                @aggregate
                @lifecycle(field: status, initial: OPEN)
                entity Invoice {
                    status InvoiceStatus required
                }

                @aggregate
                entity Item {
                    name String required
                }

                enum OrderStatus { DRAFT, PLACED }
                enum InvoiceStatus { OPEN, PAID }

                input ChangeInput { note String }

                // id-less from transition (correlated by input): valid, no problem reported
                service CorrelatedService for (Order) {
                    @transition(from: [DRAFT, PLACED], to: PLACED)
                    releaseOrders(ChangeInput) Order[]
                }

                // id-less from transition: remaining rules still apply
                service IdlessInvalidStateService for (Order) {
                    @transition(from: GONE, to: PLACED)
                    releaseOrders(ChangeInput) Order[]
                }

                service InvalidStateService for (Order) {
                    @transition(from: [DRAFT, UNKNOWN], to: MISSING)
                    changeOrder(id, ChangeInput) Order
                }

                service AmbiguousService for (Order, Invoice) {
                    @transition(from: DRAFT, to: PLACED)
                    changeSomething(id, ChangeInput) Item
                }

                service NoLifecycleService for (Item) {
                    @transition(from: DRAFT, to: PLACED)
                    changeItem(id, ChangeInput) Item
                }

                service ParameterResolvedService for (Order, Invoice) {
                    @transition(from: DRAFT, to: PLACED)
                    changeOrder(id, Order)
                }
            """.trimIndent()
        )

        val messages = model.getProblems().map { it["message"] as String }
        assertEquals(
            listOf(
                "GONE is not a valid state value",
                "UNKNOWN is not a valid state value",
                "MISSING is not a valid state value",
                "cannot determine target entity for state transition",
                "entity Item does not have a @lifecycle annotation",
            ),
            messages,
        )
    }

    @Test
    fun invalidEntityAndAggregateLifecyclesAreReportedEndToEnd() {
        val model = ZdlParser().parseModel(
            """
                @aggregate
                @lifecycle(field: missing, initial: DRAFT)
                entity MissingFieldOrder {
                    status OrderStatus required
                }

                @aggregate
                @lifecycle(field: status, initial: DRAFT)
                entity NonEnumOrder {
                    status String required
                }

                @aggregate
                @lifecycle(field: status, initial: UNKNOWN)
                entity InvalidInitialOrder {
                    status OrderStatus required
                }

                @aggregate
                @lifecycle(field: status, initial: DRAFT)
                entity Order {
                    status OrderStatus required
                }

                enum OrderStatus { DRAFT, PLACED }
                input ChangeInput { note String }
                event OrderChanged { orderId String }

                aggregate OrderAggregate (Order) {
                    @transition(from: [DRAFT, UNKNOWN], to: MISSING)
                    changeOrder(ChangeInput) withEvents OrderChanged
                }
            """.trimIndent()
        )

        val messages = model.getProblems().map { it["message"] as String }
        assertTrue(messages.count { it.contains("not a field") } >= 1, messages.toString())
        assertTrue(messages.count { it.contains("not an enum") } >= 1, messages.toString())
        assertTrue(messages.count { it.contains("UNKNOWN is not a valid value") } >= 1, messages.toString())
        assertTrue(messages.any { it == "UNKNOWN is not a valid state value" }, messages.toString())
        assertTrue(messages.any { it == "MISSING is not a valid state value" }, messages.toString())
    }

    @Test
    fun invalidInputAndOutputFieldTypesAreReportedAtTheirPublicPaths() {
        val model = ZdlParser().parseModel(
            """
                input BrokenInput {
                    value MissingType
                }

                output BrokenOutput {
                    value MissingType
                }
            """.trimIndent()
        )

        assertEquals(
            listOf(
                "inputs.BrokenInput.fields.value.type",
                "outputs.BrokenOutput.fields.value.type",
            ),
            model.getProblems().map { it["path"] },
        )
        assertTrue(model.getProblems().all { it["message"] == "MissingType is not a valid type" })
    }

    @Test
    fun validCrossModelFieldReferencesPassEndToEnd() {
        val model = ZdlParser().parseModel(
            """
                entity Order {
                    status OrderStatus
                }
                enum OrderStatus { CREATED }

                input CreateOrder {
                    order Order
                    retry CreateOrder
                }
                output OrderResult {
                    order Order
                    request CreateOrder
                    previous OrderResult
                }
                event OrderChanged {
                    order Order
                    previous OrderChanged
                }
            """.trimIndent()
        )

        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())
    }
}
