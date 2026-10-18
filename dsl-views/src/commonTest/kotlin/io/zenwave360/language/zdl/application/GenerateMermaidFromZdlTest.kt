package io.zenwave360.language.zdl.application

import io.zenwave360.language.readTestFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateMermaidFromZdlTest {

    private val forbiddenLine = Regex("^\\s*(click|link|callback|style|classDef|cssClass)(\\s|$)", RegexOption.IGNORE_CASE)

    private fun assertInert(mermaid: String) {
        assertTrue(mermaid.startsWith("classDiagram\n"), mermaid)
        // Mermaid reads everything inside a class body up to `}` as member text, so directives can
        // only appear at statement level, outside class bodies.
        var inClassBody = false
        for (line in mermaid.lines()) {
            when {
                inClassBody -> {
                    assertFalse(line.contains('{'), "nested brace in member line: $line")
                    if (line.trim() == "}") inClassBody = false
                }
                line.trimEnd().endsWith("{") -> inClassBody = true
                else -> assertFalse(forbiddenLine.containsMatchIn(line), "directive in line: $line")
            }
        }
        for (fragment in listOf("%%{", "href", "http:", "https:", "javascript:", "<script", "callback")) {
            assertFalse(mermaid.contains(fragment, ignoreCase = true), "'$fragment' in:\n$mermaid")
        }
    }

    @Test
    fun execute_RendersAggregatesEntitiesEnumsServicesAndRelationships() {
        val zdl = """
            aggregate OrderAggregate(CustomerOrder) {
                cancel(CancelInput?) withEvents OrderCancelled [OrderRejected|OrderFailed]
            }

            @aggregate
            entity CustomerOrder {
                status OrderStatus required
                orderItems OrderItem[] {
                    name String
                }
            }

            entity Customer {
                name String
            }

            entity Address {
                street String
            }

            enum OrderStatus {
                RECEIVED, CANCELLED
            }

            enum Level {
                LOW(-1), HIGH(1)
            }

            input CancelInput {
                reason String
            }

            output OrderSummary {
                id String
            }

            event OrderCancelled {
                id String
            }

            relationship ManyToOne {
                Address{customer} to Customer
            }

            relationship OneToMany {
                Customer{addresses} to Address{owner}
            }

            service OrderService for (OrderAggregate) {
                cancelOrder(id, CancelInput) OrderSummary[] withEvents OrderCancelled
                findOrder(id) CustomerOrder?
            }
        """.trimIndent()

        val expected = """
            classDiagram
                class OrderAggregate {
                    <<aggregate>>
                    cancel(CancelInput?) withEvents OrderCancelled OrderRejected or OrderFailed
                }
                class CustomerOrder {
                    <<aggregate>>
                    OrderStatus status
                    OrderItem[] orderItems
                }
                class OrderItem {
                    <<embedded>>
                    String name
                }
                class Customer {
                    String name
                }
                class Address {
                    String street
                }
                class OrderStatus {
                    <<enumeration>>
                    RECEIVED
                    CANCELLED
                }
                class Level {
                    <<enumeration>>
                    LOW = -1
                    HIGH = 1
                }
                class CancelInput {
                    <<input>>
                    String reason
                }
                class OrderSummary {
                    <<output>>
                    String id
                }
                class OrderCancelled {
                    <<event>>
                    String id
                }
                class OrderService {
                    <<service>>
                    cancelOrder(id, CancelInput) OrderSummary[] withEvents OrderCancelled
                    findOrder(id) CustomerOrder?
                }
                OrderAggregate *-- CustomerOrder
                CustomerOrder --> OrderStatus : status
                CustomerOrder *-- "*" OrderItem : orderItems
                OrderService ..> OrderAggregate
                OrderService ..> CancelInput
                OrderService ..> OrderSummary
                OrderService ..> OrderCancelled
                Address "*" --> "1" Customer : customer
                Customer "1" -- "*" Address : addresses / owner

        """.trimIndent()

        val mermaid = GenerateMermaidFromZdl().execute(zdl)
        assertEquals(expected, mermaid)
        assertInert(mermaid)
    }

    @Test
    fun execute_EmptyDocumentIsAnEmptyDiagram() {
        assertEquals("classDiagram\n    note \"No types declared\"\n", GenerateMermaidFromZdl().execute(""))
    }

    @Test
    fun execute_NamesThatMermaidWouldInterpretAreMadeSafe() {
        val zdl = """
            entity click {
                name String
            }
            entity style {
                owner click
            }
            entity Other.Name {
                name String
            }
        """.trimIndent()

        val mermaid = GenerateMermaidFromZdl().execute(zdl)

        assertTrue(mermaid.contains("    class click_ {\n"), mermaid)
        assertTrue(mermaid.contains("    style_ --> click_ : owner\n"), mermaid)
        assertTrue(mermaid.contains("    class Other_Name[\"Other.Name\"]\n"), mermaid)
        assertInert(mermaid)
    }

    @Test
    fun execute_TestCorporaRenderInertDiagrams() {
        for (file in listOf(
            "complete.zdl", "composed.zdl", "nested-fields.zdl", "nested-input-output-model.zdl",
            "order-fulfillment-state-machine.zdl", "policies.zdl", "problems.zdl",
        )) {
            val mermaid = GenerateMermaidFromZdl().execute(readTestFile(file))
            assertInert(mermaid)
            assertTrue(mermaid.lines().count { it.isNotBlank() } > 1, file)
        }
        val complete = GenerateMermaidFromZdl().execute(readTestFile("complete.zdl"))
        assertTrue(complete.contains("CustomerOrderAggregate *-- CustomerOrder"), complete)
        assertTrue(complete.contains("<<enumeration>>"), complete)
        assertTrue(complete.contains("<<service>>"), complete)
    }
}
