package io.zenwave360.language.zdl.view

import io.zenwave360.language.readViewsTestFile
import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZdlDomainTest {

    private fun domain(zdl: String) = ZdlDomain(ZdlParser().parseModel(zdl))

    /** `name:Type`, with a trailing `*` on fields that came from `@copy`. */
    private fun ZdlDomain.fields(type: String) = fieldsOf(type).map { "${it.name}:${it.type}${if (it.copied) "*" else ""}" }

    private val copyZdl = """
        entity Order {
            id Long
            status String
            total BigDecimal
        }
        relationship OneToMany {
            Order{lines} to Line
        }
        entity Line {
            qty Integer
        }
        @copy(Order)
        event OrderPlaced {
            status Integer
            extra String
        }
        @copy(OrderPlaced)
        event OrderPlacedV2 {
            more String
        }
        @copy(Missing)
        event Orphan {
            own String
        }
        @copy(Order)
        input OrderInput {
            note String
        }
    """.trimIndent()

    @Test
    fun copyOwnFieldReplacesCopiedInPlaceAndRelationshipsAreNotCopied() {
        val d = domain(copyZdl)
        assertEquals(listOf("id:Long*", "status:Integer", "total:BigDecimal*", "extra:String"), d.fields("OrderPlaced"))
        assertEquals(listOf("id:Long*", "status:String*", "total:BigDecimal*", "note:String"), d.fields("OrderInput"))
    }

    @Test
    fun copyIsResolvedRecursivelyAndUnknownSourceIsIgnored() {
        val d = domain(copyZdl)
        assertEquals(
            listOf("id:Long*", "status:Integer*", "total:BigDecimal*", "extra:String*", "more:String"),
            d.fields("OrderPlacedV2"),
        )
        assertEquals(listOf("own:String"), d.fields("Orphan"))
        assertEquals(listOf("Missing"), d.copiesOf("Orphan"))
    }

    @Test
    fun copyCycleDoesNotLoop() {
        val d = domain("@copy(B)\nevent A { a String }\n@copy(A)\nevent B { b String }")
        assertEquals(listOf("a:String*", "b:String"), d.fields("B"))
    }

    @Test
    fun commandVersusQuery() {
        val d = domain(
            """
            service S for (A) {
                withEv(X) A withEvents E
                @transition(to: Y)
                trans(id) A
                @listener(event: E)
                listen(X) A
                @asyncapi(channel: "c")
                async(X) A
                @calls(zdl: Z, command: Svc.m)
                call(X) A
                getIt(id) A?
                search(Q) A[]
            }
            """.trimIndent(),
        )
        val s = d.services.single()
        assertEquals(listOf("withEv", "trans", "listen", "async", "call"), s.commands.map { it.name })
        assertEquals(listOf("getIt", "search"), s.queries.map { it.name })
        assertTrue(s.queries.all { it.query })
    }

    @Test
    fun reachabilityStopsAtOtherRootsAndFindsUnreachableTypes() {
        val d = domain(
            """
            @aggregate
            entity A { b B  c C }
            @aggregate
            entity C { d D }
            entity B { k K }
            entity D { x String }
            entity Lonely { y String }
            enum K { ONE }
            """.trimIndent(),
        )
        val reach = d.reachableFrom("A")
        assertEquals(listOf("B"), reach.entities)
        assertEquals(listOf("K"), reach.enums)
        assertEquals(listOf("C"), reach.otherRoots)
        assertEquals(setOf("A", "C"), d.aggregateRoots)
        assertEquals(listOf("Lonely"), d.unreachableTypes())
        assertFalse("D" in d.unreachableTypes())
    }

    @Test
    fun nestedTypesAndEnumValues() {
        val d = domain(
            """
            event Stock {
                items StockItem[] {
                    sku String
                    parts Part[] {
                        n Integer
                    }
                }
            }
            enum Level { LOW(1), HIGH(2) }
            """.trimIndent(),
        )
        assertEquals(listOf("StockItem", "Part"), d.nestedTypesOf("Stock"))
        assertEquals(listOf("Stock"), d.events.map { it.name })
        assertEquals(listOf("LOW=1", "HIGH=2"), d.enumValues("Level").map { it.name + "=" + it.value })
    }

    @Test
    fun modulithPaymentsProcessing() {
        val d = domain(readViewsTestFile("arcadia-editions-modulith/payments-processing-domain-model.zdl"))
        val payment = d.aggregates.single()
        assertEquals("Payment", payment.root)
        assertEquals(ZdlLifecycle("status", "AUTHORIZATION_REQUESTED"), payment.lifecycle)
        val service = d.services.single()
        assertEquals(listOf(payment), d.aggregatesOf(service))
        assertEquals(4, service.commands.size)
        assertEquals(ZdlTransition(listOf("AUTHORIZED", "AUTHORIZATION_REQUESTED", "FAILED"), listOf("VOIDED")), service.methods.last().transition)
        assertEquals(listOf("PaymentAuthorized", "PaymentDeclined", "PaymentFailed"), service.methods.first().eventNames)
    }
}
