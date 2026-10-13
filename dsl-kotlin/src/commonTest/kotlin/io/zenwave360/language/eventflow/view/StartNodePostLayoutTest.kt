package io.zenwave360.language.eventflow.view

import io.zenwave360.language.source.SourceRef
import kotlin.test.Test
import kotlin.test.assertEquals

class StartNodePostLayoutTest {

    @Test
    fun apply_moves_start_next_to_first_real_target() {
        val start = FlowNode(
            id = "event:Start",
            type = FlowNodeType.START,
            label = "Start",
            system = null,
            service = null,
            sourceRef = SourceRef("<test>", 1, 1),
            position = Point(20.0, 20.0),
            dimensions = Dimensions(180.0, 56.0)
        )
        val command = FlowNode(
            id = "command:DoWork",
            type = FlowNodeType.COMMAND,
            label = "DoWork",
            system = null,
            service = null,
            sourceRef = SourceRef("<test>", 1, 1),
            position = Point(600.0, 20.0),
            dimensions = Dimensions(180.0, 56.0)
        )

        val adjusted = StartNodePostLayout.apply(
            nodes = listOf(start, command),
            edges = listOf(
                FlowEdge("self", "event:Start", "event:Start", FlowEdgeType.TRIGGER),
                FlowEdge("trigger", "event:Start", "command:DoWork", FlowEdgeType.TRIGGER)
            ),
            canvasPadding = 20.0,
            desiredGap = 80.0
        )

        val adjustedStart = adjusted.first { it.id == "event:Start" }
        assertEquals(340.0, adjustedStart.position!!.x)
        assertEquals(20.0, adjustedStart.position!!.y)
    }
}
