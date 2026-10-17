package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.architecture.ArchitectureFixtures
import io.zenwave360.language.zdl.architecture.ZdlArchitectureReader
import io.zenwave360.language.zdl.architecture.ZdlSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZdlArchitectureToMermaidTransformerTest {
    private val small = ZdlArchitectureReader().read(ArchitectureFixtures.small())
    private val modulith = ZdlArchitectureReader().read(ArchitectureFixtures.modulith())

    private val smallFull = """
        flowchart LR
          classDef module fill:#e8f0fe,stroke:#4a6fa5,stroke-width:2px,color:#111
          classDef event fill:#fff4d6,stroke:#c9a227,color:#111
          classDef foreign fill:#f3f3f3,stroke:#888,stroke-dasharray:5 5,color:#111

          subgraph sg_Billing["Billing"]
            Billing{{"Billing"}}
            InvoiceVoided["InvoiceVoided"]
          end

          subgraph sg_Ledger["Ledger"]
            Ledger{{"Ledger"}}
            EntryPosted["EntryPosted"]
          end

          subgraph sg_Orders["Orders"]
            Orders{{"Orders"}}
            OrderPlaced["OrderPlaced"]
          end

          InvoiceIssued@{ shape: h-cyl, label: "InvoiceIssued" }
          ext_partner_shipped_event_v1@{ shape: h-cyl, label: "partner-shipped-event-v1" }

          class Billing,Orders module
          class EntryPosted,Ledger,ext_partner_shipped_event_v1,sg_Ledger foreign
          class InvoiceIssued,InvoiceVoided,OrderPlaced event

          Billing --> InvoiceIssued
          EntryPosted -.-> Billing
          InvoiceIssued -.-> Billing
          InvoiceIssued -.-> Orders
          InvoiceVoided -.-> Billing
          OrderPlaced -.-> Billing
          ext_partner_shipped_event_v1 -.-> Billing
          Billing ==>|"closeOrder<br/>markPaid"| Orders
    """.trimIndent() + "\n"

    @Test
    fun small_SingleDiagram() {
        val result = ZdlArchitectureToMermaidTransformer().transform(small)
        assertFalse(result.scaled)
        assertEquals(1, result.diagrams.size)
        assertEquals("Architecture", result.diagrams[0].title)
        assertEquals(null, result.diagrams[0].moduleId)
        assertEquals(smallFull, result.diagrams[0].mermaid)
        assertEquals(ZdlArchitectureToMermaidTransformer.LEGEND, result.legend)
    }

    @Test
    fun small_LegacyShapes() {
        val mermaid = ZdlArchitectureToMermaidTransformer(legacyShapes = true).transform(small).diagrams[0].mermaid
        val expected = smallFull
            .replace("flowchart LR", "flowchart TB")
            .replace("InvoiceIssued@{ shape: h-cyl, label: \"InvoiceIssued\" }", "InvoiceIssued[(\"InvoiceIssued\")]")
            .replace(
                "ext_partner_shipped_event_v1@{ shape: h-cyl, label: \"partner-shipped-event-v1\" }",
                "ext_partner_shipped_event_v1[(\"partner-shipped-event-v1\")]",
            )
        assertEquals(expected, mermaid)
    }

    @Test
    fun small_ScaledByLowThreshold() {
        val result = ZdlArchitectureToMermaidTransformer(maxModules = 1).transform(small)
        assertTrue(result.scaled)
        assertEquals(listOf("Architecture overview", "Billing", "Orders"), result.diagrams.map { it.title })
        assertEquals(listOf(null, "Billing", "Orders"), result.diagrams.map { it.moduleId })
        assertEquals(
            """
            flowchart LR
              classDef module fill:#e8f0fe,stroke:#4a6fa5,stroke-width:2px,color:#111
              classDef foreign fill:#f3f3f3,stroke:#888,stroke-dasharray:5 5,color:#111

              Billing{{"Billing"}}
              Ledger{{"Ledger"}}
              Orders{{"Orders"}}

              class Billing,Orders module
              class Ledger foreign

              Billing -.->|"InvoiceIssued"| Orders
              Ledger -.->|"EntryPosted"| Billing
              Orders -.->|"OrderPlaced"| Billing
              Billing ==>|"closeOrder<br/>markPaid"| Orders
            """.trimIndent() + "\n",
            result.diagrams[0].mermaid,
        )
        assertEquals(smallFull, result.diagrams[1].mermaid)
        assertEquals(
            """
            flowchart LR
              classDef module fill:#e8f0fe,stroke:#4a6fa5,stroke-width:2px,color:#111
              classDef event fill:#fff4d6,stroke:#c9a227,color:#111

              subgraph sg_Billing["Billing"]
                Billing{{"Billing"}}
              end

              subgraph sg_Orders["Orders"]
                Orders{{"Orders"}}
                OrderPlaced["OrderPlaced"]
              end

              InvoiceIssued@{ shape: h-cyl, label: "InvoiceIssued" }

              class Billing,Orders module
              class InvoiceIssued,OrderPlaced event

              Billing --> InvoiceIssued
              InvoiceIssued -.-> Orders
              OrderPlaced -.-> Billing
              Billing ==>|"closeOrder<br/>markPaid"| Orders
            """.trimIndent() + "\n",
            result.diagrams[2].mermaid,
        )
    }

    @Test
    fun modulith_ScaledOverviewSnapshot() {
        val result = ZdlArchitectureToMermaidTransformer().transform(modulith)
        assertTrue(result.scaled)
        assertEquals(7, result.diagrams.size)
        assertEquals(
            """
            flowchart LR
              classDef module fill:#e8f0fe,stroke:#4a6fa5,stroke-width:2px,color:#111

              CatalogInventory{{"Catalog Inventory"}}
              CatalogProducts{{"Product Catalog"}}
              FulfillmentShipping{{"Fulfillment Shipping"}}
              NotificationsConsumer{{"Notifications Consumer"}}
              OrdersCheckout{{"Orders Checkout"}}
              PaymentsProcessing{{"Payments Processing"}}

              class CatalogInventory,CatalogProducts,FulfillmentShipping,NotificationsConsumer,OrdersCheckout,PaymentsProcessing module

              CatalogInventory -.->|"StockReleased"| OrdersCheckout
              FulfillmentShipping -.->|"FulfillmentFailed<br/>FulfillmentScheduled"| PaymentsProcessing
              OrdersCheckout -.->|"OrderConfirmed"| FulfillmentShipping
              OrdersCheckout -.->|"OrderCancelled<br/>StockUnavailable"| NotificationsConsumer
              OrdersCheckout -.->|"OrderCreated"| PaymentsProcessing
              PaymentsProcessing -.->|"PaymentDeclined<br/>PaymentRetryExhausted<br/>PaymentVoided"| CatalogInventory
              PaymentsProcessing -.->|"PaymentCaptured"| NotificationsConsumer
              PaymentsProcessing -.->|"PaymentAuthorized"| OrdersCheckout
              OrdersCheckout ==>|"confirmStockReservation<br/>reserveStock"| CatalogInventory
            """.trimIndent() + "\n",
            result.diagrams[0].mermaid,
        )
    }

    @Test
    fun modulith_PerModuleDiagramsHoldOnlyRelatedEvents() {
        val result = ZdlArchitectureToMermaidTransformer().transform(modulith)
        val orders = result.diagrams.first { it.moduleId == "OrdersCheckout" }.mermaid
        assertTrue(orders.contains("    OrderCreated[\"OrderCreated\"]"))
        assertTrue(orders.contains("    PaymentAuthorized[\"PaymentAuthorized\"]"))
        assertFalse(orders.contains("PaymentFailed"))
        assertTrue(orders.contains("  OrderCreated -.-> PaymentsProcessing"))
        assertTrue(orders.contains("OrdersCheckout ==>|\"confirmStockReservation<br/>reserveStock\"| CatalogInventory"))
        for (d in result.diagrams) {
            assertFalse(d.mermaid.contains("click "))
            assertFalse(d.mermaid.contains(":::"))
        }
    }

    @Test
    fun clashingEventNames_GetModulePrefixedIds() {
        val sources = listOf(
            ZdlSource("a.zdl", "config { title \"A\" }\nevent Done { id Long }"),
            ZdlSource("b.zdl", "config { title \"B\" }\nevent Done { id Long }"),
        )
        val mermaid = ZdlArchitectureToMermaidTransformer().transform(ZdlArchitectureReader().read(sources)).diagrams[0].mermaid
        assertTrue(mermaid.contains("    A_Done[\"Done\"]"), mermaid)
        assertTrue(mermaid.contains("    B_Done[\"Done\"]"), mermaid)
    }
}
