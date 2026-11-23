package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.zfl.ZflParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A view, timer or end state still being typed is a problem on it, never a lost flow. */
class ZflIncompleteExperimentalTest {

    private val next = "flow G { start S {} when S do run { service A.B emits E } end { done: E } }"

    private fun assertOtherFlowSurvives(incomplete: String) {
        val model = ZflParser().parseModel("$incomplete\n$next")
        assertTrue(model.getProblems().isNotEmpty(), "the incomplete block is a problem")
        assertTrue(model.getProblems().none { it["path"] == "parser" }, model.getProblems().toString())
        val flows = model["flows"] as Map<*, *>
        assertEquals(setOf("F", "G"), flows.keys)
        assertEquals(1, ((flows["G"] as Map<*, *>)["whens"] as List<*>).size)
        // the analyzer takes what the walk kept
        assertEquals(listOf("F", "G"), ZflSemanticAnalyzer().analyze(model).flows.map { it.name })
    }

    @Test
    fun aTimerWithoutItsCommand() = assertOtherFlowSurvives("flow F { timer after E \"1h\" }")

    @Test
    fun aTimerWithoutAnything() = assertOtherFlowSurvives("flow F { timer }")

    @Test
    fun aTimerAtWithoutAPath() = assertOtherFlowSurvives("flow F { timer at Order do x }")

    @Test
    fun aViewWithoutItsKey() = assertOtherFlowSurvives("flow F { view V by }")

    @Test
    fun aViewWithoutItsBody() = assertOtherFlowSurvives("flow F { view V by Order.id }")

    @Test
    fun aViewWithoutAName() = assertOtherFlowSurvives("flow F { view }")

    @Test
    fun anEndStateWithoutItsValue() = assertOtherFlowSurvives("flow F { end { done: E { Order: } } }")

    @Test
    fun anEndStateWithoutItsAggregate() = assertOtherFlowSurvives("flow F { end { done: E { : X } } }")
}
