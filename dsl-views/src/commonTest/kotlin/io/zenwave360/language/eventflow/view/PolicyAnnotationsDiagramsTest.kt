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
    fun testSequence_ActorPolicyHasActorCallTheServiceAndTheAnswerComeBackOnce() {
        val sequence = diagrams("@actor(FacilityStaff)").sequences.single().mermaid
        assertEquals(
            listOf(
                "sequenceDiagram",
                "actor FacilityStaff",
                "participant GuestCallRequired as GuestCallRequired",
                "participant SlotBlockService as SlotBlockService",
                "GuestCallRequired->>SlotBlockService: recordPendingGuestCall",
                "FacilityStaff->>SlotBlockService: recordGuestCall",
                "SlotBlockService-->>FacilityStaff: GuestCallCompleted",
                "Note over SlotBlockService: end completed",
            ),
            sequence.lines().map { it.trim() },
            sequence
        )
    }

    @Test
    fun testSequence_ActorPolicyKeepsDownstreamDeliveryOfTheAnsweredEvent() {
        val source = flow("@actor(FacilityStaff)").replace(
            "end { completed: GuestCallCompleted }",
            """
            when GuestCallCompleted do archiveCall {
                service Archive.ArchiveService
                emits CallArchived
            }
            end { completed: CallArchived }
            """.trimIndent()
        )
        val sequence = ZflToMermaidDiagramsTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(source))).sequences.single().mermaid
        val lines = sequence.lines().map { it.trim() }
        assertEquals(1, lines.count { it == "SlotBlockService-->>FacilityStaff: GuestCallCompleted" }, sequence)
        assertEquals(1, lines.count { it == "SlotBlockService-->>ArchiveService: GuestCallCompleted" }, sequence)
        assertEquals(0, lines.count { it == "SlotBlockService-->>SlotBlockService: GuestCallCompleted" }, sequence)
    }

    @Test
    fun testSequence_ActorWithSpacesKeepsItsDisplayName() {
        val sequence = diagrams("@actor(\"Facility Staff\")").sequences.single().mermaid
        assertTrue(sequence.contains("    actor Facility_Staff as Facility Staff"), sequence)
        assertTrue(sequence.contains("Facility_Staff->>SlotBlockService: recordGuestCall"), sequence)
    }

    @Test
    fun testSequence_TextWithColonHashAndAngleBracketsIsEntityCoded() {
        val title = diagrams("@title(\"Call: guest #1 <urgent>; now\")").sequences.single().mermaid
        assertTrue(title.contains("SlotBlockService->>SlotBlockService: Call: guest #35;1 #lt;urgent#gt;#59; now"), title)
        val timer = diagrams("@timer(\"every: 2 days #<x>\")").sequences.single().mermaid
        assertTrue(timer.contains("Note over SlotBlockService: ⏳ every: 2 days #35;#lt;x#gt;"), timer)
    }

    @Test
    fun testFlowchart_QuotesHashAndAngleBracketsInTitleAndTimerAreEntityCoded() {
        val title = diagrams("@title(\"Say \\\"hi\\\": <x> #1\")").flowchart
        assertTrue(title.contains("{{\"Say #quot;hi#quot;: #lt;x#gt; #35;1\"}}"), title)
        assertTrue(!title.contains("\\\""), title)
        val timer = diagrams("@timer(\"at \\\"noon\\\" <x>\")").flowchart
        assertTrue(timer.contains("{{\"⏳ at #quot;noon#quot; #lt;x#gt;<br/>when PendingGuestCallRecorded do recordGuestCall\"}}"), timer)
    }

    @Test
    fun testSequence_StartTriggeredTimerPolicyNotesBeforeTheFirstArrowNamingTheCommand() {
        val source = """
            flow F {
                start Begin {}
                @timer("nightly")
                when Begin do run {
                    service Jobs.JobService
                    emits Ran
                }
                end { done: Ran }
            }
        """.trimIndent()
        val lines = ZflToMermaidDiagramsTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(source))).sequences.single().mermaid
            .lines().map { it.trim() }
        val note = lines.indexOf("Note over JobService: ⏳ nightly")
        val arrow = lines.indexOfFirst { it.endsWith(": run") }
        assertTrue(note in 0 until arrow, lines.toString())
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

    @Test
    fun testSequence_AltFamilyTitleIsTheVariantTitleForOnePathAndFromToForSeveral() {
        fun sequences(end: String) = ZflToMermaidDiagramsTransformer().transform(
            ZflSemanticAnalyzer().analyze(
                ZflParser().parseModel(
                    """
                    flow F {
                        start Begin {}
                        when Begin do run {
                            service Jobs.JobService
                            emits Ok @outcome(good)
                            emits Bad @outcome(bad)
                        }
                        end {
                            $end
                        }
                    }
                    """.trimIndent()
                )
            )
        ).sequences
        // one path reaches `failed`: its title names the choice taken
        val single = sequences("done: Ok\n failed: Bad").map { it.title }
        assertEquals(2, single.size)
        assertTrue(single.all { it.startsWith("from Begin → ") }, single.toString())
        assertTrue(single[0].endsWith(" to done") && single[1].endsWith(" to failed"), single.toString())
        // two paths reach `done`: the family keeps `from X to Z`
        val family = sequences("done: Ok, Bad").single()
        assertEquals("from Begin to done", family.title)
        assertTrue(family.mermaid.contains("alt "), family.mermaid)
    }
}
