package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OpenAPI, AsyncAPI and ZDL shapes the Arcadia files do not cover. */
class SpecPagesTest {

    private fun page(kind: SourceKind, text: String): String =
        DslDocs().generate(DocsInput(listOf(DocsSource(kind, "spec.yml", text)))).files.first { it.path != "README.md" && it.path != "architecture.md" }.content

    @Test
    fun openApiReferencesCombinatorsAndArrays() {
        val page = page(
            SourceKind.OPENAPI,
            """
            openapi: 3.1.0
            info: { title: Pets, version: 1.0.0, description: "Pets\nand owners" }
            paths:
              /pets:
                get: { operationId: listPets, description: "All | pets", tags: [pets, read] }
                parameters: []
              /owners:
                ${'$'}ref: other.yml#/paths/owners
            components:
              schemas:
                Pet: { allOf: [ { ${'$'}ref: "#/components/schemas/Base" }, { type: object } ], description: A pet }
                Pets: { type: array, items: { ${'$'}ref: "#/components/schemas/Pet" } }
                Anything: { type: array }
                Untyped: {}
            """.trimIndent(),
        )

        assertTrue(page.contains("## Pets 1.0.0\n\nSource: `spec.yml` · OpenAPI 3.1.0\n\nPets and owners\n"), page)
        assertTrue(page.contains("| GET | `/pets` | listPets |  |  | All \\| pets |"), page)
        assertTrue(page.contains("|  | `/owners` |  |  |  | Defined in `other.yml#/paths/owners` |"), page)
        assertTrue(page.contains("#### Other schemas\n"), page)
        assertTrue(page.contains("##### Pet\n"), page)
        assertTrue(page.contains("A pet\n"), page)
        assertTrue(page.contains("##### Anything\n"), page)
        assertFalse(page.contains("Untyped"), page)
    }

    @Test
    fun asyncApiV2() {
        val page = page(
            SourceKind.ASYNCAPI,
            """
            asyncapi: 2.6.0
            info: { title: Legacy, version: "2" }
            channels:
              orders.placed:
                publish:
                  operationId: onOrderPlaced
                  message: { ${'$'}ref: "#/components/messages/OrderPlaced" }
              orders.cancelled:
                subscribe:
                  operationId: onOrderCancelled
                  message:
                    oneOf:
                      - { ${'$'}ref: "#/components/messages/OrderCancelled" }
                      - { name: OrderVoided }
            components:
              messages:
                OrderPlaced: { name: OrderPlaced, schemaFormat: application/schema+json, payload: { type: object }, description: Placed }
                OrderCancelled: { payload: { ${'$'}ref: "schemas/cancelled.json" }, title: Cancelled }
                Shared: { ${'$'}ref: "common.yml#/components/messages/Shared" }
                Empty: {}
            """.trimIndent(),
        )

        assertTrue(page.contains("| `onOrderPlaced` | publish | `orders.placed` | [`OrderPlaced`](#orderplaced) |  |"), page)
        assertTrue(page.contains("| `onOrderCancelled` | subscribe | `orders.cancelled` | [`OrderCancelled`](#ordercancelled), [`OrderVoided`](#ordervoided) |  |"), page)
        assertTrue(page.contains("##### OrderPlaced\n\nPlaced\n\nPayload `OrderPlacedPayload` · `application/schema+json`\n"), page)
        // Without a file reader the referenced schema is an external class.
        assertTrue(page.contains("##### OrderCancelled\n\nCancelled\n\nPayload `cancelled`\n"), page)
        assertTrue(page.contains("<<external>>"), page)
        assertTrue(page.contains("##### Shared\n\nDefined in common.yml#/components/messages/Shared\n"), page)
        assertTrue(page.contains("##### Empty\n\nNo payload.\n"), page)
    }

    @Test
    fun asyncApiV3OperationMessagesAndNoOperations() {
        val withMessages = page(
            SourceKind.ASYNCAPI,
            """
            asyncapi: 3.0.0
            info: { title: Orders }
            channels:
              placed: { address: orders.placed, messages: { A: {}, B: {} } }
            operations:
              send: { action: send, channel: { ${'$'}ref: "#/channels/placed" }, messages: [ { ${'$'}ref: "#/channels/placed/messages/B" } ] }
            """.trimIndent(),
        )
        assertTrue(withMessages.contains("## Orders\n"), withMessages)
        assertTrue(withMessages.contains("| `send` | send | `placed` | [`B`](#b) |  |"), withMessages)

        val empty = page(SourceKind.ASYNCAPI, "asyncapi: 3.0.0")
        assertTrue(empty.contains("## spec.yml\n"), empty)
        assertTrue(empty.contains("No channels or operations."), empty)
        assertFalse(empty.contains("Messages"))
    }

    @Test
    fun zdlDescriptionsAndKinds() {
        val page = page(
            SourceKind.ZDL,
            """
            /** Shop model. */
            config { title "Shop" }

            /** A customer
             * of the shop. */
            entity Customer { name String address Address }

            @embedded
            entity Address { street String }

            output CustomerView { name String }

            aggregate CustomerAggregate (Customer) {
                register(Customer) withEvents CustomerRegistered
            }

            event CustomerRegistered { name String tags String[] }

            service CustomerService for (CustomerAggregate) {
                /** Registers a customer. */
                register(Customer) Customer withEvents CustomerRegistered
                get(id) Customer[]
            }
            """.trimIndent(),
        )

        assertTrue(page.contains("# Domain model\n\nShop\n\nSource: `spec.yml`\n\nShop model.\n"), page)
        assertTrue(page.contains("#### Customer\n\nAggregate root\n\nA customer of the shop.\n"), page)
        assertTrue(page.contains("| address | [Address](#address) |  |"), page)
        assertTrue(page.contains("Embedded entity of `Customer`"), page)
        assertTrue(page.contains("### CustomerAggregate\n"), page)
        assertTrue(page.contains("#### CustomerView\n"), page)
        assertTrue(page.contains("| register | [Customer](#customer) | [Customer](#customer) | [CustomerRegistered](#customerregistered) |  | Registers a customer. |"), page)
        assertTrue(page.contains("| get | id | [Customer](#customer)[] |  |  |  |"), page)
        assertTrue(page.contains("### CustomerRegistered\n"), page)
        assertTrue(page.contains("| tags | String[] |  |"), page)
        assertFalse(page.contains("Class diagram"), page)
    }
}
