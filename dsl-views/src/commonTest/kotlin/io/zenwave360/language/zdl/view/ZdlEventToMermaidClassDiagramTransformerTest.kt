package io.zenwave360.language.zdl.view

import io.zenwave360.language.readViewsTestFile
import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZdlEventToMermaidClassDiagramTransformerTest {

    private val fm = "---\nconfig:\n  theme: default\n  themeVariables:\n    lineColor: \"#888888\"\n    edgeLabelBackground: \"#ffffff\"\n---\n"

    private fun transform(zdl: String) = ZdlEventToMermaidClassDiagramTransformer().transform(ZdlParser().parseModel(zdl))

    @Test
    fun frontMatterIsTheLightThemeAndStylesAreLiteral() {
        assertEquals(fm, MermaidLight.FRONT_MATTER)
        assertEquals("fill:#ffe0bf,stroke:#333333,color:#111111", MermaidLight.classStyle("event"))
        assertEquals("fill:#fbd3ee,stroke:#333333,color:#111111", MermaidLight.classStyle("aggregate"))
        assertEquals("fill:#f2f2f2,stroke:#333333,color:#111111", MermaidLight.classStyle("enumeration"))
        assertEquals("fill:#ffffff,stroke:#333333,color:#111111", MermaidLight.classStyle("embedded"))
    }

    @Test
    fun perServiceWithCopyNestedTypesAlternativesAndOtherEvents() {
        val zdl = """
            entity Order {
                id Long
                status String
            }
            input PlaceInput {
                sku String
            }
            service OrderService for (Order) {
                placeOrder(PlaceInput) Order withEvents [OrderPlaced | StockUnavailable]
                getOrder(id) Order?
            }
            service AuditService for (Order) {
                @transition(to: DONE)
                audit(PlaceInput) Order withEvents OrderPlaced
                noEvents(PlaceInput) Order withEvents Missing
            }
            service QueryOnly for (Order) {
                getAll(id) Order?
            }
            @copy(Order)
            event OrderPlaced {
                status String
                placedBy String
            }
            event StockUnavailable {
                items StockItem[] {
                    sku String
                    wanted Integer
                }
                reason String
            }
            event Lonely {
                note String
            }
        """.trimIndent()

        val expectedOrderService = fm + """
            classDiagram
                class OrderPlaced {
                    <<event>>
                    Long id
                    String status
                    String placedBy
                }
                class StockUnavailable {
                    <<event>>
                    StockItem[] items
                    String reason
                }
                class StockItem {
                    <<embedded>>
                    String sku
                    Integer wanted
                }
                StockUnavailable *-- "*" StockItem : items
                style OrderPlaced fill:#ffe0bf,stroke:#333333,color:#111111
                style StockUnavailable fill:#ffe0bf,stroke:#333333,color:#111111
                style StockItem fill:#ffffff,stroke:#333333,color:#111111

        """.trimIndent()
        val expectedOthers = fm + """
            classDiagram
                class Lonely {
                    <<event>>
                    String note
                }
                style Lonely fill:#ffe0bf,stroke:#333333,color:#111111

        """.trimIndent()
        val expectedAudit = fm + """
            classDiagram
                class OrderPlaced {
                    <<event>>
                    Long id
                    String status
                    String placedBy
                }
                style OrderPlaced fill:#ffe0bf,stroke:#333333,color:#111111

        """.trimIndent()

        val result = transform(zdl)
        assertEquals(listOf("OrderService", "AuditService"), result.services.map { it.service })
        assertEquals(listOf("OrderPlaced", "StockUnavailable"), result.services[0].events)
        assertEquals(expectedOrderService, result.services[0].mermaid)
        assertEquals(expectedAudit, result.services[1].mermaid)
        assertEquals(expectedOthers, result.others)
        assertEquals(listOf("Lonely"), result.otherEvents)
    }

    @Test
    fun noOthersWhenEveryEventIsEmitted() {
        val result = transform(
            """
            service S for (X) {
                go(In) X withEvents Done
            }
            event Done { a String }
            """.trimIndent(),
        )
        assertNull(result.others)
        assertTrue(result.otherEvents.isEmpty())
    }

    @Test
    fun modulithOrdersCheckoutHasTheNestedStockItems() {
        val result = transform(readViewsTestFile("arcadia-editions-modulith/orders-checkout-domain-model.zdl"))
        val diagram = result.services.single().mermaid
        assertTrue(diagram.startsWith(fm + "classDiagram\n"))
        assertTrue(diagram.contains("<<event>>"))
        assertTrue(diagram.contains("*-- \"*\""))
        assertNull(result.others)
    }
}
