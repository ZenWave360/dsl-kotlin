package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.architecture.ArchitectureFixtures
import io.zenwave360.language.zdl.architecture.ZdlArchitectureReader
import io.zenwave360.language.zdl.architecture.ZdlSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The architecture overview: a box per module with its commands, services and events. */
class ZdlArchitectureToEventStormingTransformerTest {
    private val transformer = ZdlArchitectureToEventStormingTransformer()

    private fun read(vararg zdl: Pair<String, String>) = ZdlArchitectureReader().read(zdl.map { ZdlSource(it.first, it.second) })

    @Test
    fun boxPerModuleWithArrowsFromCommandsToServiceToEvents() {
        val orders = """
            config { title "Orders" }
            entity Order { status String }
            service OrderService for (Order) {
                placeOrder(Order) Order withEvents OrderPlaced
                cancelOrder(id) Order withEvents [OrderCancelled | CancelRejected]
                getOrder(id) Order?
            }
            event OrderPlaced { id Long }
            event OrderCancelled { id Long }
            event CancelRejected { id Long }
            event OrderArchived { id Long }
        """.trimIndent()
        val billing = """
            config { title "Billing" }
            entity Invoice { total Long }
            service BillingService for (Invoice) {
                issue(Invoice) Invoice withEvents [A | B | C | D | E]
            }
            event A { id Long }
            event B { id Long }
            event C { id Long }
            event D { id Long }
            event E { id Long }
        """.trimIndent()
        val expected = """
            flowchart LR
              classDef command fill:#a7c7f2,stroke:#3b6fb6,color:#111
              classDef service fill:#fbd3ee,stroke:#c2569b,color:#111
              classDef event fill:#ffb76b,stroke:#d9822b,color:#111
              subgraph sg_Billing["Billing"]
                direction LR
                Billing_issue["issue"] --> Billing_BillingService
                Billing_BillingService(["BillingService"])
                Billing_BillingService --> Billing_A["A"]
                Billing_BillingService --> Billing_B["B"]
                Billing_BillingService --> Billing_C["C"]
                Billing_BillingService --> Billing_D["D"]
                Billing_BillingService --> Billing_E["E"]
              end
              subgraph sg_Orders["Orders"]
                direction LR
                Orders_placeOrder["placeOrder"] --> Orders_OrderService
                Orders_cancelOrder["cancelOrder"] --> Orders_OrderService
                Orders_OrderService(["OrderService"])
                Orders_OrderService --> Orders_OrderPlaced["OrderPlaced"]
                Orders_OrderService --> Orders_OrderCancelled["OrderCancelled"]
                Orders_OrderService --> Orders_CancelRejected["CancelRejected"]
                Orders_OrderService --> Orders_OrderArchived["OrderArchived"]
              end
              class Billing_issue,Orders_placeOrder,Orders_cancelOrder command
              class Billing_BillingService,Orders_OrderService service
              class Billing_A,Billing_B,Billing_C,Billing_D,Billing_E,Orders_OrderPlaced,Orders_OrderCancelled,Orders_CancelRejected,Orders_OrderArchived event
              style sg_Billing fill:#ffffff,stroke:#999999,stroke-dasharray:4 3,color:#333333
              style sg_Orders fill:#ffffff,stroke:#999999,stroke-dasharray:4 3,color:#333333

        """.trimIndent()
        assertEquals(expected, transformer.transform(read("orders.zdl" to orders, "billing.zdl" to billing)))
    }

    @Test
    fun nullWithoutCommandsOrEvents() {
        assertNull(transformer.transform(read("a.zdl" to "entity A { a String }")))
    }

    @Test
    fun modulithHasEveryModule() {
        val text = transformer.transform(ZdlArchitectureReader().read(ArchitectureFixtures.modulith()))!!
        for (box in listOf("CatalogInventory", "CatalogProducts", "FulfillmentShipping", "NotificationsConsumer", "OrdersCheckout", "PaymentsProcessing")) {
            assertTrue(text.contains("subgraph sg_$box["), box)
        }
        assertTrue(text.contains("PaymentsProcessing_PaymentsProcessingService([\"PaymentsProcessingService\"])"))
    }
}
