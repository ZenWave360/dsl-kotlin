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
        assertEquals(mapOf("name" to "maxItems", "value" to 10L, "aggregate" to "Order"), rules["Order.maxItems"])
        assertEquals(mapOf("name" to "maxItems", "value" to 3L, "aggregate" to "Customer"), rules["Customer.maxItems"])
        assertEquals(mapOf("name" to "audit", "value" to "Every change is audited", "aggregate" to null), rules["audit"])
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
        assertEquals(mapOf("name" to "minimumAmount", "value" to "An order total must be at least 10 EUR", "aggregate" to "Order"), rules[0])
        assertEquals(mapOf("name" to "stock-first", "value" to "Stock is reserved before payment is taken", "aggregate" to "Order"), rules[1])
        assertEquals(mapOf("name" to "maxItems", "value" to 10L, "aggregate" to "Order"), rules[2])
        assertEquals(mapOf("name" to "audit", "value" to "Every change is audited", "aggregate" to null), rules[3])
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
