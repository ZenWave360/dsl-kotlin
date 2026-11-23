package io.zenwave360.language.zdl.view

import io.zenwave360.language.readViewsTestFile
import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZdlAggregateToMermaidClassDiagramTransformerTest {

    private val FM = MermaidLight.FRONT_MATTER

    private fun transform(zdl: String) = ZdlAggregateToMermaidClassDiagramTransformer().transform(ZdlParser().parseModel(zdl))

    @Test
    fun embeddedEnumRelationshipOtherAggregateAndUnreachableEntity() {
        val zdl = """
            @aggregate
            entity Order {
                status OrderStatus
                items OrderItem[] {
                    sku String
                }
                customer Customer
                memos Memo
            }
            @aggregate
            entity Customer {
                name String
            }
            entity Memo {
                text String
            }
            entity Line {
                qty Integer
            }
            entity Orphan {
                label String
                kind Kind
                owner Customer
            }
            enum OrderStatus {
                NEW, DONE
            }
            enum Kind {
                A, B
            }
            relationship OneToMany {
                Order{lines} to Line{order}
            }
        """.trimIndent()

        val expectedOrder = FM + """
            classDiagram
                class Order {
                    <<aggregate>>
                    OrderStatus status
                    OrderItem[] items
                    Customer customer
                    Memo memos
                }
                class OrderItem {
                    <<embedded>>
                    String sku
                }
                class Memo {
                    <<entity>>
                    String text
                }
                class Line {
                    <<entity>>
                    Integer qty
                }
                class OrderStatus {
                    <<enumeration>>
                    NEW
                    DONE
                }
                class Customer {
                    <<aggregate>>
                }
                Order --> OrderStatus : status
                Order *-- "*" OrderItem : items
                Order --> Customer : customer
                Order --> Memo : memos
                Order "1" -- "*" Line : lines / order
                style Order fill:#fbd3ee,stroke:#333333,color:#111111
                style OrderItem fill:#ffffff,stroke:#333333,color:#111111
                style Memo fill:#ffffff,stroke:#333333,color:#111111
                style Line fill:#ffffff,stroke:#333333,color:#111111
                style OrderStatus fill:#f2f2f2,stroke:#333333,color:#111111
                style Customer fill:#fbd3ee,stroke:#333333,color:#111111

        """.trimIndent()
        val expectedCustomer = FM + """
            classDiagram
                class Customer {
                    <<aggregate>>
                    String name
                }
                style Customer fill:#fbd3ee,stroke:#333333,color:#111111

        """.trimIndent()
        val expectedOthers = FM + """
            classDiagram
                class Orphan {
                    <<entity>>
                    String label
                    Kind kind
                    Customer owner
                }
                class Kind {
                    <<enumeration>>
                    A
                    B
                }
                Orphan --> Kind : kind
                style Orphan fill:#ffffff,stroke:#333333,color:#111111
                style Kind fill:#f2f2f2,stroke:#333333,color:#111111

        """.trimIndent()

        val result = transform(zdl)
        assertEquals(listOf("Order", "Customer"), result.aggregates.map { it.aggregate })
        assertEquals(expectedOrder, result.aggregates[0].mermaid)
        assertEquals(expectedCustomer, result.aggregates[1].mermaid)
        assertEquals(expectedOthers, result.unused)
    }

    @Test
    fun aggregateBlockNamesTheDiagramAndThereAreNoOthersWhenEverythingIsReachable() {
        val zdl = """
            aggregate OrderAggregate(CustomerOrder) {
                cancel(CancelInput) withEvents OrderCancelled
            }
            entity CustomerOrder {
                total BigDecimal
            }
        """.trimIndent()

        val result = transform(zdl)
        val diagram = result.aggregates.single()
        assertEquals("OrderAggregate", diagram.aggregate)
        assertEquals("CustomerOrder", diagram.root)
        assertEquals(
            FM + "classDiagram\n    class CustomerOrder {\n        <<aggregate>>\n        BigDecimal total\n    }\n" +
                "    style CustomerOrder fill:#fbd3ee,stroke:#333333,color:#111111\n",
            diagram.mermaid,
        )
        assertNull(result.unused)
    }

    @Test
    fun modulithOrdersCheckout() {
        val expected = FM + """
            classDiagram
                class Order {
                    <<aggregate>>
                    String orderId
                    OrderStatus status
                    String customerId
                    String customerEmail
                    String paymentMethodId
                    @json OrderItem[] items
                    @json ShippingAddress shippingAddress
                    BigDecimal totalAmount
                    String currency
                    Instant createdAt
                    Instant confirmedAt
                    Instant cancelledAt
                    String cancellationReason
                }
                class OrderItem {
                    <<embedded>>
                    String sku
                    Integer quantity
                    BigDecimal unitPrice
                }
                class ShippingAddress {
                    <<embedded>>
                    String name
                    String line1
                    String line2
                    String city
                    String region
                    String postalCode
                    String country
                }
                class OrderStatus {
                    <<enumeration>>
                    CREATED
                    CONFIRMED
                    CANCELLED
                }
                Order --> OrderStatus : status
                Order *-- "*" OrderItem : items
                Order *-- ShippingAddress : shippingAddress
                style Order fill:#fbd3ee,stroke:#333333,color:#111111
                style OrderItem fill:#ffffff,stroke:#333333,color:#111111
                style ShippingAddress fill:#ffffff,stroke:#333333,color:#111111
                style OrderStatus fill:#f2f2f2,stroke:#333333,color:#111111

        """.trimIndent()
        val result = transform(readViewsTestFile("arcadia-editions-modulith/orders-checkout-domain-model.zdl"))
        assertEquals(expected, result.aggregates.single().mermaid)
        assertNull(result.unused)
    }

    @Test
    fun inputsOutputsAndUnusedTypesFollowEverySpecifiedStartingPoint() {
        val result = transform(
            """
                input CreateOrder { details InputDetails }
                entity InputDetails { kind Kind }
                output OrderView { customer Customer }
                entity Customer { name String }
                enum Kind { STANDARD }
                event OrderPlaced { eventDetail EventDetail }
                entity EventDetail { value String }
                service Orders for (ServiceDetail) { find(ServiceDetail) OrderView }
                entity ServiceDetail { code String }
                entity Unused { value String }
            """.trimIndent(),
        )

        val inputs = result.inputs!!
        val outputs = result.outputs!!
        val unused = result.unused!!
        assertEquals(listOf("CreateOrder", "InputDetails", "Kind"), Regex("class (\\w+)").findAll(inputs).map { it.groupValues[1] }.toList())
        assertEquals(listOf("OrderView", "Customer"), Regex("class (\\w+)").findAll(outputs).map { it.groupValues[1] }.toList())
        assertTrue(inputs.contains("CreateOrder --> InputDetails : details"), inputs)
        assertTrue(inputs.contains("InputDetails --> Kind : kind"), inputs)
        assertTrue(outputs.contains("OrderView --> Customer : customer"), outputs)
        assertTrue(unused.contains("class Unused"), unused)
        assertFalse(unused.contains("EventDetail"), unused)
        assertFalse(unused.contains("ServiceDetail"), unused)
    }
}
