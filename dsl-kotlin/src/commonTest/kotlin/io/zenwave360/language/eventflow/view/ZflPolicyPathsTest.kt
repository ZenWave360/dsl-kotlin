package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.Severity
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.language.zfl.semantic.ZflSemanticModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Variants, occurrences and graph of policies annotated with `@title`, `@actor` and `@timer`. */
class ZflPolicyPathsTest {

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

    private fun analyze(source: String): ZflSemanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(source))

    private fun variantIds(model: ZflSemanticModel): Set<String> {
        val path = ZflSequencePaths(model.flows.single()).paths().single()
        return ZflToFlowViewModelTransformer().transform(model, path).nodes.map { it.id }.toSet()
    }

    private val expectedIds = setOf(
        "event:GuestCallRequired",
        "command:recordPendingGuestCall",
        "event:PendingGuestCallRecorded",
        "policy:PendingGuestCallRecorded:recordGuestCall",
        "command:recordGuestCall",
        "event:GuestCallCompleted",
    )

    @Test
    fun testVariant_ThroughPolicyWithTitleAndActorKeepsPolicyCommandAndEvents() {
        val ids = variantIds(analyze(flow("@title(\"Staff call the guest\")\n@actor(FacilityStaff)")))
        assertTrue(expectedIds.all { it in ids }, ids.toString())
    }

    @Test
    fun testVariant_ThroughPolicyWithTitleAndTimerKeepsPolicyCommandAndEvents() {
        val ids = variantIds(analyze(flow("@title(\"Call back\")\n@timer(\"after 2 days\")")))
        assertTrue(expectedIds.all { it in ids }, ids.toString())
    }

    @Test
    fun testPaths_AnnotationsDoNotLeakBetweenOccurrencesOfACommand() {
        val source = """
            flow Notifications {
                start Placed {}
                when Placed do place {
                    service Orders.OrderService
                    emits Shipped
                    emits Returned
                }
                @title("Notify on shipping")
                when Shipped, Returned do notify {
                    service Orders.NotifyService
                    emits Notified
                }
                when Returned, Lost do notify {
                    service Orders.NotifyService
                    emits Notified
                }
                end { done: Notified }
            }
        """.trimIndent()
        val paths = ZflSequencePaths(analyze(source).flows.single()).paths()
        val labels = paths.map { path -> path.messages.map { it.label } }
        // the occurrence of `Shipped, Returned` is titled, the one of `Returned, Lost` is not
        assertTrue(labels.any { "Notify on shipping" in it }, labels.toString())
        val titled = paths.filter { path -> path.messages.any { it.label == "Notify on shipping" } }
        val plain = paths.filter { path -> path.messages.none { it.label == "Notify on shipping" } }
        assertTrue(plain.isNotEmpty() && plain.all { path -> path.messages.any { it.label == "notify" } }, labels.toString())
        // via Shipped only the titled occurrence consumes; the untitled one never shows the title
        assertTrue(titled.none { path -> path.messages.any { it.label == "notify" } }, labels.toString())
    }

    @Test
    fun testGraph_StartTriggeredPolicyWithAnnotationsKeepsItsNode() {
        fun source(annotation: String) = """
            flow GuestCallFlow {
                @actor(Staff)
                start GuestCallRequired {}
                $annotation
                when GuestCallRequired do recordGuestCall {
                    service Bookings.SlotBlockService
                    emits GuestCallCompleted
                }
                end { completed: GuestCallCompleted }
            }
        """.trimIndent()
        val id = "policy:GuestCallRequired:recordGuestCall"
        fun nodes(annotation: String) = ZflToFlowViewModelTransformer().transform(analyze(source(annotation))).nodes
        assertTrue(nodes("").none { it.id == id })
        assertEquals("T", nodes("@title(\"T\")").single { it.id == id }.title)
        assertEquals("actor", nodes("@actor(Other)").single { it.id == id }.policyKind)
        assertEquals("timer", nodes("@timer(\"later\")").single { it.id == id }.policyKind)
    }

    private fun warnings(annotations: String) = analyze(flow(annotations)).diagnostics
        .filter { it.severity == Severity.WARNING && it.message.contains("more than once") }

    @Test
    fun testAnalyze_RepeatedPolicyAnnotationsAreReported() {
        assertEquals(1, warnings("@title(\"a\")\n@title(\"b\")").size)
        assertEquals(1, warnings("@actor(A)\n@actor(B)").size)
        assertEquals(1, warnings("@timer(\"a\")\n@timer(\"b\")").size)
        assertEquals(1, warnings("@time(\"a\")\n@timer(\"b\")").size)
        assertEquals(0, warnings("@title(\"a\")\n@actor(A)").size)
        assertEquals(0, warnings("").size)
    }

    @Test
    fun testAnalyze_RepeatedStartAnnotationsAreReported() {
        val model = analyze(
            """
            flow F {
                @actor(A)
                @actor(B)
                start S {}
                when S do c {
                    service X.Svc
                    emits E
                }
                end { done: E }
            }
            """.trimIndent()
        )
        assertEquals(1, model.diagnostics.count { it.message.contains("more than once") })
        assertEquals("B", model.flows.single().starts.single().actor)
    }
}
