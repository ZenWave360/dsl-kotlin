package io.zenwave360.language.eventflow.view

import io.zenwave360.language.readTestFile
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZflToServiceViewModelTransformerTest {

    @Test
    fun testTransform_GroupsCommandsAndEventsBySystemAndServiceLevelsOnly() {
        val zflContent = """
            flow ServiceMapFlow {
                @actor(Customer)
                start CustomerPlacesOrder {
                }

                when CustomerPlacesOrder do createOrder {
                    service Orders
                    emits OrderCreated
                }

                when OrderCreated do authorizePayment {
                    service Payments/Authorization/Payment
                    emits PaymentAuthorized
                    emits PaymentFailed
                }

                when PaymentAuthorized do notifyCustomer {
                    service Notifications.Consumer.Email
                    emits OrderConfirmationSent
                }

                do unboundedCleanup {
                    emits CleanupFinished
                }

                end {
                    completed: OrderConfirmationSent
                }
            }
        """.trimIndent()

        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent))
        val viewModel = ServiceViewLayoutEngine().layout(
            ZflToServiceViewModelTransformer().transform(semanticModel)
        )

        assertEquals("zfl.services.view@1", viewModel.schema)
        assertEquals(
            setOf("Orders", "Payments\nAuthorization", "Notifications\nConsumer", "Unbounded"),
            viewModel.groups.map { it.label }.toSet()
        )

        val createOrder = viewModel.nodes.find { it.id == "command:createOrder" }
        assertNotNull(createOrder)
        assertEquals("group:Orders", createOrder.groupId)

        val authorizePayment = viewModel.nodes.find { it.id == "command:authorizePayment" }
        assertNotNull(authorizePayment)
        assertEquals("group:Payments>Authorization", authorizePayment.groupId)

        val notifyCustomer = viewModel.nodes.find { it.id == "command:notifyCustomer" }
        assertNotNull(notifyCustomer)
        assertEquals("group:Notifications>Consumer", notifyCustomer.groupId)

        val unboundedCleanup = viewModel.nodes.find { it.id == "command:unboundedCleanup" }
        assertNotNull(unboundedCleanup)
        assertEquals("group:Unbounded", unboundedCleanup.groupId)

        assertNotNull(viewModel.nodes.find { it.id == "event:PaymentAuthorized@Payments>Authorization" })
        assertNotNull(viewModel.nodes.find { it.id == "event:PaymentFailed@Payments>Authorization" })
        assertNotNull(viewModel.nodes.find { it.id == "event:CleanupFinished@Unbounded" })

        assertNull(
            viewModel.nodes.find { it.id == "event:CustomerPlacesOrder" },
            "Synthetic start events should not render in services view"
        )

        assertTrue(viewModel.nodes.all { it.position != null })
        assertTrue(viewModel.groups.all { it.position != null && it.dimensions != null })
        assertNotNull(viewModel.bounds)
    }

    @Test
    fun testTransform_PlaceOrderFlow_ProducesExpectedServicesGroups() {
        val content = readTestFile("flow/place-order-flow.zfl")
        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(content))

        val viewModel = ServiceViewLayoutEngine().layout(
            ZflToServiceViewModelTransformer().transform(semanticModel)
        )

        assertEquals(
            setOf(
                "CatalogProducts\nCatalogProductsService",
                "OrdersCheckout\nOrdersCheckoutService",
                "PaymentsProcessing\nPaymentsProcessingService",
                "FulfillmentShipping\nFulfillmentShippingService",
                "NotificationsConsumer\nNotificationsConsumerService"
            ),
            viewModel.groups.map { it.label }.toSet()
        )

        assertNotNull(viewModel.nodes.find { it.id == "command:startOrderCheckout" })
        assertNotNull(viewModel.nodes.find { it.id == "event:StockReserved@CatalogProducts>CatalogProductsService" })
        assertNotNull(viewModel.nodes.find { it.id == "event:StockUnavailable@CatalogProducts>CatalogProductsService" })
        assertNotNull(viewModel.nodes.find { it.id == "event:PaymentAuthorized@PaymentsProcessing>PaymentsProcessingService" })
    }
}
