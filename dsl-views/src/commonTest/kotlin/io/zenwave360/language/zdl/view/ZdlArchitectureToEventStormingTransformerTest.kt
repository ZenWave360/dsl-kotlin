package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.architecture.ArchitectureFixtures
import io.zenwave360.language.zdl.architecture.ZdlArchitectureReader
import io.zenwave360.language.zdl.architecture.ZdlSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The architecture overview: a box per module with its services, commands and events, no arrows. */
class ZdlArchitectureToEventStormingTransformerTest {
    private val transformer = ZdlArchitectureToEventStormingTransformer()

    private fun read(vararg zdl: Pair<String, String>) = ZdlArchitectureReader().read(zdl.map { ZdlSource(it.first, it.second) })

    @Test
    fun boxPerModuleWithColumnsAndRows() {
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
              classDef event fill:#ffb76b,stroke:#d9822b,color:#111
              classDef service fill:#fbd3ee,stroke:#c2569b,color:#111
              subgraph sg_Billing["Billing"]
                direction LR
                Billing__c_BillingService_issue["issue"]
                Billing__e_A["A"]
                Billing__e_B["B"]
                Billing__e_C["C"]
                Billing__e_D["D"]
                Billing__e_E["E"]
                Billing__s_BillingService(["BillingService"])
              end
              subgraph sg_Orders["Orders"]
                direction LR
                Orders__c_OrderService_placeOrder["placeOrder"]
                Orders__c_OrderService_cancelOrder["cancelOrder"]
                Orders__e_OrderPlaced["OrderPlaced"]
                Orders__e_OrderCancelled["OrderCancelled"]
                Orders__e_CancelRejected["CancelRejected"]
                Orders__e_OrderArchived["OrderArchived"]
                Orders__s_OrderService(["OrderService"])
              end
              Billing__c_BillingService_issue ~~~ Billing__s_BillingService
              Billing__s_BillingService ~~~ Billing__e_A
              Billing__s_BillingService ~~~ Billing__e_B
              Billing__s_BillingService ~~~ Billing__e_C
              Billing__e_A ~~~ Billing__e_D
              Billing__e_B ~~~ Billing__e_E
              Orders__c_OrderService_placeOrder ~~~ Orders__s_OrderService
              Orders__c_OrderService_cancelOrder ~~~ Orders__s_OrderService
              Orders__s_OrderService ~~~ Orders__e_OrderPlaced
              Orders__s_OrderService ~~~ Orders__e_OrderCancelled
              Orders__s_OrderService ~~~ Orders__e_CancelRejected
              Orders__s_OrderService ~~~ Orders__e_OrderArchived
              Billing__e_D ~~~ Orders__c_OrderService_placeOrder
              class Billing__c_BillingService_issue,Orders__c_OrderService_placeOrder,Orders__c_OrderService_cancelOrder command
              class Billing__s_BillingService,Orders__s_OrderService service
              class Billing__e_A,Billing__e_B,Billing__e_C,Billing__e_D,Billing__e_E,Orders__e_OrderPlaced,Orders__e_OrderCancelled,Orders__e_CancelRejected,Orders__e_OrderArchived event
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
    fun modulithHasEveryModuleAndNoArrows() {
        val text = transformer.transform(ZdlArchitectureReader().read(ArchitectureFixtures.modulith()))!!
        for (box in listOf("CatalogInventory", "CatalogProducts", "FulfillmentShipping", "NotificationsConsumer", "OrdersCheckout", "PaymentsProcessing")) {
            assertTrue(text.contains("subgraph sg_$box["), box)
        }
        assertFalse(text.contains("-->"))
        assertTrue(text.contains("PaymentsProcessing__s_PaymentsProcessingService([\"PaymentsProcessingService\"])"))
    }
}
