package io.zenwave360.language.eventflow.view

import io.zenwave360.language.source.SourceRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElkLayoutConstraintsTest {

    @Test
    fun partitions_increase_monotonically_on_acyclic_edges() {
        val viewModel = FlowViewModel(
            nodes = listOf(
                node("event:Start", FlowNodeType.START),
                node("command:A", FlowNodeType.COMMAND),
                node("event:X", FlowNodeType.EVENT),
                node("policy:P", FlowNodeType.POLICY),
                node("command:B", FlowNodeType.COMMAND)
            ),
            edges = listOf(
                edge("e1", "event:Start", "command:A", FlowEdgeType.TRIGGER),
                edge("e2", "command:A", "event:X", FlowEdgeType.CAUSATION),
                edge("e3", "event:X", "policy:P", FlowEdgeType.TRIGGER),
                edge("e4", "policy:P", "command:B", FlowEdgeType.TRIGGER)
            )
        )

        val partitions = ElkLayoutConstraints.partitions(viewModel)

        assertTrue(partitions.getValue("event:Start") < partitions.getValue("command:A"))
        assertTrue(partitions.getValue("command:A") < partitions.getValue("event:X"))
        assertTrue(partitions.getValue("event:X") < partitions.getValue("policy:P"))
        assertTrue(partitions.getValue("policy:P") < partitions.getValue("command:B"))
    }

    @Test
    fun partitions_break_cycles_at_policy_to_command_edges() {
        val viewModel = FlowViewModel(
            nodes = listOf(
                node("command:authorizePayment", FlowNodeType.COMMAND),
                node("event:PaymentFailed", FlowNodeType.EVENT),
                node("policy:retry", FlowNodeType.POLICY),
                node("command:retryPayment", FlowNodeType.COMMAND),
                node("event:PaymentRetried", FlowNodeType.EVENT),
                node("policy:authorize", FlowNodeType.POLICY)
            ),
            edges = listOf(
                edge("e1", "command:authorizePayment", "event:PaymentFailed", FlowEdgeType.CAUSATION),
                edge("e2", "event:PaymentFailed", "policy:retry", FlowEdgeType.CONDITIONAL),
                edge("e3", "policy:retry", "command:retryPayment", FlowEdgeType.CONDITIONAL),
                edge("e4", "command:retryPayment", "event:PaymentRetried", FlowEdgeType.CAUSATION),
                edge("e5", "event:PaymentRetried", "policy:authorize", FlowEdgeType.TRIGGER),
                edge("e6", "policy:authorize", "command:authorizePayment", FlowEdgeType.TRIGGER)
            )
        )

        val partitions = ElkLayoutConstraints.partitions(viewModel)

        assertTrue(partitions.getValue("command:authorizePayment") < partitions.getValue("event:PaymentFailed"))
        assertTrue(partitions.getValue("command:retryPayment") < partitions.getValue("event:PaymentFailed"))
        assertTrue(partitions.getValue("event:PaymentFailed") < partitions.getValue("policy:retry"))
        assertTrue(partitions.getValue("command:retryPayment") < partitions.getValue("policy:retry"))
        assertTrue(partitions.getValue("command:retryPayment") < partitions.getValue("event:PaymentRetried"))
        assertTrue(partitions.getValue("event:PaymentRetried") < partitions.getValue("policy:authorize"))
    }

    private fun node(id: String, type: FlowNodeType) = FlowNode(
        id = id,
        type = type,
        label = id,
        system = null,
        service = null,
        sourceRef = SourceRef("<test>", 1, 1)
    )

    private fun edge(id: String, source: String, target: String, type: FlowEdgeType) = FlowEdge(
        id = id,
        source = source,
        target = target,
        type = type
    )
}
