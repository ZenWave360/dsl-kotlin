package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.eventflow.view.ZflToFlowViewModelTransformer
import io.zenwave360.language.zfl.ZflParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZflPolicyAnnotationsTest {

    private fun flow(annotations: String) = """
        flow GuestCallFlow {
            start GuestCallRequired {}
            when GuestCallRequired do recordPendingGuestCall {
                service Bookings.SlotBlockService
                emits PendingGuestCallRecorded
            }
            $annotations
            when PendingGuestCallRecorded do recordGuestCall {
                service Bookings.SlotBlockService
                emits GuestCallCompleted
            }
            end { completed: GuestCallCompleted }
        }
    """.trimIndent()

    private fun analyze(annotations: String) = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(flow(annotations)))

    @Test
    fun testAnalyze_PolicyReadsTitleActorAndTimer() {
        val actor = analyze("@actor(FacilityStaff)\n@title(\"Staff call the guest\")").flows.single().policies.last()
        assertEquals("FacilityStaff", actor.actor)
        assertEquals("Staff call the guest", actor.title)
        assertNull(actor.timer)

        val timer = analyze("@timer(\"after 2 days\")").flows.single().policies.last()
        assertEquals("after 2 days", timer.timer)
        val time = analyze("@time(\"nightly\")").flows.single().policies.last()
        assertEquals("nightly", time.timer)

        val plain = analyze("").flows.single().policies.last()
        assertNull(plain.title)
        assertNull(plain.actor)
        assertNull(plain.timer)
    }

    @Test
    fun testAnalyze_PolicyWithActorAndTimerAddsDiagnostic() {
        val model = analyze("@actor(FacilityStaff)\n@timer(\"nightly\")")
        assertTrue(
            model.diagnostics.any { it.severity == Severity.ERROR && it.message.contains("either '@actor' or '@timer'") },
            model.diagnostics.toString()
        )
    }

    @Test
    fun testAnalyze_MapValuedPolicyAnnotationAddsDiagnostic() {
        val model = analyze("@actor(k: v)")
        assertTrue(model.diagnostics.any { it.message.contains("'@actor' expects a single value") }, model.diagnostics.toString())
    }

    @Test
    fun testViewModel_PolicyNodeCarriesTitleKindAndActorOrTimer() {
        val id = "policy:PendingGuestCallRecorded:recordGuestCall"
        val actorNode = ZflToFlowViewModelTransformer()
            .transform(analyze("@actor(FacilityStaff)\n@title(\"Staff call the guest\")"))
            .nodes.single { it.id == id }
        assertEquals("actor", actorNode.policyKind)
        assertEquals("FacilityStaff", actorNode.actor)
        assertEquals("Staff call the guest", actorNode.title)
        assertNull(actorNode.timer)

        val timerModel = ZflToFlowViewModelTransformer().transform(analyze("@timer(\"after 2 days\")"))
        val timerNode = timerModel.nodes.single { it.id == id }
        assertEquals("timer", timerNode.policyKind)
        assertEquals("after 2 days", timerNode.timer)
        assertNull(timerNode.title)
        assertTrue(timerModel.toJson().contains("\"policyKind\":\"timer\""))
        assertTrue(timerModel.toJson().contains("\"timer\":\"after 2 days\""))

        val plain = ZflToFlowViewModelTransformer().transform(analyze("")).nodes.single { it.id == id }
        assertNull(plain.policyKind)
        assertNull(plain.title)
    }
}
