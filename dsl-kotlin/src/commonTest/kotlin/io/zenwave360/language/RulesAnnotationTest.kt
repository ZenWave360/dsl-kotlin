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

    @Suppress("UNCHECKED_CAST")
    private fun method(model: Map<String, Any?>) =
        (((model["services"] as Map<String, Any?>)["OrderService"] as Map<String, Any?>)["methods"]
            as Map<String, Any?>)["placeOrder"] as Map<String, Any?>

    @Test
    fun zdlMethod_rulesMirroredIntoPolicyOption_likePolicy() {
        val viaRules = method(zdl("""@rules(minimumAmount, "stock-first")
            placeOrder(Order) Order"""))
        val viaPolicy = method(zdl("""@policy(minimumAmount, "stock-first")
            placeOrder(Order) Order"""))
        @Suppress("UNCHECKED_CAST")
        fun policy(m: Map<String, Any?>) = (m["options"] as Map<String, Any?>)["policy"]
        @Suppress("UNCHECKED_CAST")
        fun listed(m: Map<String, Any?>) = (m["optionsList"] as List<Map<String, Any?>>).filter { it["name"] == "policy" }
        assertEquals(listOf("minimumAmount", "stock-first"), policy(viaRules))
        assertEquals(policy(viaPolicy), policy(viaRules))
        assertEquals(listed(viaPolicy), listed(viaRules))
        val single = method(zdl("""@rules(minimumAmount)
            placeOrder(Order) Order"""))
        assertEquals("minimumAmount", policy(single))
    }

    @Test
    fun zdlMethod_rulesAndPolicyTogether_mergeWithoutDuplicates() {
        val m = method(zdl("""@policy(minimumAmount)
            @rules(minimumAmount, "stock-first")
            placeOrder(Order) Order"""))
        @Suppress("UNCHECKED_CAST")
        val options = m["options"] as Map<String, Any?>
        assertEquals(listOf("minimumAmount", "stock-first"), options["policy"])
        assertEquals(listOf("minimumAmount", "stock-first"), m["rules"])
        @Suppress("UNCHECKED_CAST")
        assertEquals(1, (m["optionsList"] as List<Map<String, Any?>>).count { it["name"] == "policy" })
    }

    @Test
    fun zdlPolicyDeprecation_rangeCoversTheWholeAnnotation_inOffsets() {
        val text = zdlRules.replace("%s", """@policy(minimumAmount, "stock-first")
            placeOrder(Order) Order""")
        val warning = ZdlParser().parseModel(text).getProblems().single { it["message"].toString().startsWith("'@policy'") }
        val loc = warning["location"] as IntArray
        assertEquals("""@policy(minimumAmount, "stock-first")""", text.substring(loc[0], loc[1]))
    }

    private fun zflAggregate(refs: String) = """
        flow PlaceOrder {
            rules (Order) {
                limit "At most 100 EUR"
            }
            rules (Payment) {
                limit "Payment limit"
            }
            start PlaceOrderRequested {}
            @rules($refs)
            when PlaceOrderRequested do placeOrder
        }
    """.trimIndent()

    @Test
    fun zflQualifiedNameResolvesToTheAggregateRule_quotedAndUnquoted_andBareStillMatches() {
        for (refs in listOf("Order.limit", "\"Order.limit\"", "Payment.limit", "limit", "Order.limit, limit")) {
            val semantic = analyze(zflAggregate(refs))
            assertTrue(semantic.diagnostics.none { "rule" in it.message }, "$refs: ${semantic.diagnostics}")
        }
        assertEquals(
            listOf("Order.limit"),
            analyze(zflAggregate("Order.limit")).flows.single().commands.single().rules,
        )
    }

    @Test
    fun zflUnknownQualifiedNameWarnsOnTheWholeName() {
        val text = zflAggregate("Order.nope, Shipping.limit")
        val warnings = analyze(text).diagnostics.filter { "rule" in it.message }
        assertEquals(listOf("Flow 'PlaceOrder' has no rule 'Order.nope'.", "Flow 'PlaceOrder' has no rule 'Shipping.limit'."), warnings.map { it.message })
        val at = text.indexOf("Order.nope")
        assertEquals(at - (text.lastIndexOf('\n', at) + 1) + 1, warnings.first().sourceRef!!.column)
    }

    @Test
    fun zflUnquotedQualifiedName_sameModelAsQuoted() {
        val quoted = analyze(zfl("""@rules("Order.limit", audit)
            when PlaceOrderRequested do placeOrder""")).flows.single().commands.single()
        val unquoted = analyze(zfl("""@rules(Order.limit, audit)
            when PlaceOrderRequested do placeOrder""")).flows.single().commands.single()
        assertEquals(listOf("Order.limit", "audit"), unquoted.rules)
        assertEquals(quoted.rules, unquoted.rules)
    }

    @Test
    fun zdlQualifiedNameResolvesToTheAggregateRule() {
        val text = zdlRules.replace("%s", """@rules(Order.minimumAmount, "Order.stock-first")
            placeOrder(Order) Order""")
        val model = ZdlParser().parseModel(text)
        assertTrue(model.getProblems().isEmpty(), model.getProblems().toString())
    }

    @Test
    fun zflUnquotedQualifiedName_inDoBlock() {
        val semantic = analyze(zfl("""@rules(Order.limit)
            do placeOrder {
            }"""))
        assertEquals(listOf("Order.limit"), semantic.flows.single().commands.single().rules)
    }

    @Test
    fun zdlUnquotedQualifiedName_sameAsQuoted() {
        val unquoted = zdl("""@rules(Order.limit, minimumAmount)
            placeOrder(Order) Order""")
        val quoted = zdl("""@rules("Order.limit", minimumAmount)
            placeOrder(Order) Order""")
        assertEquals(listOf("Order.limit", "minimumAmount"), methodRules(unquoted))
        assertEquals(methodRules(quoted), methodRules(unquoted))
    }

    @Suppress("UNCHECKED_CAST")
    private fun zdlOption(text: String): Any? =
        (((ZflParser().parseModel(text)["systems"] as Map<String, Any?>)["Orders"] as Map<String, Any?>)["options"]
            as Map<String, Any?>)["zdl"]

    @Test
    fun zflSystemZdl_listKeepsOrder_singleStaysAString() {
        assertEquals(
            listOf("../orders/orders.zdl", "https://example.com/orders/orders.zdl"),
            zdlOption("""systems {
                @zdl("../orders/orders.zdl", "https://example.com/orders/orders.zdl")
                Orders { service OrderService } }"""))
        assertEquals("../orders/orders.zdl", zdlOption("""systems {
                @zdl("../orders/orders.zdl")
                Orders { service OrderService } }"""))
    }
}
