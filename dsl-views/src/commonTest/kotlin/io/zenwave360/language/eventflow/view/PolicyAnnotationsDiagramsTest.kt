package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolicyAnnotationsDiagramsTest {

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

    private fun semantic(annotations: String) = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(flow(annotations)))

    private fun diagrams(annotations: String) = ZflToMermaidDiagramsTransformer().transform(semantic(annotations))

    @Test
    fun testSequence_ActorPolicyHasActorCallTheServiceAndTheAnswerComeBack() {
        val sequence = diagrams("@actor(FacilityStaff)").sequences.single().mermaid
        assertEquals(
            listOf(
                "FacilityStaff->>SlotBlockService: recordGuestCall",
                "SlotBlockService-->>FacilityStaff: GuestCallCompleted",
            ),
            sequence.lines().map { it.trim() }.filter { it.contains("FacilityStaff") && it.contains("SlotBlockService") },
            sequence
        )
        assertTrue(sequence.contains("    actor FacilityStaff"), sequence)
        // the actor replaces the event delivery
        assertTrue(!sequence.contains("-->>SlotBlockService: PendingGuestCallRecorded"), sequence)
    }

    @Test
    fun testSequence_TimerPolicyAddsNoteBeforeTheCommand() {
        val lines = diagrams("@timer(\"after 2 days\")").sequences.single().mermaid.lines().map { it.trim() }
        val note = lines.indexOf("Note over SlotBlockService: ⏳ after 2 days")
        assertTrue(note > 0, lines.toString())
        assertEquals("SlotBlockService->>SlotBlockService: recordGuestCall", lines[note + 1])
    }

    @Test
    fun testSequence_TitleReplacesTheCommandText() {
        val sequence = diagrams("@title(\"Block the slot\")").sequences.single().mermaid
        assertTrue(sequence.contains("SlotBlockService->>SlotBlockService: Block the slot"), sequence)
        assertTrue(!sequence.contains(": recordGuestCall"), sequence)
    }

    @Test
    fun testFlowchart_PolicyShowsTitleAndActorOrTimerFirst() {
        val actor = diagrams("@actor(FacilityStaff)\n@title(\"Staff call the guest\")").flowchart
        assertTrue(actor.contains("{{\"👤 FacilityStaff<br/>Staff call the guest\"}}"), actor)
        val timer = diagrams("@timer(\"after 2 days\")").flowchart
        assertTrue(timer.contains("{{\"⏳ after 2 days<br/>when PendingGuestCallRecorded do recordGuestCall\"}}"), timer)
        val plain = diagrams("").flowchart
        assertTrue(plain.contains("{{\"when PendingGuestCallRecorded do recordGuestCall\"}}"), plain)
    }

    @Test
    fun testVariantTitles_FollowTheStartTheChoicesAndTheEnd() {
        assertEquals("from GuestCallRequired to completed", diagrams("").sequences.single().title)
        val path = ZflSequencePaths(semantic("").flows.single()).paths().single()
        assertEquals("from GuestCallRequired to completed", sequenceVariantTitle(path))
        val withChoices = path.copy(choices = listOf(SequenceChoice("a", 0, true), SequenceChoice("b", 1, false)))
        assertEquals("from GuestCallRequired → a → b to completed", sequenceVariantTitle(withChoices))
    }
}
