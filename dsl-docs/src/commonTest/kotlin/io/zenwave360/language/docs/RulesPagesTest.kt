package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertTrue

/** ZFL-R11 and ZFL-R12 in the generated pages: the rules of a flow and a module, and the rules of each command. */
class RulesPagesTest {

    private val zfl = """
        flow PlaceOrder {
            rules (Order) {
                minimumAmount "At least 10 EUR"
                stock-first "Stock first"
            }
            start PlaceOrderRequested {}
            @rules(minimumAmount, "stock-first")
            when PlaceOrderRequested do placeOrder {
                service Orders.OrderService
                emits OrderPlaced
            }
            end { completed: OrderPlaced }
        }
    """.trimIndent()

    private val zdl = """
        rules (Order) {
            minimumAmount "At least 10 EUR"
        }
        @aggregate
        entity Order { name String }
        service OrderService for (Order) {
            @rules(minimumAmount)
            placeOrder(Order) Order withEvents OrderPlaced
        }
        event OrderPlaced { name String }
    """.trimIndent()

    @Test
    fun theFlowPageListsTheRulesAndEachCommandsRules() {
        val output = DslDocs().generate(DocsInput(listOf(DocsSource(SourceKind.ZFL, "flows/place-order.zfl", zfl))))
        val page = output.files.first { it.path == "flows/place-order.md" }.content

        assertTrue("\n## Rules\n" in page, page)
        assertTrue("### minimumAmount\n\nAggregate: `Order`\n\nAt least 10 EUR\n\nApplied by: `placeOrder`\n" in page, page)
        assertTrue("### stock-first\n" in page, page)
        assertTrue("`placeOrder` (rules: [minimumAmount](#minimumamount), [stock-first](#stock-first))" in page, page)
        assertTrue("%% rules: minimumAmount, stock-first" in page, page)
    }

    @Test
    fun theModulePageListsTheRulesAndEachMethodsRules() {
        val page = DslDocs().previewZdl(zdl)

        assertTrue("\n## Rules\n" in page, page)
        assertTrue("### minimumAmount\n\nAggregate: Order\n\nAt least 10 EUR\n\nApplied by: `OrderService.placeOrder`\n" in page, page)
        assertTrue("| Rules |" in page, page)
        assertTrue("[minimumAmount](#minimumamount)" in page, page)
    }

    @Test
    fun sameRuleNameUnderTwoAggregatesLinksEachReferenceToItsOwnHeading() {
        val text = """
            rules (Order) { limit "Order limit" }
            rules (Payment) { limit "Payment limit" }
            @aggregate
            entity Order { name String }
            @aggregate
            entity Payment { name String }
            service OrderService for (Order) {
                @rules("Order.limit")
                placeOrder(Order) Order withEvents OrderPlaced
            }
            service PaymentService for (Payment) {
                @rules("Payment.limit")
                pay(Payment) Payment withEvents Paid
            }
            event OrderPlaced { name String }
            event Paid { name String }
        """.trimIndent()
        val page = DslDocs().previewZdl(text)

        assertTrue("[Order.limit](#limit)" in page, page)
        assertTrue("[Payment.limit](#limit-1)" in page, page)
    }

    @Test
    fun anAmbiguousPlainNameIsNotLinked() {
        val text = """
            rules (Order) { limit "Order limit" }
            rules (Payment) { limit "Payment limit" }
            @aggregate
            entity Order { name String }
            service OrderService for (Order) {
                @rules(limit)
                placeOrder(Order) Order withEvents OrderPlaced
            }
            event OrderPlaced { name String }
        """.trimIndent()
        val page = DslDocs().previewZdl(text)

        assertTrue("| limit |" in page, page)
    }

    @Test
    fun flowRulesWithTheSameNameLinkEachCommandToItsOwnHeading() {
        val text = """
            flow F {
                rules (Order) { limit "Order limit" }
                rules (Payment) { limit "Payment limit" }
                start S {}
                @rules("Order.limit")
                when S do a { service X.A emits E1 }
                @rules("Payment.limit")
                when E1 do b { service X.B emits E2 }
                end { done: E2 }
            }
        """.trimIndent()
        val page = DslDocs().generate(DocsInput(listOf(DocsSource(SourceKind.ZFL, "f.zfl", text)))).files.first { it.path == "flows/f.md" }.content

        assertTrue("[Order.limit](#limit)" in page, page)
        assertTrue("[Payment.limit](#limit-1)" in page, page)
    }

    @Test
    fun theFlowPageShowsTheAnnotationsAndJavadocOfARule() {
        val text = """
            flow F {
                rules (Order) {
                    /** Seats are never oversold. */
                    @invariant @rule(severity: high) noOversell "No oversell"
                    plain "Plain"
                }
                start S {}
                when S do a { service X.A emits E1 }
                end { done: E1 }
            }
        """.trimIndent()
        val page = DslDocs().generate(DocsInput(listOf(DocsSource(SourceKind.ZFL, "f.zfl", text)))).files.first { it.path == "flows/f.md" }.content

        assertTrue("### noOversell\n\n`@invariant` `@rule(severity: high)`\n\nSeats are never oversold.\n\nAggregate: `Order`\n\nNo oversell\n" in page, page)
        assertTrue("### plain\n\nAggregate: `Order`\n\nPlain\n" in page, page)
    }

    @Test
    fun theModulePageShowsTheAnnotationsAndJavadocOfARule() {
        val text = """
            rules (Order) {
                /** Seats are never oversold. */
                @invariant @pre(late) noOversell "No oversell"
                plain "Plain"
            }
            @aggregate
            entity Order { name String }
        """.trimIndent()
        val page = DslDocs().previewZdl(text)

        assertTrue("### noOversell\n\n`@invariant` `@pre(late)`\n\nSeats are never oversold.\n\nAggregate: Order\n\nNo oversell\n" in page, page)
        assertTrue("### plain\n\nAggregate: Order\n\nPlain\n" in page, page)
    }
}
