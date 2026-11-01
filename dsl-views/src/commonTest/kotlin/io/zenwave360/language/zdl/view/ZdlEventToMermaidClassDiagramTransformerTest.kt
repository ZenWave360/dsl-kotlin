package io.zenwave360.language.zdl.view

import io.zenwave360.language.readViewsTestFile
import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZdlEventToMermaidClassDiagramTransformerTest {

    private val fm = "---\nconfig:\n  theme: default\n  themeVariables:\n    lineColor: \"#888888\"\n    edgeLabelBackground: \"#ffffff\"\n    transitionColor: \"#888888\"\n    transitionLabelColor: \"#111111\"\n---\n"

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
    fun stateTransitionsAndFlowchartSubgraphTitlesAreDarkWhateverThePageTheme() {
        // The state diagram draws lines and arrowheads with transitionColor and edge-label text with transitionLabelColor.
        assertTrue(MermaidLight.FRONT_MATTER.contains("    transitionColor: \"#888888\"\n"))
        assertTrue(MermaidLight.FRONT_MATTER.contains("    transitionLabelColor: \"#111111\"\n"))
        // A flowchart subgraph title takes titleColor, not the colour of its `style`: dark text on every light fill.
        assertEquals(
            "---\nconfig:\n  themeVariables:\n    titleColor: \"#111111\"\n---\n",
            MermaidLight.SUBGRAPH_TITLE_FRONT_MATTER,
        )
        assertTrue(ZdlServiceToEventStormingTransformer.AGGREGATE_STYLE.contains("color:#111"))
        assertTrue(MermaidLight.PLAIN_BOX_STYLE.contains("color:#111"))
    }

    @Test
    fun allEventsOfTheZdlInOneDiagramWithCopyNestedTypesAndJson() {
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
            }
            @copy(Order)
            event OrderPlaced {
                status String
                placedBy String
            }
            event StockUnavailable {
                @json items StockItem[] {
                    sku String
                    wanted Integer
                }
                reason String
            }
            event Lonely {
                note String
            }
        """.trimIndent()

        val expected = fm + """
            classDiagram
                class OrderPlaced {
                    <<event>>
                    Long id
                    String status
                    String placedBy
                }
                class StockUnavailable {
                    <<event>>
                    @json StockItem[] items
                    String reason
                }
                class StockItem {
                    <<embedded>>
                    String sku
                    Integer wanted
                }
                class Lonely {
                    <<event>>
                    String note
                }
                StockUnavailable *-- "*" StockItem : items
                style OrderPlaced fill:#ffe0bf,stroke:#333333,color:#111111
                style StockUnavailable fill:#ffe0bf,stroke:#333333,color:#111111
                style StockItem fill:#ffffff,stroke:#333333,color:#111111
                style Lonely fill:#ffe0bf,stroke:#333333,color:#111111

        """.trimIndent()
        assertEquals(expected, transform(zdl))
    }

    @Test
    fun nullWhenThereAreNoEvents() {
        assertNull(transform("entity A { a String }"))
    }

    @Test
    fun modulithOrdersCheckoutHasTheNestedStockItems() {
        val diagram = transform(readViewsTestFile("arcadia-editions-modulith/orders-checkout-domain-model.zdl"))!!
        assertTrue(diagram.startsWith(fm + "classDiagram\n"))
        assertTrue(diagram.contains("<<event>>"))
        assertTrue(diagram.contains("*-- \"*\""))
    }
}
