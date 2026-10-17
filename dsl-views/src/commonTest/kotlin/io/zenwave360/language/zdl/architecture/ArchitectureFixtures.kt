package io.zenwave360.language.zdl.architecture

import io.zenwave360.language.readViewsTestFile

/** Test inputs shared by the architecture model and diagram tests. */
object ArchitectureFixtures {

    private val modulithFiles = listOf(
        "catalog-inventory", "catalog-products", "fulfillment-shipping",
        "notifications-consumer", "orders-checkout", "payments-processing",
    )

    /** The six modulith ZDLs, all events internal. Paths are relative, like `dsl-docs.yml` would give them. */
    fun modulith(): List<ZdlSource> = modulithFiles.map {
        val file = "$it-domain-model.zdl"
        ZdlSource(file, readViewsTestFile("arcadia-editions-modulith/$file"))
    }

    /**
     * Two small ZDLs covering what the modulith does not: an external event, a foreign event, a
     * referenced module (`ledger-model.zdl` is not in the set), calls, and unresolvable references.
     */
    fun small(): List<ZdlSource> = listOf(
        ZdlSource(
            "zdl/billing.zdl",
            """
            config {
                title "Shop - Billing"
                basePackage "com.shop.billing"
            }
            apis {
                zdl client OrdersZdl "../zdl/orders.zdl"
                zdl client LedgerZdl "./ledger-model.zdl"
            }

            service BillingService for (Invoice) {
                @listener(zdl: OrdersZdl, event: OrderPlaced)
                @listener(event: InvoiceVoided)
                issueInvoice(id) Invoice withEvents [InvoiceIssued | InvoiceVoided]

                @asyncapi(api: PartnerApi, channel: "partner-shipped-event-v1")
                onShipped(id) Invoice

                @asyncapi(channel: "invoice-issued-event-v1")
                reissue(id) Invoice

                @listener(zdl: LedgerZdl, event: EntryPosted)
                @calls(zdl: OrdersZdl, command: OrdersService.markPaid)
                @calls(zdl: OrdersZdl, command: OrdersService.closeOrder)
                reconcile(id) Invoice

                @listener(zdl: NopeZdl, event: Whatever)
                @listener(zdl: OrdersZdl, event: Missing)
                @asyncapi(channel: "no-such-channel-v1")
                broken(id) Invoice
            }

            @asyncapi({ channel: "invoice-issued-event-v1", topic: "billing.invoice-issued.v1" })
            event InvoiceIssued { id Long }

            event InvoiceVoided { id Long }
            """.trimIndent(),
        ),
        ZdlSource(
            "zdl/orders.zdl",
            """
            config {
                title "Shop - Orders"
            }

            service OrdersService for (Order) {
                placeOrder(id) Order withEvents OrderPlaced
                markPaid(id) Order
                closeOrder(id) Order

                @asyncapi(api: BillingApi, channel: "invoice-issued-event-v1")
                onInvoice(id) Order
            }

            event OrderPlaced { id Long }
            """.trimIndent(),
        ),
    )
}
