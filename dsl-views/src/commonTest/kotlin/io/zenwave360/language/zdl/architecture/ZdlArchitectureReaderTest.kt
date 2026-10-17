package io.zenwave360.language.zdl.architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ZdlArchitectureReaderTest {

    private val reader = ZdlArchitectureReader()

    private fun edges(a: ZdlArchitecture) =
        a.consumptions.map { "${it.event.module}.${it.event.name} -> ${it.consumer} (${it.method})" }

    // ---- modulith ---------------------------------------------------------------------------

    @Test
    fun modulith_ModulesFollowFileNamesAndTitles() {
        val a = reader.read(ArchitectureFixtures.modulith())
        assertEquals(
            listOf(
                "CatalogInventory", "CatalogProducts", "FulfillmentShipping",
                "NotificationsConsumer", "OrdersCheckout", "PaymentsProcessing",
            ),
            a.modules.map { it.id },
        )
        assertTrue(a.modules.none { it.referenced })
        val orders = assertNotNull(a.module("OrdersCheckout"))
        assertEquals("orders-checkout", orders.slug)
        assertEquals("Orders Checkout", orders.name)
        assertEquals("orders-checkout-domain-model.zdl", orders.path)
        assertEquals("com.arcadiaeditions.orders.checkout", orders.basePackage)
        assertEquals("Product Catalog", a.module("CatalogProducts")?.name)
        assertEquals(emptyList(), a.problems)
    }

    @Test
    fun modulith_AllEventsAreInternal() {
        val a = reader.read(ArchitectureFixtures.modulith())
        assertEquals(COUNT_EVENTS, a.events.size)
        assertTrue(a.events.all { it.kind == EventKind.INTERNAL && it.topic == null && it.channel == null })
        assertEquals(
            listOf("OrderCancelled", "OrderConfirmed", "OrderCreated", "StockUnavailable"),
            a.eventsOf("OrdersCheckout").map { it.name },
        )
        assertTrue(a.foreignEvents.isEmpty())
    }

    @Test
    fun modulith_ConsumptionsResolveAcrossModules() {
        val a = reader.read(ArchitectureFixtures.modulith())
        val edges = edges(a)
        assertEquals(COUNT_EDGES, edges.size, edges.joinToString("\n"))
        assertTrue(
            edges.any { it.startsWith("OrdersCheckout.OrderCreated -> PaymentsProcessing (PaymentsProcessingService.") },
            edges.joinToString("\n"),
        )
        assertTrue(edges.any { it.startsWith("PaymentsProcessing.PaymentFailed -> PaymentsProcessing (") })
        assertEquals(listOf("PaymentsProcessing"), a.consumersOf(EventRef("OrdersCheckout", "OrderCreated")))
        assertEquals(listOf("NotificationsConsumer"), a.consumersOf(EventRef("OrdersCheckout", "StockUnavailable")))
        assertTrue(a.consumptions.all { it.via == ConsumptionVia.LISTENER })
    }

    @Test
    fun modulith_CallsUseTheCommandNameOnly() {
        val a = reader.read(ArchitectureFixtures.modulith())
        assertEquals(
            listOf(
                ArchCall(
                    "OrdersCheckout", "CatalogInventory", "confirmStockReservation",
                    "InventoryService.confirmStockReservation", "OrdersCheckoutService.confirmOrder",
                ),
                ArchCall(
                    "OrdersCheckout", "CatalogInventory", "reserveStock",
                    "InventoryService.reserveStock", "OrdersCheckoutService.startOrderCheckout",
                ),
            ),
            a.calls,
        )
    }

    // ---- small fixture ----------------------------------------------------------------------

    @Test
    fun small_TitlePrefixIsDroppedWhenSharedAndModulesAreResolved() {
        val a = reader.read(ArchitectureFixtures.small())
        assertEquals(listOf("Billing", "Ledger", "Orders"), a.modules.map { it.id })
        assertEquals(listOf("Billing", "Ledger", "Orders"), a.modules.map { it.name })
        val ledger = assertNotNull(a.module("Ledger"))
        assertTrue(ledger.referenced)
        assertEquals("zdl/ledger-model.zdl", ledger.path)
        assertEquals("com.shop.billing", a.module("Billing")?.basePackage)
        assertEquals(false, a.module("Orders")?.referenced)
    }

    @Test
    fun small_EventKindsComeFromTheAsyncapiTopic() {
        val a = reader.read(ArchitectureFixtures.small())
        assertEquals(
            ArchEvent("Billing", "InvoiceIssued", EventKind.EXTERNAL, "invoice-issued-event-v1", "billing.invoice-issued.v1"),
            a.event(EventRef("Billing", "InvoiceIssued")),
        )
        assertEquals(ArchEvent("Billing", "InvoiceVoided", EventKind.INTERNAL), a.event(EventRef("Billing", "InvoiceVoided")))
        // The referenced module holds only the referenced event.
        assertEquals(listOf(ArchEvent("Ledger", "EntryPosted", EventKind.INTERNAL)), a.eventsOf("Ledger"))
    }

    @Test
    fun small_RepeatedListenersAndAsyncapiChannelsAreAllKept() {
        val a = reader.read(ArchitectureFixtures.small())
        assertEquals(
            listOf(
                "Billing.InvoiceIssued -> Billing (BillingService.reissue)",
                "Billing.InvoiceIssued -> Orders (OrdersService.onInvoice)",
                "Billing.InvoiceVoided -> Billing (BillingService.issueInvoice)",
                "Ledger.EntryPosted -> Billing (BillingService.reconcile)",
                "Orders.OrderPlaced -> Billing (BillingService.issueInvoice)",
                "null.partner-shipped-event-v1 -> Billing (BillingService.onShipped)",
            ),
            edges(a).sorted(),
        )
        assertEquals(
            ConsumptionVia.ASYNCAPI,
            a.consumptions.single { it.method == "OrdersService.onInvoice" }.via,
        )
        assertEquals(
            ConsumptionVia.LISTENER,
            a.consumptions.single { it.method == "BillingService.reconcile" }.via,
        )
    }

    @Test
    fun small_UnknownProviderBecomesAForeignEvent() {
        val a = reader.read(ArchitectureFixtures.small())
        assertEquals(listOf(ArchForeignEvent("partner-shipped-event-v1", "PartnerApi")), a.foreignEvents)
        assertEquals(listOf("Billing"), a.consumersOf(EventRef(null, "partner-shipped-event-v1")))
    }

    @Test
    fun small_CallsAreRecordedPerCommand() {
        val a = reader.read(ArchitectureFixtures.small())
        assertEquals(
            listOf(
                ArchCall("Billing", "Orders", "closeOrder", "OrdersService.closeOrder", "BillingService.reconcile"),
                ArchCall("Billing", "Orders", "markPaid", "OrdersService.markPaid", "BillingService.reconcile"),
            ),
            a.calls,
        )
    }

    @Test
    fun small_UnresolvableReferencesAreProblemsAndNeverThrow() {
        val a = reader.read(ArchitectureFixtures.small())
        assertEquals(
            listOf(
                "zdl/billing.zdl: @asyncapi on BillingService.broken refers to channel 'no-such-channel-v1' not found in Billing",
                "zdl/billing.zdl: @listener on BillingService.broken refers to event 'Missing' not found in Orders",
                "zdl/billing.zdl: @listener on BillingService.broken refers to unknown zdl client 'NopeZdl'",
            ),
            a.problems.sorted(),
        )
    }

    @Test
    fun small_ClientsOutsideTheSetBecomeReferencedModules() {
        val a = reader.read(listOf(ArchitectureFixtures.small().first()))
        assertEquals(listOf("Billing", "Ledger", "Orders"), a.modules.map { it.id })
        assertEquals(listOf(false, true, true), a.modules.map { it.referenced })
        // Referenced modules use the slug words; every event referenced in them is recorded, found or not.
        assertEquals("Shop - Billing", a.module("Billing")?.name)
        assertEquals("Orders", a.module("Orders")?.name)
        assertEquals(listOf("Billing"), a.calls.map { it.caller }.distinct())
        assertEquals(listOf("Orders"), a.calls.map { it.callee }.distinct())
        assertEquals(listOf("Missing", "OrderPlaced"), a.eventsOf("Orders").map { it.name })
    }

    // ---- edge cases -------------------------------------------------------------------------

    @Test
    fun singleModuleKeepsItsTitleAndDomainModelFallsBackToTheFolderName() {
        val a = reader.read(listOf(ZdlSource("orders-checkout-api/domain-model.zdl", "config { title \"My Orders\" }")))
        val module = a.modules.single()
        assertEquals("OrdersCheckoutApi", module.id)
        assertEquals("orders-checkout-api", module.slug)
        assertEquals("My Orders", module.name)
    }

    @Test
    fun remoteClientWithTheSameGenericFileNameIsNotTheDeclaringModule() {
        val zdl = """
            config { title "Orders" }
            apis { zdl client CatalogInventoryZdl "https://example.com/catalog-inventory-api/main/domain-model.zdl" }
            entity Order { id Long }
            service OrdersService for (Order) {
                @calls(zdl: CatalogInventoryZdl, command: InventoryService.reserveStock)
                start(Order) Order
            }
        """.trimIndent()
        val a = reader.read(listOf(ZdlSource("domain-model.zdl", zdl)))
        assertEquals(listOf("CatalogInventory", "DomainModel"), a.modules.map { it.id })
        assertEquals(listOf("DomainModel" to "CatalogInventory"), a.calls.map { it.caller to it.callee })
    }

    @Test
    fun missingTitleFallsBackToTheSlugWords() {
        val a = reader.read(listOf(ZdlSource("payments-processing-model.zdl", "")))
        assertEquals("Payments Processing", a.modules.single().name)
    }

    @Test
    fun syntaxErrorsAreProblemsAndOtherFilesStillRead() {
        val a = reader.read(
            listOf(
                ZdlSource("broken.zdl", "event {{{"),
                ZdlSource("ok.zdl", "event Done { id Long }"),
            ),
        )
        assertEquals(listOf("Broken", "Ok"), a.modules.map { it.id })
        assertEquals(1, a.problems.size)
        assertTrue(a.problems.single().startsWith("broken.zdl: "), a.problems.single())
        assertEquals(listOf("Done"), a.eventsOf("Ok").map { it.name })
    }

    @Test
    fun duplicateModuleIdsAreReportedAndTheSecondFileIgnored() {
        val a = reader.read(listOf(ZdlSource("a/x.zdl", ""), ZdlSource("b/x.zdl", "")))
        assertEquals(1, a.modules.size)
        assertEquals(listOf("b/x.zdl: duplicate module 'X', the file is ignored"), a.problems)
    }

    @Test
    fun clientPathsAreMatchedWithWindowsSeparatorsAndDotSegments() {
        val a = reader.read(
            listOf(
                ZdlSource(
                    "docs\\models\\a.zdl",
                    "apis { zdl client B \"../models/./b.zdl\" }\nservice S for (E) {\n @listener(zdl: B, event: Ev)\n m(id) E\n}",
                ),
                ZdlSource("docs/models/b.zdl", "event Ev { id Long }"),
            ),
        )
        assertEquals(listOf("A", "B"), a.modules.map { it.id })
        assertTrue(a.modules.none { it.referenced })
        assertEquals(listOf("B.Ev -> A (S.m)"), edges(a))
    }

    private companion object {
        const val COUNT_EVENTS = 25
        const val COUNT_EDGES = 14
    }
}
