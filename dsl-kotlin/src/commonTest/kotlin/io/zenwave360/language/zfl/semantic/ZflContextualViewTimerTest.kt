package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.zfl.ZflParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** `view` and `timer` are contextual words: still usable as any name, still opening the experimental blocks. */
class ZflContextualViewTimerTest {

    @Test
    fun viewAndTimerAreUsableAsEveryKindOfName() {
        val zfl = """
            systems {
                view {
                    service timer(view) {
                        commands: view, timer
                    }
                }
                timer {
                    service view
                }
            }

            flow view {
                start timer {
                    view String
                    timer String
                }

                when timer do view {
                    service view.timer
                    emits view
                }

                when view do timer {
                    service timer/view
                    @failure
                    emits timer
                    on view call timer
                }

                when timer do timer

                end {
                    view: view
                    timer: timer
                }
            }

            flow timer {
                start view {
                }
            }
        """.trimIndent()
        val model = ZflParser().parseModel(zfl)
        assertEquals(emptyList(), model.getProblems())
        val flows = model["flows"] as Map<*, *>
        assertEquals(setOf("view", "timer"), flows.keys)
        val view = flows["view"] as Map<*, *>
        assertTrue((view["views"] as? List<*>).isNullOrEmpty())
        assertTrue((view["timers"] as? List<*>).isNullOrEmpty())
    }

    @Test
    fun viewAndTimerNamesCoexistWithTheBlocks() {
        val zfl = """
            flow F {
                view view by timer.id {
                    timer
                    view.timer
                    Other[].view
                }
                start view { }
                when view do timer {
                    service Svc.Timer
                    emits timer
                }
                timer after timer "1h" unless view, timer do view
                timer at timer.view do timer
                timer after view "5m" do view {
                    emits view
                }
                end {
                    timer: timer { timer: [view, none] }
                }
            }
        """.trimIndent()
        val model = ZflParser().parseModel(zfl)
        assertEquals(emptyList(), model.getProblems())
        val flow = (model["flows"] as Map<*, *>)["F"] as Map<*, *>
        val views = flow["views"] as List<*>
        assertEquals(1, views.size)
        assertEquals("view", (views[0] as Map<*, *>)["name"])
        assertEquals("timer", (views[0] as Map<*, *>)["keyAggregate"])
        val timers = flow["timers"] as List<*>
        assertEquals(3, timers.size)
        assertEquals("timer", (timers[0] as Map<*, *>)["after"])
        assertEquals(listOf("view", "timer"), (timers[0] as Map<*, *>)["unless"])
        assertNotNull((timers[1] as Map<*, *>)["at"])
        assertEquals("view", (timers[2] as Map<*, *>)["after"])
    }

    @Test
    fun aMisspelledBlockWordIsAProblem() {
        val model = ZflParser().parseModel("flow F {\n  vieww V by A.id { A }\n}")
        assertTrue(model.getProblems().isNotEmpty())
    }
}
