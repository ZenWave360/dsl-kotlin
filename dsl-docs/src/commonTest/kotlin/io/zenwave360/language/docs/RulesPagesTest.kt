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
}
