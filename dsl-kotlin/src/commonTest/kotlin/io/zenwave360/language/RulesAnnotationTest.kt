package io.zenwave360.language

import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.Severity
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** ZFL-R12: `@rules(...)` on ZFL `when … do` / `do` and on ZDL service methods. */
class RulesAnnotationTest {

    private fun zfl(body: String) = """
        flow PlaceOrder {
            rules {
                minimumAmount "At least 10 EUR"
                stock-first "Stock first"
                audit "Audited"
            }
            start PlaceOrderRequested {}
            $body
        }
    """.trimIndent()

    private fun analyze(text: String) = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(text))

    @Test
    fun zflWhenDo_knownNames_quotedHyphen_noWarnings() {
        val semantic = analyze(zfl("""@rules(minimumAmount, "stock-first")
            when PlaceOrderRequested do placeOrder"""))
        assertTrue(semantic.diagnostics.none { "rule" in it.message }, semantic.diagnostics.toString())
        val flow = semantic.flows.single()
        assertEquals(listOf("minimumAmount", "stock-first"), flow.policies.single().rules)
        assertEquals(listOf("minimumAmount", "stock-first"), flow.commands.single().rules)
        assertEquals(listOf("minimumAmount", "stock-first"), flow.commands.single().occurrences.single().rules)
    }

    @Test
    fun zflDo_singleName_andUnknownNameWarnsOnTheName() {
        val text = zfl("""@rules(audit, nope)
            do placeOrder {
            }""")
        val semantic = analyze(text)
        val flow = semantic.flows.single()
        assertEquals(listOf("audit", "nope"), flow.commands.single().rules)
        val warnings = semantic.diagnostics.filter { "rule" in it.message }
        assertEquals(1, warnings.size)
        val warning = warnings.single()
        assertEquals(Severity.WARNING, warning.severity)
        assertEquals("Flow 'PlaceOrder' has no rule 'nope'.", warning.message)
        val at = text.indexOf("nope")
        assertEquals(text.substring(0, at).count { it == '\n' } + 1, warning.sourceRef!!.line)
        assertEquals(at - (text.lastIndexOf('\n', at) + 1) + 1, warning.sourceRef!!.column)
    }

    @Test
    fun zflUnknownQuotedName_warns() {
        val semantic = analyze(zfl("""@rules("no-such")
            when PlaceOrderRequested do placeOrder"""))
        assertEquals(1, semantic.diagnostics.count { it.severity == Severity.WARNING && "no-such" in it.message })
    }

    @Test
    fun zflWithoutRulesAnnotation_hasNoRulesAndNoWarning() {
        val semantic = analyze(zfl("when PlaceOrderRequested do placeOrder"))
        assertTrue(semantic.flows.single().commands.single().rules.isEmpty())
        assertTrue(semantic.diagnostics.none { "rule" in it.message })
    }

    private val zdlRules = """
        rules (Order) {
            minimumAmount "At least 10 EUR"
            stock-first "Stock first"
        }
        entity Order { name String }
        aggregate OrderAggregate(Order) {
        }
        service OrderService for (OrderAggregate) {
            %s
        }
    """.trimIndent()

    private fun zdl(method: String) = ZdlParser().parseModel(zdlRules.replace("%s", method))

    @Suppress("UNCHECKED_CAST")
    private fun methodRules(model: Map<String, Any?>): List<String> =
        ((((model["services"] as Map<String, Any?>)["OrderService"] as Map<String, Any?>)["methods"]
            as Map<String, Any?>)["placeOrder"] as Map<String, Any?>)["rules"] as List<String>

    @Test
    fun zdlMethod_knownNames_severalAndHyphenQuoted() {
        val model = zdl("""@rules(minimumAmount, "stock-first")
            placeOrder(Order) Order""")
        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())
        assertEquals(listOf("minimumAmount", "stock-first"), methodRules(model))
    }

    @Test
    fun zdlMethod_unknownNameWarnsOnTheName() {
        val text = zdlRules.replace("%s", """@rules(minimumAmount, nope)
            placeOrder(Order) Order""")
        val model = ZdlParser().parseModel(text)
        val warnings = model.getProblems().filter { it["severity"] == "WARNING" }
        assertEquals(1, warnings.size, model.getProblems().toString())
        assertEquals("No rule 'nope' is declared.", warnings.single()["message"])
        val loc = warnings.single()["location"] as IntArray
        assertEquals("nope", text.substring(loc[0], loc[1]))
    }

    @Test
    fun zdlMethod_policyAlias_resolvesWarnsAndKeepsPolicyOption() {
        val text = zdlRules.replace("%s", """@policy(minimumAmount)
            placeOrder(Order) Order""")
        val model = ZdlParser().parseModel(text)
        assertEquals(listOf("minimumAmount"), methodRules(model))
        val warnings = model.getProblems().filter { it["severity"] == "WARNING" }
        assertEquals(1, warnings.size, model.getProblems().toString())
        assertEquals("'@policy' is deprecated, use '@rules'.", warnings.single()["message"])
        val loc = warnings.single()["location"] as IntArray
        assertEquals("@policy(minimumAmount)", text.substring(loc[0], loc[1]))
        @Suppress("UNCHECKED_CAST")
        val options = ((((model["services"] as Map<String, Any?>)["OrderService"] as Map<String, Any?>)["methods"]
            as Map<String, Any?>)["placeOrder"] as Map<String, Any?>)["options"] as Map<String, Any?>
        assertEquals("minimumAmount", options["policy"])
    }

    @Test
    fun zdlMethod_policyAlias_unknownNameAlsoWarns() {
        val model = zdl("""@policy(ghost)
            placeOrder(Order) Order""")
        assertEquals(2, model.getProblems().count { it["severity"] == "WARNING" }, model.getProblems().toString())
    }

    @Test
    fun zdlMethod_withoutAnnotation_hasEmptyRules() {
        val model = zdl("placeOrder(Order) Order")
        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())
        assertTrue(methodRules(model).isEmpty())
    }

    @Test
    fun zdlMethod_rulesDeclaredAsPoliciesKeyword_count() {
        val model = ZdlParser().parseModel(zdlRules.replace("rules (Order)", "policies (Order)").replace("%s", """@rules(minimumAmount)
            placeOrder(Order) Order"""))
        val warnings = model.getProblems().filter { it["severity"] == "WARNING" }
        assertEquals(listOf("'policies' is deprecated, use 'rules'."), warnings.map { it["message"] })
    }
}
