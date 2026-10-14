package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** OpenAPI, AsyncAPI and ZDL shapes the Arcadia files do not cover. */
class SpecPagesTest {

    private fun page(kind: SourceKind, text: String): String =
        DslDocs().generate(DocsInput(listOf(DocsSource(kind, "spec.yml", text)))).files.first { it.path != "README.md" }.content

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
        assertTrue(page.contains("| GET | `/pets` | listPets | pets, read | All \\| pets |"), page)
        assertTrue(page.contains("|  | /owners |  |  | Defined in `other.yml#/paths/owners` |"), page)
        assertTrue(page.contains("| Pet | allOf: Base, object |  | A pet |"), page)
        assertTrue(page.contains("| Pets | array of Pet |  |  |"), page)
        assertTrue(page.contains("| Anything | array of any |  |  |"), page)
        assertTrue(page.contains("| Untyped |  |  |  |"), page)
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

        assertTrue(page.contains("| onOrderPlaced | publish | orders.placed | orders.placed | OrderPlaced |"), page)
        assertTrue(page.contains("| onOrderCancelled | subscribe | orders.cancelled | orders.cancelled | OrderCancelled, OrderVoided |"), page)
        assertTrue(page.contains("| OrderPlaced | inline | application/schema+json | Placed |"), page)
        assertTrue(page.contains("| OrderCancelled | `schemas/cancelled.json` |  | Cancelled |"), page)
        assertTrue(page.contains("| Shared |  |  | Defined in `common.yml#/components/messages/Shared` |"), page)
        assertTrue(page.contains("| Empty |  |  |  |"), page)
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
        assertTrue(withMessages.contains("| send | send | placed | orders.placed | B |"), withMessages)

        val empty = page(SourceKind.ASYNCAPI, "asyncapi: 3.0.0")
        assertTrue(empty.contains("## spec.yml\n"), empty)
        assertTrue(empty.contains("No operations."), empty)
        assertFalse(empty.contains("### Messages"))
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

        assertTrue(page.contains("## Shop\n"), page)
        assertTrue(page.contains("| Customer | Aggregate root | A customer of the shop. |"), page)
        assertTrue(page.contains("| Address | Embedded entity |  |"), page)
        assertTrue(page.contains("| CustomerAggregate | Aggregate |  |"), page)
        assertTrue(page.contains("| CustomerView | Output |  |"), page)
        assertTrue(page.contains("| CustomerService | register | Customer | Customer | CustomerRegistered | Registers a customer. |"), page)
        assertTrue(page.contains("| CustomerService | get | id | Customer[] |  |  |"), page)
        assertTrue(page.contains("| CustomerRegistered | name: String, tags: String[] |  |"), page)
    }
}
