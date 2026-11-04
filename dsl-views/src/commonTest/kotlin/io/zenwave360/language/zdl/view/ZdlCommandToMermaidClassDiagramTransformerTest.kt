package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class ZdlCommandToMermaidClassDiagramTransformerTest {

    private val transformer = ZdlCommandToMermaidClassDiagramTransformer()
    private val fm = MermaidLight.FRONT_MATTER

    private fun transform(zdl: String) = transformer.transform(ZdlParser().parseModel(zdl))

    @Test
    fun methodWithInputAndOutputHasTheSignatureAtTheCentre() {
        val diagram = transform(
            """
            input PlaceOrderInput { sku String }
            entity Order { id Long }
            service OrderService for (Order) {
                placeOrder(PlaceOrderInput) Order
            }
            """.trimIndent(),
        ).single()

        assertEquals("OrderService", diagram.service)
        assertEquals("placeOrder", diagram.command)
        assertEquals(
            fm + """
                classDiagram
                    class cmd_OrderService__placeOrder["placeOrder(PlaceOrderInput) Order"]
                    class PlaceOrderInput {
                        <<input>>
                    }
                    class Order {
                        <<output>>
                    }
                    PlaceOrderInput --> cmd_OrderService__placeOrder
                    cmd_OrderService__placeOrder --> Order
                    style cmd_OrderService__placeOrder fill:#ffffff,stroke:#333333,color:#111111
                    style PlaceOrderInput fill:#ffffff,stroke:#333333,color:#111111
                    style Order fill:#ffffff,stroke:#333333,color:#111111

            """.trimIndent(),
            diagram.mermaid,
        )
    }

    @Test
    fun methodWithoutInputDrawsOnlySignatureAndOutput() {
        val diagram = transform("entity Order { id Long } service OrderService for (Order) { findOrder() Order }").single()

        assertEquals("findOrder() Order", diagram.mermaid.substringAfter("[\"").substringBefore("\"]"))
        assertFalse(diagram.mermaid.contains("<<input>>"))
        assertEquals(1, " --> ".toRegex().findAll(diagram.mermaid).count())
    }

    @Test
    fun methodWithoutOutputDrawsOnlySignatureAndInput() {
        val diagram = transform("input CancelInput { reason String } service OrderService for (Order) { cancel(CancelInput) }").single()

        assertEquals("cancel(CancelInput)", diagram.mermaid.substringAfter("[\"").substringBefore("\"]"))
        assertFalse(diagram.mermaid.contains("<<output>>"))
        assertEquals(1, " --> ".toRegex().findAll(diagram.mermaid).count())
    }

    @Test
    fun voidMethodDrawsOnlyItsSignature() {
        val diagram = transform("service HealthService for (Health) { ping() }").single()

        assertEquals("ping()", diagram.mermaid.substringAfter("[\"").substringBefore("\"]"))
        assertFalse(diagram.mermaid.contains("<<input>>"))
        assertFalse(diagram.mermaid.contains("<<output>>"))
        assertFalse(diagram.mermaid.contains(" --> "))
    }

    @Test
    fun sameNamedMethodsAreDistinguishedByTheirService() {
        val diagrams = transform(
            """
            input PlaceInput { sku String }
            entity Order { id Long }
            service CheckoutService for (Order) { place(PlaceInput) Order }
            service RetryService for (Order) { place(PlaceInput) Order }
            """.trimIndent(),
        )

        assertEquals(listOf("CheckoutService" to "place", "RetryService" to "place"), diagrams.map { it.service to it.command })
        assertNotEquals(diagrams[0].mermaid, diagrams[1].mermaid)
        assertEquals("cmd_CheckoutService__place", diagrams[0].mermaid.substringAfter("class ").substringBefore("["))
        assertEquals("cmd_RetryService__place", diagrams[1].mermaid.substringAfter("class ").substringBefore("["))
    }

    @Test
    fun hostileNamesAreSafeInIdentifiersAndLabels() {
        val service = ZdlService("Order Service", emptyList(), null, emptyList())
        val command = ZdlCommand(
            owner = service.name,
            name = "place{Order}\"",
            hasId = false,
            parameter = "Place<Input>",
            parameterIsOptional = false,
            returnType = "Order;drop",
            returnIsArray = false,
            returnIsOptional = false,
            events = emptyList(),
            query = false,
            transition = null,
            javadoc = null,
        )

        val result = transformer.transform(service, command)

        assertEquals("cmd_Order_Service__place_Order__", result.mermaid.substringAfter("class ").substringBefore("["))
        assertEquals("placeOrder(PlaceInput) Orderdrop", result.mermaid.substringAfter("[\"").substringBefore("\"]"))
        assertFalse(result.mermaid.contains("{Order}"))
        assertFalse(result.mermaid.contains(";drop"))
    }

    @Test
    fun arrayAndOptionalReturnsKeepTheirMarkersInTheSignature() {
        val diagrams = transform(
            """
            entity Order { id Long }
            service OrderService for (Order) {
                findAll() Order[]
                findMaybe() Order?
            }
            """.trimIndent(),
        )
        val labels = diagrams.map { it.mermaid.substringAfter("[\"").substringBefore("\"]") }

        assertEquals(listOf("findAll() Order#91;#93;", "findMaybe() Order?"), labels)
        assertNotEquals(labels[0], "findAll() Order")
        assertFalse(diagrams[0].mermaid.contains("Order["))
        assertEquals(1, " --> ".toRegex().findAll(diagrams[0].mermaid).count())
    }

    @Test
    fun focusedLookupReturnsNullForAnUnknownMethod() {
        val domain = ZdlDomain(ZdlParser().parseModel("service Orders for (Order) { place() }"))
        assertNull(transformer.transform(domain, "Orders", "missing"))
    }
}
