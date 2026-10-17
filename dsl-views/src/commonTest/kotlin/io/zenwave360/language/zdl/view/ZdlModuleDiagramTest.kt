package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.architecture.ArchitectureFixtures
import io.zenwave360.language.zdl.architecture.ZdlArchitectureReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The module flowchart (commands and events) on the small hand-written fixture and the modulith. */
class ZdlModuleDiagramTest {
    private val small = ZdlArchitectureReader().read(ArchitectureFixtures.small())
    private val modulith = ZdlArchitectureReader().read(ArchitectureFixtures.modulith())
    private val transformer = ZdlArchitectureToMermaidTransformer()

    // Billing: internal event (InvoiceVoided, also consumed by its own command), external pipe (InvoiceIssued),
    // foreign pipe (partner-shipped-event-v1), referenced module (Ledger), calls out (reconcile ==> Orders),
    // the emitting command of a consumed event (placeOrder --> OrderPlaced).
    private val billing = """
            flowchart LR
              classDef module fill:#e8f0fe,stroke:#4a6fa5,color:#111
              classDef command fill:#a7c7f2,stroke:#3b6fb6,color:#111
              classDef event fill:#ffb76b,stroke:#d9822b,color:#111
              classDef foreign fill:#f3f3f3,stroke:#888,stroke-dasharray:5 5,color:#111

              subgraph sg_Billing["Billing"]
                Billing__BillingService__issueInvoice["issueInvoice"]
                Billing__BillingService__onShipped["onShipped"]
                Billing__BillingService__reissue["reissue"]
                Billing__BillingService__reconcile["reconcile"]
                Billing__BillingService__broken["broken"]
                Billing__InvoiceVoided["InvoiceVoided"]
              end

              subgraph sg_Ledger["Ledger"]
                Ledger__EntryPosted["EntryPosted"]
              end

              subgraph sg_Orders["Orders"]
                Orders__OrdersService__placeOrder["placeOrder"]
                Orders__OrdersService__markPaid["markPaid"]
                Orders__OrdersService__closeOrder["closeOrder"]
                Orders__OrdersService__onInvoice["onInvoice"]
                Orders__OrderPlaced["OrderPlaced"]
              end

              Billing__InvoiceIssued@{ shape: h-cyl, label: "InvoiceIssued" }
              ext_partner_shipped_event_v1@{ shape: h-cyl, label: "partner-shipped-event-v1" }

              class sg_Billing,sg_Orders module
              class ext_partner_shipped_event_v1,sg_Ledger foreign
              class Billing__BillingService__broken,Billing__BillingService__issueInvoice,Billing__BillingService__onShipped,Billing__BillingService__reconcile,Billing__BillingService__reissue,Orders__OrdersService__closeOrder,Orders__OrdersService__markPaid,Orders__OrdersService__onInvoice,Orders__OrdersService__placeOrder command
              class Billing__InvoiceIssued,Billing__InvoiceVoided,Ledger__EntryPosted,Orders__OrderPlaced event

              Billing__BillingService__issueInvoice --> Billing__InvoiceIssued
              Billing__BillingService__issueInvoice --> Billing__InvoiceVoided
              Orders__OrdersService__placeOrder --> Orders__OrderPlaced
              Billing__InvoiceIssued -.-> Billing__BillingService__reissue
              Billing__InvoiceIssued -.-> Orders__OrdersService__onInvoice
              Billing__InvoiceVoided -.-> Billing__BillingService__issueInvoice
              Ledger__EntryPosted -.-> Billing__BillingService__reconcile
              Orders__OrderPlaced -.-> Billing__BillingService__issueInvoice
              ext_partner_shipped_event_v1 -.-> Billing__BillingService__onShipped
              Billing__BillingService__reconcile ==> Orders__OrdersService__closeOrder
              Billing__BillingService__reconcile ==> Orders__OrdersService__markPaid
    """.trimIndent() + "\n"

    // Orders: a call in (from Billing.reconcile) and a consumed external event whose emitter is in Billing.
    private val orders = """
            flowchart LR
              classDef module fill:#e8f0fe,stroke:#4a6fa5,color:#111
              classDef command fill:#a7c7f2,stroke:#3b6fb6,color:#111
              classDef event fill:#ffb76b,stroke:#d9822b,color:#111

              subgraph sg_Billing["Billing"]
                Billing__BillingService__issueInvoice["issueInvoice"]
                Billing__BillingService__reconcile["reconcile"]
              end

              subgraph sg_Orders["Orders"]
                Orders__OrdersService__placeOrder["placeOrder"]
                Orders__OrdersService__markPaid["markPaid"]
                Orders__OrdersService__closeOrder["closeOrder"]
                Orders__OrdersService__onInvoice["onInvoice"]
                Orders__OrderPlaced["OrderPlaced"]
              end

              Billing__InvoiceIssued@{ shape: h-cyl, label: "InvoiceIssued" }

              class sg_Billing,sg_Orders module
              class Billing__BillingService__issueInvoice,Billing__BillingService__reconcile,Orders__OrdersService__closeOrder,Orders__OrdersService__markPaid,Orders__OrdersService__onInvoice,Orders__OrdersService__placeOrder command
              class Billing__InvoiceIssued,Orders__OrderPlaced event

              Billing__BillingService__issueInvoice --> Billing__InvoiceIssued
              Orders__OrdersService__placeOrder --> Orders__OrderPlaced
              Billing__InvoiceIssued -.-> Orders__OrdersService__onInvoice
              Orders__OrderPlaced -.-> Billing__BillingService__issueInvoice
              Billing__BillingService__reconcile ==> Orders__OrdersService__closeOrder
              Billing__BillingService__reconcile ==> Orders__OrdersService__markPaid
    """.trimIndent() + "\n"

    // Ledger is a referenced module: its diagram is just what points to it.
    private val ledger = """
            flowchart LR
              classDef module fill:#e8f0fe,stroke:#4a6fa5,color:#111
              classDef command fill:#a7c7f2,stroke:#3b6fb6,color:#111
              classDef event fill:#ffb76b,stroke:#d9822b,color:#111
              classDef foreign fill:#f3f3f3,stroke:#888,stroke-dasharray:5 5,color:#111

              subgraph sg_Billing["Billing"]
                Billing__BillingService__reconcile["reconcile"]
              end

              subgraph sg_Ledger["Ledger"]
                Ledger__EntryPosted["EntryPosted"]
              end

              class sg_Billing module
              class sg_Ledger foreign
              class Billing__BillingService__reconcile command
              class Ledger__EntryPosted event

              Ledger__EntryPosted -.-> Billing__BillingService__reconcile
    """.trimIndent() + "\n"

    private val ordersCheckout = """
            flowchart LR
              classDef module fill:#e8f0fe,stroke:#4a6fa5,color:#111
              classDef command fill:#a7c7f2,stroke:#3b6fb6,color:#111
              classDef event fill:#ffb76b,stroke:#d9822b,color:#111

              subgraph sg_CatalogInventory["Catalog Inventory"]
                CatalogInventory__InventoryService__reserveStock["reserveStock"]
                CatalogInventory__InventoryService__releaseStock["releaseStock"]
                CatalogInventory__InventoryService__confirmStockReservation["confirmStockReservation"]
                CatalogInventory__StockReleased["StockReleased"]
              end

              subgraph sg_FulfillmentShipping["Fulfillment Shipping"]
                FulfillmentShipping__FulfillmentShippingService__scheduleFulfillment["scheduleFulfillment"]
              end

              subgraph sg_NotificationsConsumer["Notifications Consumer"]
                NotificationsConsumer__NotificationsConsumerService__sendStockUnavailableNotification["sendStockUnavailableNotification"]
                NotificationsConsumer__NotificationsConsumerService__sendOrderCancelledNotification["sendOrderCancelledNotification"]
              end

              subgraph sg_OrdersCheckout["Orders Checkout"]
                OrdersCheckout__OrdersCheckoutService__startOrderCheckout["startOrderCheckout"]
                OrdersCheckout__OrdersCheckoutService__confirmOrder["confirmOrder"]
                OrdersCheckout__OrdersCheckoutService__cancelOrder["cancelOrder"]
                OrdersCheckout__OrderCancelled["OrderCancelled"]
                OrdersCheckout__OrderConfirmed["OrderConfirmed"]
                OrdersCheckout__OrderCreated["OrderCreated"]
                OrdersCheckout__StockUnavailable["StockUnavailable"]
              end

              subgraph sg_PaymentsProcessing["Payments Processing"]
                PaymentsProcessing__PaymentsProcessingService__authorizePayment["authorizePayment"]
                PaymentsProcessing__PaymentAuthorized["PaymentAuthorized"]
              end

              class sg_CatalogInventory,sg_FulfillmentShipping,sg_NotificationsConsumer,sg_OrdersCheckout,sg_PaymentsProcessing module
              class CatalogInventory__InventoryService__confirmStockReservation,CatalogInventory__InventoryService__releaseStock,CatalogInventory__InventoryService__reserveStock,FulfillmentShipping__FulfillmentShippingService__scheduleFulfillment,NotificationsConsumer__NotificationsConsumerService__sendOrderCancelledNotification,NotificationsConsumer__NotificationsConsumerService__sendStockUnavailableNotification,OrdersCheckout__OrdersCheckoutService__cancelOrder,OrdersCheckout__OrdersCheckoutService__confirmOrder,OrdersCheckout__OrdersCheckoutService__startOrderCheckout,PaymentsProcessing__PaymentsProcessingService__authorizePayment command
              class CatalogInventory__StockReleased,OrdersCheckout__OrderCancelled,OrdersCheckout__OrderConfirmed,OrdersCheckout__OrderCreated,OrdersCheckout__StockUnavailable,PaymentsProcessing__PaymentAuthorized event

              CatalogInventory__InventoryService__releaseStock --> CatalogInventory__StockReleased
              OrdersCheckout__OrdersCheckoutService__cancelOrder --> OrdersCheckout__OrderCancelled
              OrdersCheckout__OrdersCheckoutService__confirmOrder --> OrdersCheckout__OrderConfirmed
              OrdersCheckout__OrdersCheckoutService__startOrderCheckout --> OrdersCheckout__OrderCreated
              OrdersCheckout__OrdersCheckoutService__startOrderCheckout --> OrdersCheckout__StockUnavailable
              PaymentsProcessing__PaymentsProcessingService__authorizePayment --> PaymentsProcessing__PaymentAuthorized
              CatalogInventory__StockReleased -.-> OrdersCheckout__OrdersCheckoutService__cancelOrder
              OrdersCheckout__OrderCancelled -.-> NotificationsConsumer__NotificationsConsumerService__sendOrderCancelledNotification
              OrdersCheckout__OrderConfirmed -.-> FulfillmentShipping__FulfillmentShippingService__scheduleFulfillment
              OrdersCheckout__OrderCreated -.-> PaymentsProcessing__PaymentsProcessingService__authorizePayment
              OrdersCheckout__StockUnavailable -.-> NotificationsConsumer__NotificationsConsumerService__sendStockUnavailableNotification
              PaymentsProcessing__PaymentAuthorized -.-> OrdersCheckout__OrdersCheckoutService__confirmOrder
              OrdersCheckout__OrdersCheckoutService__confirmOrder ==> CatalogInventory__InventoryService__confirmStockReservation
              OrdersCheckout__OrdersCheckoutService__startOrderCheckout ==> CatalogInventory__InventoryService__reserveStock
    """.trimIndent() + "\n"

    @Test
    fun small_BillingHasEveryKindOfNode() = assertEquals(billing, transformer.moduleDiagram(small, "Billing"))

    @Test
    fun small_OrdersHasCallInAndEmittingCommand() = assertEquals(orders, transformer.moduleDiagram(small, "Orders"))

    @Test
    fun small_ReferencedModuleIsDashed() = assertEquals(ledger, transformer.moduleDiagram(small, "Ledger"))

    @Test
    fun modulith_OrdersCheckout() = assertEquals(ordersCheckout, transformer.moduleDiagram(modulith, "OrdersCheckout"))

    @Test
    fun architectureKnowsCommandsAndQueries() {
        assertEquals(listOf("placeOrder", "markPaid", "closeOrder", "onInvoice"), small.commandsOf("Orders").map { it.method })
        assertEquals(listOf("OrderPlaced"), small.commandsOf("Orders").first().events)
        // markPaid and closeOrder are queries by the command/query rule, yet the call to them is drawn
        assertTrue(small.commandsOf("Orders").first { it.method == "markPaid" }.query)
        assertTrue(small.commands.none { it.module == "Ledger" })
    }

    @Test
    fun queriesAreLeftOutUnlessCalled() {
        val text = transformer.moduleDiagram(modulith, "OrdersCheckout")
        assertFalse(text.contains("getOrder"))
    }

    @Test
    fun noRuleOfPartOneIsBroken() {
        for (m in listOf(small to "Billing", small to "Orders", small to "Ledger", modulith to "OrdersCheckout")) {
            val text = transformer.moduleDiagram(m.first, m.second)
            assertFalse(text.contains("click "))
            assertFalse(Regex("\\}:::").containsMatchIn(text))
            assertFalse(text.contains(":::"))
        }
    }

    @Test
    fun unknownModuleGivesAnEmptyFlowchart() {
        assertEquals("flowchart LR\n", transformer.moduleDiagram(small, "Nope"))
    }

    @Test
    fun legendNamesTheNewShapes() {
        assertTrue(ZdlArchitectureToMermaidTransformer.MODULE_LEGEND.startsWith("Box = module. Blue = command."))
    }
}
