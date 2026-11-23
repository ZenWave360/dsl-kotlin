package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A `timer` and a `when` may name the same event and command; each keeps its own node, and so do several timers. */
class ZflTimerPolicyIdsTest {

    private val zfl = """
        flow F {
            start S {}

            when S do ship {
                service Shop.ShopService
                emits Shipped
            }

            when Shipped do remind {
                service Shop.ShopService
                emits Reminded
            }

            timer after Shipped "1h" do remind
            timer after Shipped "2d" unless Reminded do remind

            end {
                completed: Reminded
            }
        }
    """

    private val flow get() = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zfl)).flows.single()

    @Test
    fun eachTimerAndTheWhenHaveTheirOwnPolicyNode() {
        val graph = ZflToFlowGraphTransformer().transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zfl)))
        val ids = graph.nodes.map { it.id }.filter { it.startsWith("policy:Shipped:remind") }
        assertEquals(3, ids.distinct().size, ids.toString())
        assertTrue("policy:Shipped:remind" in ids, ids.toString())
        val timers = graph.nodes.filter { it.id in ids && it.timer != null }
        assertEquals(listOf("1h after Shipped", "2d after Shipped, unless Reminded"), timers.map { it.timer })
    }

    @Test
    fun theSequenceFindsThePolicyOfItsOwnOccurrence() {
        val messages = ZflSequencePaths(flow).paths().flatMap { it.messages }
        assertNotNull(messages.firstOrNull { it.note && "1h" in it.label }, messages.toString())
        val refs = messages.flatMap { it.refs }
        assertTrue("policy:Shipped:remind:timer:1h after Shipped" in refs, refs.toString())
        assertTrue("policy:Shipped:remind" in refs, refs.toString())
    }
}
