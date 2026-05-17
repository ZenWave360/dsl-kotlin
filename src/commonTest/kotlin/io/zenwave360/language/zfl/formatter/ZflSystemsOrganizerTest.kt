package io.zenwave360.language.zfl.formatter

import io.zenwave360.language.zfl.ZflParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZflSystemsOrganizerTest {

    @Test
    fun organizeSystems_rebuilds_systems_from_policy_services() {
        val input = """
            @flag
            systems {
                // keep this comment with Orders
                @zdl("orders/model.zdl")
                @external
                Orders {
                    // keep this comment with OrdersService
                    @sync
                    service OrdersService for(Product, LegacyOrder) {
                        commands: staleCommand
                    }
                }

                Payments {
                    service LegacyPaymentsService {
                        commands: stalePayment
                    }
                }
            }

            flow CheckoutFlow {
                when CheckoutStarted do createOrder {
                    service Orders/OrdersService/Order
                    emits OrderCreated
                }

                when OrderCreated do chargePayment {
                    service Payments.PaymentService
                    emits PaymentAuthorized
                }

                when PaymentRetryRequested do chargePayment {
                    service Payments.PaymentService
                    emits PaymentAuthorized
                }
            }
        """.trimIndent()

        val expected = """
            @flag
            systems {
                // keep this comment with Orders
                @zdl("orders/model.zdl")
                @external
                Orders {
                    // keep this comment with OrdersService
                    @sync
                    service OrdersService for(Order) {
                        commands: createOrder
                    }
                }

                Payments {
                    service PaymentService {
                        commands: chargePayment
                    }
                }
            }

            flow CheckoutFlow {
                when CheckoutStarted do createOrder {
                    service Orders / OrdersService / Order
                    emits OrderCreated
                }

                when OrderCreated do chargePayment {
                    service Payments.PaymentService
                    emits PaymentAuthorized
                }

                when PaymentRetryRequested do chargePayment {
                    service Payments.PaymentService
                    emits PaymentAuthorized
                }
            }
        """.trimIndent() + "\n"

        val organized = ZflParser().organizeSystems(input)

        assertEquals(expected, organized)
    }

    @Test
    fun organizeSystems_inserts_systems_block_when_missing_and_ignores_actions_without_service() {
        val input = """
            config {
                basePackage "io.zenwave360.example"
            }

            flow SampleFlow {
                when Started do createInvoice {
                    service Billing
                    emits InvoiceCreated
                }

                when InvoiceCreated do notifyAccounting {
                    emits AccountingNotified
                }
            }
        """.trimIndent()

        val expected = """
            config {
                basePackage "io.zenwave360.example"
            }

            systems {
                Billing {
                    service Billing {
                        commands: createInvoice
                    }
                }
            }

            flow SampleFlow {
                when Started do createInvoice {
                    service Billing
                    emits InvoiceCreated
                }

                when InvoiceCreated do notifyAccounting {
                    emits AccountingNotified
                }
            }
        """.trimIndent() + "\n"

        val organized = ZflParser().organizeSystems(input)

        assertEquals(expected, organized)
    }

    @Test
    fun organizeSystems_unions_aggregates_and_commands_in_first_seen_order() {
        val input = """
            systems {
                Inventory {
                    service OldService {
                        commands: oldCommand
                    }
                }
            }

            flow InventoryFlow {
                when StockCheckRequested do reserveStock {
                    service Inventory/InventoryService/Product
                    emits StockReserved
                }

                when InventorySyncRequested do syncInventory {
                    service Inventory/InventoryService/Warehouse
                    emits InventorySynced
                }

                when RetryRequested do reserveStock {
                    service Inventory/InventoryService/Product
                    emits StockReserved
                }
            }
        """.trimIndent()

        val expected = """
            systems {
                Inventory {
                    service InventoryService for(Product, Warehouse) {
                        commands: reserveStock, syncInventory
                    }
                }
            }

            flow InventoryFlow {
                when StockCheckRequested do reserveStock {
                    service Inventory / InventoryService / Product
                    emits StockReserved
                }

                when InventorySyncRequested do syncInventory {
                    service Inventory / InventoryService / Warehouse
                    emits InventorySynced
                }

                when RetryRequested do reserveStock {
                    service Inventory / InventoryService / Product
                    emits StockReserved
                }
            }
        """.trimIndent() + "\n"

        val organized = ZflParser().organizeSystems(input)
        val model = ZflParser().parseModel(organized)

        assertEquals(expected, organized)
        assertTrue(model.getProblems().isEmpty(), "Organized ZFL should parse without problems")
    }

    @Test
    fun organizeSystems_preserves_service_level_comments_and_annotations() {
        val input = """
            systems {
                Catalog {
                    // service note
                    @ownedBy("catalog-team")
                    service CatalogService for(Product) {
                        commands: oldCommand
                    }
                }
            }

            flow CatalogFlow {
                when RefreshRequested do refreshCatalog {
                    service Catalog/CatalogService
                    emits CatalogRefreshed
                }
            }
        """.trimIndent()

        val expected = """
            systems {
                Catalog {
                    // service note
                    @ownedBy("catalog-team")
                    service CatalogService for(Product) {
                        commands: refreshCatalog
                    }
                }
            }

            flow CatalogFlow {
                when RefreshRequested do refreshCatalog {
                    service Catalog / CatalogService
                    emits CatalogRefreshed
                }
            }
        """.trimIndent() + "\n"

        val organized = ZflParser().organizeSystems(input)

        assertEquals(expected, organized)
    }

    @Test
    fun organizeSystems_preserves_existing_aggregates_when_policy_has_none_for_service() {
        val input = """
            systems {
                Orders {
                    service OrdersService for(Product, LegacyOrder) {
                        commands: oldCommand
                    }
                }
            }

            flow OrdersFlow {
                when CheckoutStarted do createOrder {
                    service Orders/OrdersService
                    emits OrderCreated
                }
            }
        """.trimIndent()

        val expected = """
            systems {
                Orders {
                    service OrdersService for(Product, LegacyOrder) {
                        commands: createOrder
                    }
                }
            }

            flow OrdersFlow {
                when CheckoutStarted do createOrder {
                    service Orders / OrdersService
                    emits OrderCreated
                }
            }
        """.trimIndent() + "\n"

        val organized = ZflParser().organizeSystems(input)

        assertEquals(expected, organized)
    }
}
