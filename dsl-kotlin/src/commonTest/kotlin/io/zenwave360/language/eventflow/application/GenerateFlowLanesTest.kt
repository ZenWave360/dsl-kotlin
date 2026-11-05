package io.zenwave360.language.eventflow.application

import io.zenwave360.language.eventflow.view.FlowLane
import io.zenwave360.language.eventflow.view.FlowLaneKind
import io.zenwave360.language.eventflow.view.FlowLanes
import io.zenwave360.language.eventflow.view.FlowNode
import io.zenwave360.language.eventflow.view.FlowViewModel
import io.zenwave360.language.eventflow.view.Point
import io.zenwave360.language.readTestFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** spec:flows/eventflow#ef-r24 */
class GenerateFlowLanesTest {
    private val generator = GenerateFlowViewFromZfl()

    // Beta is declared first but appears second in the flow; the last command has no service.
    private val flow = """
        systems {
            Beta { service BetaService { commands: payIt } }
            Alpha { service AlphaService { commands: orderIt, shipIt } }
        }

        flow Small {
            @actor(Customer)
            start Begin {}

            when Begin do orderIt {
                service Alpha.AlphaService
                emits Ordered
            }

            when Ordered do payIt {
                service Beta.BetaService
                emits Paid
            }

            when Paid do shipIt {
                service Alpha.OtherService
                emits Shipped
            }

            when Shipped do notifyIt {
                emits Notified
            }

            end { done: Notified }
        }
    """.trimIndent()

    private fun FlowViewModel.node(id: String): FlowNode = nodes.first { it.id == id }
    private fun FlowViewModel.laneOf(id: String): FlowLane = lanes!!.first { it.id == node(id).lane }

    @Test
    fun systemsMembershipAndOrderOfFirstAppearance() = runTest {
        val view = generator.execute(flow, FlowLanes.SYSTEMS)

        assertEquals(listOf("Alpha", "Beta", "No service"), view.lanes!!.map { it.label })
        assertEquals(listOf(0, 1, 2), view.lanes!!.map { it.order })
        assertTrue(view.lanes!!.all { it.kind == FlowLaneKind.SYSTEM && it.system == null })
        assertEquals("Alpha", view.laneOf("command:orderIt").label)
        assertEquals("Alpha", view.laneOf("event:Ordered").label)
        // a policy takes the band of the command it calls, even in another system
        val policy = view.nodes.first { it.id.startsWith("policy:") && it.id.endsWith(":payIt") }
        assertEquals("Beta", view.laneOf(policy.id).label)
        // a start takes the band of the command its policy calls
        assertEquals("Alpha", view.laneOf("event:Begin").label)
        assertEquals("No service", view.laneOf("command:notifyIt").label)
        assertEquals("No service", view.laneOf("event:Notified").label)
        // presentation only
        assertNull(view.node("event:Begin").system)
        assertNull(view.node(policy.id).system)
    }

    @Test
    fun servicesLanesHaveSystemAndLastBandForCommandsWithoutService() = runTest {
        val view = generator.execute(flow, FlowLanes.SERVICES)

        assertEquals(listOf("AlphaService", "BetaService", "OtherService", "No service"), view.lanes!!.map { it.label })
        assertEquals(listOf("Alpha", "Beta", "Alpha", null), view.lanes!!.map { it.system })
        assertTrue(view.lanes!!.all { it.kind == FlowLaneKind.SERVICE })
        assertEquals("No service", view.laneOf("command:notifyIt").label)
    }

    @Test
    fun commandWithASystemButNoServiceIsInTheLastBandInBothModes() = runTest {
        val content = """
            flow F {
                @actor(C)
                start Go {}
                when Go do a { service Alpha.AlphaService emits A }
                when A do b { service Alpha emits B }
                end { done: B }
            }
        """.trimIndent()
        FlowLanes.entries.filter { it != FlowLanes.NONE }.forEach { lanes ->
            val view = generator.execute(content, lanes)
            assertEquals(listOf("No service"), view.lanes!!.map { it.label }.filter { it == "No service" })
            assertEquals("No service", view.lanes!!.last().label)
            assertEquals("No service", view.laneOf("command:b").label)
            assertNotNull(view.lanes!!.firstOrNull { it.label.startsWith("Alpha") })
        }
    }

    @Test
    fun startWhosePoliciesCallCommandsInSeveralBandsTakesTheFirstCommandOfTheFlow() = runTest {
        val content = """
            flow F {
                @actor(C)
                start One {}
                @actor(C)
                start Two {}
                when One do a { service Alpha.AlphaService emits A }
                when Two do b { service Beta.BetaService emits B }
                when Two do c { service Gamma.GammaService emits C }
                end { done: A }
            }
        """.trimIndent()
        val view = generator.execute(content, FlowLanes.SYSTEMS)
        assertEquals("Alpha", view.laneOf("event:Two").label)
        assertEquals("Alpha", view.laneOf("event:One").label)
    }

    @Test
    fun variantsKeepEveryNodeInABandWhenAReadModelLosesItsProducer() = runTest {
        val content = """
            flow F {
                @actor(C)
                start One {}
                @actor(C)
                start Two {}
                when One do answer { service Alpha.AlphaService emits response Done }
                when Two do produce { service Beta.BetaService emits Done }
                end { done: Done }
            }
        """.trimIndent()
        val variants = generator.variants(content)
        assertTrue(variants.isNotEmpty())
        variants.forEach { variant ->
            listOf(FlowLanes.SYSTEMS, FlowLanes.SERVICES).forEach { lanes ->
                val view = assertIs<FlowVariantViewResult.Found>(generator.variant(content, variant.id, lanes)).view
                assertTrue(view.nodes.all { it.lane != null })
            }
        }
    }

    @Test
    fun eventRowsAreSeparatedAtTheDrawnSizeOnServerDimensionsOfEitherTarget() {
        listOf(160.0 to 48.0, 180.0 to 124.0).forEach { (w, h) ->
            val ref = io.zenwave360.language.source.SourceRef("f.zfl", 1, 1)
            fun event(id: String, y: Double) = FlowNode(
                id = id, type = io.zenwave360.language.eventflow.view.FlowNodeType.EVENT, label = id, system = "S",
                service = "V", sourceRef = ref, position = Point(20.0, y), dimensions = io.zenwave360.language.eventflow.view.Dimensions(w, h)
            )
            val view = FlowViewModel(nodes = listOf(event("event:A", 20.0), event("event:B", 100.0), event("event:C", 200.0)), edges = emptyList(), bounds = null)
            val laned = io.zenwave360.language.eventflow.view.FlowLaneLayout.apply(view, FlowLanes.SYSTEMS)
            // the page draws each box at least 180 x 112 around the same centre
            val drawn = laned.nodes.map { n ->
                val dh = maxOf(n.dimensions!!.height, 112.0)
                val cy = n.position!!.y + n.dimensions.height / 2
                (cy - dh / 2) to (cy + dh / 2)
            }.sortedBy { it.first }
            drawn.zipWithNext().forEach { (a, b) -> assertTrue(a.second <= b.first, "drawn boxes overlap at $w x $h") }
            val lane = laned.lanes!!.single()
            assertTrue(drawn.first().first >= lane.y && drawn.last().second <= lane.y + lane.height)
        }
    }

    @Test
    fun noLanesIsTheOutputWithoutLanes() = runTest {
        val plain = generator.execute(flow)
        assertEquals(plain, generator.execute(flow, FlowLanes.NONE))
        assertNull(plain.lanes)
        assertTrue(plain.nodes.all { it.lane == null })
    }

    @Test
    fun bandsAreFullWidthStackedAndHoldTheirNodesWithoutOverlap() = runTest {
        listOf(flow, readTestFile("flow/place-order-flow.zfl")).forEach { content ->
            listOf(FlowLanes.SYSTEMS, FlowLanes.SERVICES).forEach { lanes ->
                assertConsistent(generator.execute(content, lanes))
            }
        }
    }

    @Test
    fun placeOrderFixtureHasOneBandPerSystemInOrderOfAppearance() = runTest {
        val view = generator.execute(readTestFile("flow/place-order-flow.zfl"), FlowLanes.SYSTEMS)
        assertEquals("OrdersCheckout", view.lanes!!.first().label)
        assertEquals(view.lanes!!.map { it.label }.distinct(), view.lanes!!.map { it.label })
        assertTrue(view.lanes!!.size >= 4)
    }

    @Test
    fun variantIsDrawnInTheChosenLanes() = runTest {
        val content = readTestFile("flow/book-slot-flow.zfl")
        val id = generator.variants(content).first().id
        val view = assertIs<FlowVariantViewResult.Found>(generator.variant(content, id, FlowLanes.SYSTEMS)).view
        assertNotNull(view.lanes)
        assertConsistent(view)
        assertNull(assertIs<FlowVariantViewResult.Found>(generator.variant(content, id)).view.lanes)
    }

    @Test
    fun positionsAreIgnoredWithLanes() = runTest {
        val auto = generator.execute(flow, FlowLanes.SYSTEMS)
        val positions = auto.nodes.associate { it.id to Point(1.0, 2.0) }
        val result = assertIs<FlowPositionsResult.Applied>(generator.withPositions(flow, positions, FlowLanes.SYSTEMS))
        assertEquals(auto, result.view)
        // none keeps applying positions
        val moved = generator.execute(flow).nodes.associate { it.id to Point(7.0, 9.0) }
        val applied = assertIs<FlowPositionsResult.Applied>(generator.withPositions(flow, moved))
        assertTrue(applied.view.nodes.all { it.position == Point(7.0, 9.0) })
    }

    @Test
    fun jsonCarriesLaneFieldsAndNoneKeepsThemNull() = runTest {
        val view = generator.execute(flow, FlowLanes.SERVICES)
        val json = view.toJson()
        listOf(
            "\"lanes\":[", "\"lane\":\"lane:service:Alpha.AlphaService\"", "\"kind\":\"service\"", "\"label\":",
            "\"system\":\"Alpha\"", "\"order\":0", "\"width\":", "\"height\":", "\"x\":0", "\"y\":0"
        ).forEach { assertContains(json, it) }
        assertEquals(view, FlowViewModel.fromJson(json))
        val none = generator.execute(flow).toJson()
        assertContains(none, "\"lanes\":null")
        assertTrue(!none.contains("\"lane\":\""))
    }

    private fun assertConsistent(view: FlowViewModel) {
        val lanes = view.lanes!!
        val width = view.bounds!!.width
        assertTrue(lanes.all { it.x == 0.0 && it.width == width }, "bands are full width")
        lanes.zipWithNext().forEach { (a, b) -> assertTrue(a.y + a.height <= b.y, "bands do not overlap") }
        view.nodes.groupBy { it.lane }.forEach { (laneId, nodes) ->
            val lane = lanes.first { it.id == laneId }
            nodes.forEach {
                val p = it.position!!
                val d = it.dimensions!!
                assertTrue(p.y >= lane.y && p.y + d.height <= lane.y + lane.height, "${it.id} inside ${lane.id}")
                assertTrue(p.x + d.width <= width)
            }
            for (i in nodes.indices) for (j in i + 1 until nodes.size) {
                val a = nodes[i]
                val b = nodes[j]
                val ap = a.position!!
                val ad = a.dimensions!!
                val bp = b.position!!
                val bd = b.dimensions!!
                val apart = ap.x + ad.width <= bp.x || bp.x + bd.width <= ap.x ||
                    ap.y + ad.height <= bp.y || bp.y + bd.height <= ap.y
                assertTrue(apart, "${a.id} and ${b.id} overlap")
            }
        }
    }
}
