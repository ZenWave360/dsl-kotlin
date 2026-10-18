package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Grouping, links and referenced files of the OpenAPI and AsyncAPI sections, with small inline specs. */
class ApiSectionsTest {

    private fun generate(kind: SourceKind, text: String, files: Map<String, String> = emptyMap()): DocsOutput =
        DslDocs().generate(DocsInput(listOf(DocsSource(kind, "api/spec.yml", text)), fileReader = DocsFileReader { files[it] }))

    private fun DocsOutput.page(path: String) = files.first { it.path == path }.content

    private val openApi = """
        openapi: 3.0.1
        info: { title: Shop, version: 1.0.0 }
        paths:
          /orders:
            get:
              operationId: listOrders
              responses:
                "200":
                  content: { application/json: { schema: { type: array, items: { ${'$'}ref: "#/components/schemas/Order" } } } }
            post:
              operationId: createOrder
              requestBody:
                content: { application/json: { schema: { ${'$'}ref: "#/components/schemas/NewOrder" } } }
              responses:
                "201":
                  content:
                    application/json:
                      schema: { type: object, properties: { id: { type: string } } }
          /shipments/{id}:
            get:
              operationId: getShipment
              responses:
                "200":
                  content: { application/json: { schema: { ${'$'}ref: "#/components/schemas/Shipment" } } }
        components:
          schemas:
            Address: { type: object, properties: { street: { type: string } } }
            Order: { type: object, required: [id], properties: { id: { type: string }, address: { ${'$'}ref: "#/components/schemas/Address" } } }
            Shipment: { type: object, properties: { address: { ${'$'}ref: "#/components/schemas/Address" } } }
            NewOrder: { type: object, properties: { note: { type: string } } }
            OrderId: { type: string }
    """.trimIndent()

    @Test
    fun openApiGroupsByResponseAndLinksTheOperationsTable() {
        val page = generate(SourceKind.OPENAPI, openApi).page("apis.md")

        assertTrue(page.contains("| GET | `/orders` | listOrders |  | [`Order[]`](#order) |  |"), page)
        assertTrue(page.contains("| POST | `/orders` | createOrder | [`NewOrder`](#neworder) | [`createOrderResponse`](#createorderresponse) |  |"), page)
        assertTrue(page.contains("#### Order\n\nReturned by `GET /orders`.\n"), page)
        assertTrue(page.contains("#### createOrderResponse\n\nReturned by `POST /orders`.\n"), page)
        // Address is tabled once, under the first group that reaches it.
        assertEquals(1, Regex("\\*\\*Address\\*\\*").findAll(page).count(), page)
        assertTrue(page.contains("Also uses [Address](#order) (described above).\n"), page)
        assertTrue(page.contains("#### Other schemas\n"), page)
        assertTrue(page.contains("##### NewOrder\n\nSent to `POST /orders`.\n"), page)
        assertFalse(page.contains("OrderId"), "a scalar component is not a class")
        assertTrue(page.contains("- [Shop 1.0.0](#shop-100)\n  - [Operations](#operations)\n  - [Schemas](#schemas)\n"), page)
    }

    @Test
    fun referencedFilesAreReadAndListedInTheHeader() {
        val spec = """
            asyncapi: 3.0.0
            info: { title: Events }
            channels:
              created: { address: orders.created, messages: { Created: { ${'$'}ref: "#/components/messages/Created" } } }
            operations:
              onCreated: { action: send, channel: { ${'$'}ref: "#/channels/created" } }
            components:
              messages:
                Created:
                  payload:
                    schemaFormat: application/vnd.apache.avro+json;version=1.9.0
                    schema: { ${'$'}ref: avro/Created.avsc }
        """.trimIndent()
        val avro = """{ "type": "record", "name": "Created", "fields": [ { "name": "id", "type": ["null", "string"] }, { "name": "total", "type": "long" } ] }"""

        val page = generate(SourceKind.ASYNCAPI, spec, mapOf("api/avro/Created.avsc" to avro)).page("events.md")

        assertTrue(page.lines()[1].startsWith("<!-- Source: api/spec.yml sha256:"), page)
        assertTrue(page.lines()[2].startsWith("<!-- Source: api/avro/Created.avsc sha256:"), page)
        assertTrue(page.contains("| `total` | `long` |"), page)
        assertTrue(page.contains("string? id"), page)
    }

    @Test
    fun missingReferencedFileIsAProblemAndAnExternalClass() {
        val spec = """
            asyncapi: 3.0.0
            info: { title: Events }
            channels:
              created: { address: orders.created, messages: { Created: { payload: { schemaFormat: avro, schema: { ${'$'}ref: avro/Gone.avsc } } } } }
        """.trimIndent()

        val withoutReader = DslDocs().generate(DocsInput(listOf(DocsSource(SourceKind.ASYNCAPI, "spec.yml", spec))))
        assertTrue(withoutReader.problems.isEmpty(), withoutReader.problems.toString())
        assertTrue(withoutReader.page("events.md").contains("<<external>>"))

        val withReader = DslDocs().generate(
            DocsInput(listOf(DocsSource(SourceKind.ASYNCAPI, "spec.yml", spec)), fileReader = DocsFileReader { null }),
        )
        assertEquals(listOf("spec.yml: referenced file avro/Gone.avsc not found"), withReader.problems)
        assertTrue(withReader.page("events.md").contains("<<external>>"))
    }

    @Test
    fun clientChannelsGetNoTablesAndOperationsFollowTheirChannel() {
        val spec = """
            asyncapi: 3.0.0
            info: { title: Client }
            channels:
              remote: { ${'$'}ref: other.yml#/channels/remote }
            operations:
              onRemote: { action: receive, channel: { ${'$'}ref: "#/channels/remote" } }
        """.trimIndent()
        val other = """
            channels:
              remote:
                address: remote.topic
                messages:
                  Ping: { payload: { type: object, properties: { n: { type: integer } } } }
        """.trimIndent()

        val page = generate(SourceKind.ASYNCAPI, spec, mapOf("api/other.yml" to other)).page("events.md")

        assertFalse(page.contains("### Provided"), page)
        assertTrue(page.contains("### Client of\n"), page)
        assertTrue(page.contains("| `remote` | `other.yml#/channels/remote` | `remote.topic` | [`Ping`](#ping) |"), page)
        assertTrue(page.contains("| `onRemote` | receive | `remote` | [`Ping`](#ping) |  |"), page)
        assertTrue(page.contains("##### Ping\n"), page)
        assertTrue(page.contains("<<message>>"), page)
        assertFalse(page.contains("| Field |"), "a client message has a diagram only")
    }
}
