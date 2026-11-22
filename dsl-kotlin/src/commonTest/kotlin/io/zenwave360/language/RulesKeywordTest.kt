package io.zenwave360.language

import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zdl.formatter.ZdlFormatter
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.formatter.ZflFormatter
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RulesKeywordTest {

    @Test
    fun zdlRules_withAndWithoutAggregate_fillRulesAndPolicies() {
        val model = ZdlParser().parseModel(
            """
            rules (Order) { maxItems 10 }
            rules { audit "Every change is audited" }
            rules (Customer) { maxItems 3 }
            """.trimIndent()
        )

        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())
        assertEquals(model["policies"], model["rules"])
        val rules = model["rules"] as Map<*, *>
        assertEquals(setOf("Order.maxItems", "Customer.maxItems", "audit"), rules.keys)
        assertEquals(mapOf("name" to "maxItems", "value" to 10L, "aggregate" to "Order", "options" to emptyMap<String, Any?>(), "javadoc" to null), rules["Order.maxItems"])
        assertEquals(mapOf("name" to "maxItems", "value" to 3L, "aggregate" to "Customer", "options" to emptyMap<String, Any?>(), "javadoc" to null), rules["Customer.maxItems"])
        assertEquals(mapOf("name" to "audit", "value" to "Every change is audited", "aggregate" to null, "options" to emptyMap<String, Any?>(), "javadoc" to null), rules["audit"])
    }

    @Test
    fun zdlPolicies_stillParseAndFillBothKeys_andWarnOnTheKeyword() {
        val model = ZdlParser().parseModel(readTestFile("policies.zdl"))
        assertEquals(model["policies"], model["rules"])
        val policies = model["policies"] as Map<*, *>
        assertTrue(policies.keys.containsAll(listOf("somename_2", "some-3", "some-4")))

        val warnings = model.getProblems().filter { it["severity"] == "WARNING" }
        assertEquals(1, warnings.size)
        assertEquals("'policies' is deprecated, use 'rules'.", warnings.single()["message"])
        val location = warnings.single()["location"] as IntArray
        val text = readTestFile("policies.zdl")
        assertEquals("policies", text.substring(location[0], location[1]))
    }

    @Test
    fun zdlRulesFile_fillsBothKeys_includingHyphenatedNamesAndMultilineValues() {
        val model = ZdlParser().parseModel(readTestFile("rules.zdl"))
        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())
        assertEquals(model["policies"], model["rules"])
        val rules = model["rules"] as Map<*, *>
        assertEquals("this is the body of the rule 2", (rules["somename_2"] as Map<*, *>)["value"])
        assertEquals("this is the body of the rule 3", (rules["some-3"] as Map<*, *>)["value"])
        assertEquals("this is the body of the rule 4", (rules["some-4"] as Map<*, *>)["value"])
        assertNull((rules["some-4"] as Map<*, *>)["aggregate"])
    }

    @Test
    fun zdlPoliciesDeprecation_usesTheKeywordSourceRange_andRulesDoesNotWarn() {
        val text = "entity Order { id String }\npolicies { a \"1\" }\nrules { b \"2\" }\npolicies (Order) { c \"3\" }\n"
        val model = ZdlParser().parseModel(text)
        val warnings = model.getProblems()
        assertEquals(2, warnings.size, warnings.toString())
        assertTrue(warnings.all { it["message"] == "'policies' is deprecated, use 'rules'." && it["severity"] == "WARNING" })
        warnings.forEach { warning ->
            val location = warning["location"] as IntArray
            assertEquals("policies", text.substring(location[0], location[1]))
            assertEquals(location[2], location[4])
            assertEquals(location[3] + "policies".length, location[5])
        }
        assertEquals(setOf("a", "b", "Order.c"), (model["rules"] as Map<*, *>).keys)
        assertEquals(model["policies"], model["rules"])
    }

    @Test
    fun entityAndFieldNamedRulesStillParse() {
        val model = ZdlParser().parseModel(
            """
            entity rules {
                rules String
                policies String
            }
            entity policies {
                id String
            }
            """.trimIndent()
        )
        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())
        assertEquals("rules", JSONPath.get(model, "$.entities.rules.name"))
        assertEquals("String", JSONPath.get(model, "$.entities.rules.fields.rules.type"))
        assertEquals("String", JSONPath.get(model, "$.entities.rules.fields.policies.type"))
        assertEquals("policies", JSONPath.get(model, "$.entities.policies.name"))
    }

    @Test
    fun zflFlow_collectsRulesBlocksInOrder_onTheMapAndOnZflFlow() {
        val text = """
            flow PlaceOrder {
                rules (Order) {
                    minimumAmount "An order total must be at least 10 EUR"
                    stock-first "Stock is reserved before payment is taken"
                    maxItems 10
                }
                start PlaceOrderRequested {
                    rules String
                }
                when PlaceOrderRequested do placeOrder
                rules {
                    audit "Every change is audited"
                }
            }
        """.trimIndent()
        val parsed = ZflParser().parse(text)
        assertTrue(parsed.syntaxProblems.isEmpty(), parsed.syntaxProblems.toString())
        val model = ZflParser().parseModel(text)
        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())

        val rules = JSONPath.get<List<*>>(model, "$.flows.PlaceOrder.rules").orEmpty()
        assertEquals(4, rules.size)
        assertEquals(mapOf("name" to "minimumAmount", "value" to "An order total must be at least 10 EUR", "aggregate" to "Order", "options" to emptyMap<String, Any?>(), "javadoc" to null), rules[0])
        assertEquals(mapOf("name" to "stock-first", "value" to "Stock is reserved before payment is taken", "aggregate" to "Order", "options" to emptyMap<String, Any?>(), "javadoc" to null), rules[1])
        assertEquals(mapOf("name" to "maxItems", "value" to 10L, "aggregate" to "Order", "options" to emptyMap<String, Any?>(), "javadoc" to null), rules[2])
        assertEquals(mapOf("name" to "audit", "value" to "Every change is audited", "aggregate" to null, "options" to emptyMap<String, Any?>(), "javadoc" to null), rules[3])
        assertEquals("String", JSONPath.get(model, "$.flows.PlaceOrder.starts.PlaceOrderRequested.fields.rules.type"))

        val flow = ZflSemanticAnalyzer().analyze(model).flows.single()
        assertEquals(1, flow.policies.size)
        assertEquals("placeOrder", flow.policies.single().command)
        assertEquals(listOf("minimumAmount", "stock-first", "maxItems", "audit"), flow.rules.map { it.name })
        assertEquals("An order total must be at least 10 EUR", flow.rules[0].value)
        assertEquals("Order", flow.rules[0].aggregate)
        assertEquals("Stock is reserved before payment is taken", flow.rules[1].value)
        assertEquals("10", flow.rules[2].value)
        assertNull(flow.rules[3].aggregate)
        assertEquals("Every change is audited", flow.rules[3].value)
        val minimumAt = text.indexOf("minimumAmount")
        assertEquals(text.substring(0, minimumAt).count { it == '\n' } + 1, flow.rules[0].sourceRef.line)
        val lineStart = text.lastIndexOf('\n', minimumAt - 1).let { if (it < 0) 0 else it + 1 }
        assertEquals(minimumAt - lineStart + 1, flow.rules[0].sourceRef.column)
        assertEquals("<zfl>", flow.rules[0].sourceRef.file)
    }

    @Test
    fun zflRules_acceptMultilineValues() {
        val text = """
            flow PlaceOrder {
                rules {
                    story "
                        first
                          second
                    "
                }
                start S {}
                when S do go
            }
        """.trimIndent()
        val model = ZflParser().parseModel(text)
        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())
        assertEquals("first\n  second", (JSONPath.get<String>(model, "$.flows.PlaceOrder.rules[0].value") ?: "").trimIndent().trim())
        val flow = ZflSemanticAnalyzer().analyze(model).flows.single()
        assertEquals("first\n  second", flow.rules.single().value.trimIndent().trim())
        assertNull(flow.rules.single().aggregate)
    }

    @Test
    fun formatters_roundTripRulesLikePolicies_andDoNotRewriteTheKeyword() {
        val body = """
            (Order) {
            minimumAmount    "An order total must be at least 10 EUR"

            stock-first "Stock is reserved before payment is taken"
            }
        """.trimIndent()
        val policies = "policies $body\n"
        val rules = "rules $body\n"
        val formattedPolicies = ZdlFormatter().format(policies)
        val formattedRules = ZdlFormatter().format(rules)
        assertTrue(formattedPolicies.startsWith("policies"))
        assertTrue(formattedRules.startsWith("rules"))
        assertEquals(formattedPolicies.replaceFirst("policies", "rules"), formattedRules)
        assertEquals(formattedPolicies, ZdlFormatter().format(formattedPolicies))
        assertEquals(formattedRules, ZdlFormatter().format(formattedRules))
        val formattedModel = ZdlParser().parseModel(formattedRules)
        assertEquals(formattedModel["policies"], formattedModel["rules"])
        assertTrue(formattedModel.getProblems().isEmpty(), formattedModel.getProblems().toString())

        val zfl = """
            flow PlaceOrder {
            rules (Order) {
            minimumAmount    "An order total must be at least 10 EUR"
            stock-first "Stock is reserved before payment is taken"
            }
            start S {}
            rules {
            audit "Every change is audited"
            }
            when S do placeOrder
            }
        """.trimIndent()
        val formattedZfl = ZflFormatter().format(zfl)
        val expectedZfl = """
            flow PlaceOrder {
                rules(Order) {
                    minimumAmount "An order total must be at least 10 EUR"
                    stock-first "Stock is reserved before payment is taken"
                }
                start S {}
                rules {
                    audit "Every change is audited"
                }
                when S do placeOrder
            }
        """.trimIndent() + "\n"
        assertEquals(expectedZfl, formattedZfl)
        assertEquals(formattedZfl, ZflFormatter().format(formattedZfl))
        val zflModel = ZflParser().parseModel(formattedZfl)
        assertTrue(zflModel.getProblems().isEmpty(), zflModel.getProblems().toString())
        val formattedRulesList = JSONPath.get<List<*>>(zflModel, "$.flows.PlaceOrder.rules").orEmpty()
        assertEquals(3, formattedRulesList.size)
    }
}

/** ZFL-R11: annotations and javadoc on a rule, in ZDL `rules` and ZFL flow `rules` blocks. */
private const val Q = "\"\"\""

class RuleAnnotationsTest {

    private val zdl = """
        rules (SlotOccupancy) {
            /** Seats are never oversold. */
            @invariant  noBookingWithoutSeats "No se reserva si no quedan plazas."
            @pre        deadlineForCitizens   ${Q}El ciudadano cancela
                hasta el plazo.${Q}
            @rule(severity: high) plain "x"
            @temporal @audit some-rule "with dash"
            noAnnotation "still valid"
        }
    """.trimIndent()

    @Test
    fun zdlRules_keepAnnotationsAndJavadoc_andOthersStayPlain() {
        val model = ZdlParser().parseModel(zdl)
        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())
        assertEquals(model["policies"], model["rules"])
        val rules = model["rules"] as Map<*, *>
        assertEquals(5, rules.size)

        val first = rules["SlotOccupancy.noBookingWithoutSeats"] as Map<*, *>
        assertEquals("No se reserva si no quedan plazas.", first["value"])
        assertEquals("SlotOccupancy", first["aggregate"])
        assertEquals("Seats are never oversold.", first["javadoc"])
        assertEquals(mapOf("invariant" to true), first["options"])

        val pre = rules["SlotOccupancy.deadlineForCitizens"] as Map<*, *>
        assertEquals(mapOf("pre" to true), pre["options"])
        assertNull(pre["javadoc"])
        assertTrue((pre["value"] as String).contains("hasta el plazo"))

        assertEquals(mapOf("rule" to mapOf("severity" to "high")), (rules["SlotOccupancy.plain"] as Map<*, *>)["options"])
        val dashed = rules["SlotOccupancy.some-rule"] as Map<*, *>
        assertEquals(setOf("temporal", "audit"), (dashed["options"] as Map<*, *>).keys)
        assertEquals("with dash", dashed["value"])
        assertEquals(emptyMap<String, Any?>(), (rules["SlotOccupancy.noAnnotation"] as Map<*, *>)["options"])
    }

    @Test
    fun zdlRuleAnnotations_doNotLeakToOtherElements() {
        val model = ZdlParser().parseModel(
            """
            @aggregate
            entity Order { id String }
            rules { @invariant a "x" }
            entity Other { id String }
            """.trimIndent()
        )
        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())
        assertEquals(mapOf("aggregate" to true), JSONPath.get<Map<*, *>>(model, "$.entities.Order.options"))
        assertEquals(emptyMap<String, Any?>(), JSONPath.get<Map<*, *>>(model, "$.entities.Other.options"))
        assertNull(JSONPath.get<Any?>(model, "$.entities.Other.options.invariant"))
    }

    private val zfl = """
        flow PlaceOrder {
            rules (Order) {
                /** Never oversell. */
                @invariant noOversell "Stock first"
                @pre @rule(severity: high) stock-first "Multi
                    line"
                plain "x"
            }
            @title("Place")
            start PlaceOrderRequested {}
            when PlaceOrderRequested do placeOrder
        }
    """.trimIndent()

    @Test
    fun zflRules_keepAnnotationsAndJavadoc_inModelAndZflRule() {
        val parsed = ZflParser().parse(zfl)
        assertTrue(parsed.syntaxProblems.isEmpty(), parsed.syntaxProblems.toString())
        val model = ZflParser().parseModel(zfl)
        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())
        val rules = JSONPath.get<List<Map<String, Any?>>>(model, "$.flows.PlaceOrder.rules").orEmpty()
        assertEquals(mapOf("invariant" to true), rules[0]["options"])
        assertEquals("Never oversell.", rules[0]["javadoc"])
        assertEquals(setOf("pre", "rule"), (rules[1]["options"] as Map<*, *>).keys)
        assertEquals(emptyMap<String, Any?>(), rules[2]["options"])
        assertNull(rules[2]["javadoc"])
        // annotations of a rule do not become options of the flow
        assertNull(JSONPath.get<Any?>(model, "$.flows.PlaceOrder.options.invariant"))

        val flow = ZflSemanticAnalyzer().analyze(model).flows.single()
        assertEquals(listOf("noOversell", "stock-first", "plain"), flow.rules.map { it.name })
        assertEquals(mapOf("invariant" to null), flow.rules[0].options)
        assertEquals("Never oversell.", flow.rules[0].javadoc)
        assertEquals(mapOf("pre" to null, "rule" to "severity: high"), flow.rules[1].options)
        assertTrue(flow.rules[1].value.contains("line"))
        assertTrue(flow.rules[2].options.isEmpty())
        assertNull(flow.rules[2].javadoc)
    }
}
