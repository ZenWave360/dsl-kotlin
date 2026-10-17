package io.zenwave360.language.zdl.view

import io.zenwave360.language.readViewsTestFile
import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZdlServiceToEventStormingTransformerTest {

    private fun transform(zdl: String) = ZdlServiceToEventStormingTransformer().transform(ZdlParser().parseModel(zdl))

    @Test
    fun alternativesCommandWithoutEventsQueriesAndSeveralAggregates() {
        val zdl = """
            service OrderService for (Order, Customer) {
                @transition(to: CREATED)
                startCheckout(StartInput) Order withEvents OrderCreated
                pay(id, PayInput) Order withEvents [PaymentOk | PaymentFailed] OrderPaid
                @listener(event: PaymentOk)
                audit(AuditInput) Order
                getOrder(id) Order?
                findOrders(SearchInput) Order[]
            }
        """.trimIndent()

        val expected = """
            flowchart LR
              classDef command fill:#a7c7f2,stroke:#3b6fb6,color:#111
              classDef event fill:#ffb76b,stroke:#d9822b,color:#111
              subgraph agg_Order__Customer["Order, Customer"]
                direction LR
                cmd_startCheckout["startCheckout"]
                cmd_pay["pay"]
                cmd_audit["audit"]
                evt_OrderCreated["OrderCreated"]
                evt_PaymentOk["PaymentOk"]
                evt_PaymentFailed["PaymentFailed"]
                evt_OrderPaid["OrderPaid"]
              end
              style agg_Order__Customer fill:#fbd3ee,stroke:#c2569b,color:#111
              class cmd_startCheckout command
              class cmd_pay command
              class cmd_audit command
              class evt_OrderCreated event
              class evt_PaymentOk event
              class evt_PaymentFailed event
              class evt_OrderPaid event
              cmd_startCheckout --> evt_OrderCreated
              cmd_pay --> evt_PaymentOk
              cmd_pay --> evt_PaymentFailed
              cmd_pay --> evt_OrderPaid

        """.trimIndent()

        val diagram = transform(zdl).single()
        assertEquals("OrderService", diagram.service)
        assertEquals(expected, diagram.mermaid)
        assertEquals(listOf("getOrder", "findOrders"), diagram.queries)
        assertEquals("Queries: `getOrder`, `findOrders`.", diagram.queriesLine)
    }

    @Test
    fun queryOnlyServiceHasNoDiagram() {
        val zdl = """
            service ReadService for (Product) {
                getProduct(id) Product?
            }
        """.trimIndent()

        val read = transform(zdl).single()
        assertNull(read.mermaid)
        assertEquals("Queries: `getProduct`.", read.queriesLine)
    }

    @Test
    fun modulithOrdersCheckout() {
        val zdl = readViewsTestFile("arcadia-editions-modulith/orders-checkout-domain-model.zdl")
        val expected = """
            flowchart LR
              classDef command fill:#a7c7f2,stroke:#3b6fb6,color:#111
              classDef event fill:#ffb76b,stroke:#d9822b,color:#111
              subgraph agg_Order["Order"]
                direction LR
                cmd_startOrderCheckout["startOrderCheckout"]
                cmd_confirmOrder["confirmOrder"]
                cmd_cancelOrder["cancelOrder"]
                evt_OrderCreated["OrderCreated"]
                evt_StockUnavailable["StockUnavailable"]
                evt_OrderConfirmed["OrderConfirmed"]
                evt_OrderCancelled["OrderCancelled"]
              end
              style agg_Order fill:#fbd3ee,stroke:#c2569b,color:#111
              class cmd_startOrderCheckout command
              class cmd_confirmOrder command
              class cmd_cancelOrder command
              class evt_OrderCreated event
              class evt_StockUnavailable event
              class evt_OrderConfirmed event
              class evt_OrderCancelled event
              cmd_startOrderCheckout --> evt_OrderCreated
              cmd_startOrderCheckout --> evt_StockUnavailable
              cmd_confirmOrder --> evt_OrderConfirmed
              cmd_cancelOrder --> evt_OrderCancelled

        """.trimIndent()
        assertEquals(expected, transform(zdl).single().mermaid)
    }
}
