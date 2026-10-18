package io.zenwave360.language.apis.view

import io.zenwave360.language.readViewsTestFile
import io.zenwave360.language.zdl.view.MermaidLight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiSchemaToMermaidClassDiagramTransformerTest {

    private val transformer = ApiSchemaToMermaidClassDiagramTransformer()

    private fun diagram(body: String) = MermaidLight.FRONT_MATTER + "classDiagram\n" + body.trimIndent().lines().joinToString("") { "    $it\n" }

    @Suppress("UNCHECKED_CAST")
    private fun openApi(): Map<String, Any?> = jsonTree(
        """
        {"components":{"schemas":{
          "Order":{"type":"object","required":["id","status"],"description":"An order","properties":{
            "id":{"type":"string","format":"uuid"},
            "status":{"${'$'}ref":"#/components/schemas/OrderStatus"},
            "note":{"type":"string"},
            "lines":{"type":"array","items":{"${'$'}ref":"#/components/schemas/Line"}},
            "meta":{"type":"object","additionalProperties":{"type":"string"}},
            "owner":{"oneOf":[{"${'$'}ref":"#/components/schemas/Person"},{"${'$'}ref":"https://example.org/s.json#/Company"}]},
            "address":{"type":"object","properties":{"city":{"type":"string"}}},
            "created":{"type":"string","format":"date-time"}}},
          "OrderStatus":{"type":"string","enum":["NEW","DONE"]},
          "Line":{"allOf":[{"${'$'}ref":"#/components/schemas/Base"},
            {"type":"object","required":["qty"],"properties":{"qty":{"type":"integer","format":"int32"}}}]},
          "Base":{"type":"object","properties":{"sku":{"type":"string"}}},
          "Person":{"type":"object","properties":{"name":{"type":"string"}}}}}}
        """.trimIndent(),
    ) as Map<String, Any?>

    @Test
    fun `json schema root with ref, required, array, enum, allOf, map, oneOf and nested object`() {
        val builder = ApiSchemaGraphBuilder(openApi())
        val root = builder.addJsonSchema(null, jsonTree("""{"${'$'}ref":"#/components/schemas/Order"}"""))
        val graph = builder.build()
        assertEquals("Order", root)
        assertEquals(listOf("https://example.org/s.json#/Company"), graph.unresolved)
        assertEquals(
            diagram(
                """
                    class Order {
                        <<response>>
                        string/uuid id
                        OrderStatus status
                        string? note
                        Line[]? lines
                        map~string, string~? meta
                        oneOf Person or Company? owner
                        OrderAddress? address
                        string/date-time? created
                    }
                    class OrderStatus {
                        <<enumeration>>
                        NEW
                        DONE
                    }
                    class Line {
                        string? sku
                        integer/int32 qty
                    }
                    class Person {
                        string? name
                    }
                    class Company {
                        <<external>>
                    }
                    class OrderAddress {
                        string? city
                    }
                    Order --> OrderStatus : status
                    Order *-- "*" Line : lines
                    Order *-- Person : owner
                    Order --> Company : owner
                    Order *-- OrderAddress : address
                    style Order fill:#fbd3ee,stroke:#333333,color:#111111
                    style OrderStatus fill:#f2f2f2,stroke:#333333,color:#111111
                    style Line fill:#ffffff,stroke:#333333,color:#111111
                    style Person fill:#ffffff,stroke:#333333,color:#111111
                    style Company fill:#ffffff,stroke:#333333,color:#111111
                    style OrderAddress fill:#ffffff,stroke:#333333,color:#111111
                """,
            ),
            transformer.transform(root, graph, "response"),
        )
        assertEquals("Order", graph.reachableFrom("Order").first().name)
        assertEquals("An order", graph.schemas.getValue("Order").description)
        assertNull(transformer.transform("Nope", graph))
    }

    @Test
    fun `unresolved external ref becomes a stub class and is reported`() {
        val builder = ApiSchemaGraphBuilder()
        val root = builder.addJsonSchema(null, jsonTree("""{"${'$'}ref":"common/types.yml#/components/schemas/Money"}"""))
        val graph = builder.build()
        assertEquals("Money", root)
        assertEquals(listOf("common/types.yml#/components/schemas/Money"), graph.unresolved)
        assertEquals(
            diagram(
                """
                    class Money {
                        <<external>>
                    }
                    style Money fill:#ffffff,stroke:#333333,color:#111111
                """,
            ),
            transformer.transform(root, graph, "message"),
        )
    }

    @Test
    fun `external file ref is read through the resolver`() {
        val common = jsonTree("""{"components":{"schemas":{"Money":{"type":"object","required":["amount"],"properties":{"amount":{"type":"number"}}}}}}""")
        val builder = ApiSchemaGraphBuilder(
            resolver = { file, base -> if (file == "common.yml") ResolvedNode(common, "$base/common.yml") else null },
        )
        val root = builder.addJsonSchema(
            "Invoice",
            jsonTree("""{"type":"object","properties":{"total":{"${'$'}ref":"common.yml#/components/schemas/Money"}}}"""),
        )
        val graph = builder.build()
        assertTrue(graph.unresolved.isEmpty())
        assertEquals(
            diagram(
                """
                    class Invoice {
                        <<request>>
                        Money? total
                    }
                    class Money {
                        number amount
                    }
                    Invoice *-- Money : total
                    style Invoice fill:#ffffff,stroke:#333333,color:#111111
                    style Money fill:#ffffff,stroke:#333333,color:#111111
                """,
            ),
            transformer.transform(root, graph, "request"),
        )
    }

    @Test
    fun `avro record with nested record, enum, array, union with null and logical type`() {
        val avro = jsonTree(
            """
            {"type":"record","name":"OrderPlaced","namespace":"com.acme","doc":"An order was placed","fields":[
              {"name":"orderId","type":"string"},
              {"name":"placedAt","type":{"type":"long","logicalType":"timestamp-millis"}},
              {"name":"note","type":["null","string"]},
              {"name":"channel","type":{"type":"enum","name":"Channel","symbols":["WEB","APP"]}},
              {"name":"customer","type":{"type":"record","name":"Customer","fields":[
                {"name":"name","type":"string"},
                {"name":"channel","type":"Channel"}]}},
              {"name":"lines","type":{"type":"array","items":{"type":"record","name":"Line","fields":[
                {"name":"sku","type":"string"},{"name":"qty","type":"int"}]}}},
              {"name":"tags","type":{"type":"map","values":"string"}},
              {"name":"payload","type":["null","string","long"]},
              {"name":"previous","type":["null","OrderPlaced"]}]}
            """.trimIndent(),
        )
        val builder = ApiSchemaGraphBuilder()
        val root = builder.addAvro(avro)
        val graph = builder.build()
        assertEquals("OrderPlaced", root)
        assertEquals("An order was placed", graph.schemas.getValue("OrderPlaced").description)
        assertEquals(
            diagram(
                """
                    class OrderPlaced {
                        <<message>>
                        string orderId
                        long/timestamp-millis placedAt
                        string? note
                        Channel channel
                        Customer customer
                        Line[] lines
                        map~string, string~ tags
                        string or long? payload
                        OrderPlaced? previous
                    }
                    class Channel {
                        <<enumeration>>
                        WEB
                        APP
                    }
                    class Customer {
                        string name
                        Channel channel
                    }
                    class Line {
                        string sku
                        int qty
                    }
                    OrderPlaced --> Channel : channel
                    OrderPlaced *-- Customer : customer
                    OrderPlaced *-- "*" Line : lines
                    OrderPlaced *-- OrderPlaced : previous
                    Customer --> Channel : channel
                    style OrderPlaced fill:#ffe0bf,stroke:#333333,color:#111111
                    style Channel fill:#f2f2f2,stroke:#333333,color:#111111
                    style Customer fill:#ffffff,stroke:#333333,color:#111111
                    style Line fill:#ffffff,stroke:#333333,color:#111111
                """,
            ),
            transformer.transform(root, graph, "message"),
        )
    }

    @Test
    fun `avro types referenced by name are loaded next to the schema or stubbed`() {
        val resolver = RefResolver { ref, base ->
            if (ref == "ShippingAddressInput.avsc") ResolvedNode(jsonTree(readViewsTestFile("api-schemas/avro/$ref")), base) else null
        }
        val builder = ApiSchemaGraphBuilder(resolver = resolver)
        val root = builder.addAvro(jsonTree(readViewsTestFile("api-schemas/avro/ScheduleFulfillmentInput.avsc")))
        val graph = builder.build()
        assertEquals(listOf("FulfillmentItemInput"), graph.unresolved)
        assertEquals(
            diagram(
                """
                    class ScheduleFulfillmentInput {
                        <<message>>
                        string orderId
                        string customerId
                        ShippingAddressInput shippingAddress
                        FulfillmentItemInput[] items
                    }
                    class ShippingAddressInput {
                        string name
                        string line1
                        string? line2
                        string city
                        string? region
                        string postalCode
                        string country
                    }
                    class FulfillmentItemInput {
                        <<external>>
                    }
                    ScheduleFulfillmentInput *-- ShippingAddressInput : shippingAddress
                    ScheduleFulfillmentInput --> FulfillmentItemInput : items
                    style ScheduleFulfillmentInput fill:#ffe0bf,stroke:#333333,color:#111111
                    style ShippingAddressInput fill:#ffffff,stroke:#333333,color:#111111
                    style FulfillmentItemInput fill:#ffffff,stroke:#333333,color:#111111
                """,
            ),
            transformer.transform(root, graph, "message"),
        )
    }
}
