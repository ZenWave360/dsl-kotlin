package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ZFL-R12: each command's rules are a `%%` comment before its message in the sequence diagrams. */
class RulesCommentDiagramsTest {

    private val flow = """
        flow PlaceOrder {
            rules {
                minimumAmount "At least 10 EUR"
                stock-first "Stock first"
            }
            start PlaceOrderRequested {}
            @rules(minimumAmount, "stock-first")
            when PlaceOrderRequested do placeOrder {
                service Orders.OrderService
                emits OrderPlaced
            }
            when OrderPlaced do notifyCustomer {
                service Notifications.NotificationService
                emits CustomerNotified
            }
            end { completed: CustomerNotified }
        }
    """.trimIndent()

    private fun diagrams(mode: MermaidSequenceRenderMode) =
        ZflToMermaidDiagramsTransformer().transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(flow)), mode)

    @Test
    fun theRulesOfACommandAreACommentBeforeItsMessage() {
        val lines = diagrams(MermaidSequenceRenderMode.SEPARATE_VARIANTS).sequences.single().mermaid.lines().map { it.trim() }

        assertEquals("%% PlaceOrder: from PlaceOrderRequested to completed", lines[1])
        val comment = lines.indexOf("%% rules: minimumAmount, stock-first")
        assertTrue(comment > 1, lines.toString())
        assertEquals("PlaceOrderRequested->>OrderService: placeOrder", lines[comment + 1])
        assertEquals(1, lines.count { "%% rules" in it }, lines.toString())
    }

    @Test
    fun aCommandWithoutRulesGetsNoComment() {
        val mermaid = diagrams(MermaidSequenceRenderMode.SEPARATE_VARIANTS).sequences.single().mermaid
        assertFalse(mermaid.lines().any { it.contains("notifyCustomer") && it.contains("%%") }, mermaid)
        assertEquals(2, mermaid.lines().count { "%%" in it }, mermaid)
    }

    @Test
    fun theAltBlocksDiagramCarriesTheCommentToo() {
        val mermaid = diagrams(MermaidSequenceRenderMode.ALT_BLOCKS).sequences.single().mermaid
        assertTrue("    %% rules: minimumAmount, stock-first\n" in mermaid, mermaid)
    }

    @Test
    fun aMultiLineRuleNameStaysOnOneCommentLine() {
        val mermaid = ZflToMermaidDiagramsTransformer().transform(
            ZflSemanticAnalyzer().analyze(ZflParser().parseModel(flow.replace("@rules(minimumAmount, \"stock-first\")", "@rules(minimumAmount)"))),
            MermaidSequenceRenderMode.SEPARATE_VARIANTS,
        ).sequences.single().mermaid
        assertTrue("    %% rules: minimumAmount\n" in mermaid, mermaid)
        assertTrue(mermaid.lines().filter { "%%" in it }.all { it.trim().startsWith("%%") }, mermaid)
    }
}
