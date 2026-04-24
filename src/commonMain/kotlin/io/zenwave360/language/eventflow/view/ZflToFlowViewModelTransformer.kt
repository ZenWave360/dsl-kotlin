package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.semantic.*

/**
 * Transforms a ZFL semantic model into a FlowViewModel (without layout).
 *
 * Mental model:
 * Each when in ZFL expresses:
 *   (trigger event[s]) → [optional policy] → command → emitted event[s]
 *
 * The transformer's job is to make this chain explicit as nodes and edges.
 * The resulting FlowViewModel has no layout (position/dimensions are null).
 */
class ZflToFlowViewModelTransformer {

    fun transform(semanticModel: ZflSemanticModel): FlowViewModel {
        val nodeMap = mutableMapOf<String, FlowNode>()
        val edgeMap = linkedMapOf<String, FlowEdge>()

        semanticModel.flows.forEach { flow ->
            val endNodeId = endId("end")

            // 1. Register start events as nodes + self-loop TRIGGER edge (marks the flow entry point)
            flow.starts.forEach { start ->
                nodeMap[eventId(start.name)] = FlowNode(
                    id = eventId(start.name),
                    type = FlowNodeType.START,
                    label = start.name,
                    system = null,
                    service = null,
                    sourceRef = start.sourceRef
                )
                addEdge(
                    edgeMap,
                    FlowEdge(
                        id = edgeId(eventId(start.name), eventId(start.name)),
                        source = eventId(start.name),
                        target = eventId(start.name),
                        type = FlowEdgeType.TRIGGER,
                        sourceRef = start.sourceRef
                    )
                )
            }

            // 2. Register all commands as nodes
            flow.commands.forEach { command ->
                nodeMap[commandId(command.name)] = FlowNode(
                    id = commandId(command.name),
                    type = FlowNodeType.COMMAND,
                    label = command.name,
                    system = command.system,
                    service = command.service,
                    sourceRef = command.sourceRef
                )
            }

            // 3. Register all events as nodes
            flow.events.forEach { event ->
                nodeMap[eventId(event.name)] = FlowNode(
                    id = eventId(event.name),
                    type = FlowNodeType.EVENT,
                    label = event.name,
                    system = event.system,
                    service = event.service,
                    sourceRef = event.sourceRef
                )
            }

            // 4. Register all policies as nodes
            flow.policies.forEach { policy ->
                nodeMap[policyNodeId(policy)] = FlowNode(
                    id = policyNodeId(policy),
                    type = FlowNodeType.POLICY,
                    label = policyLabel(policy),
                    system = null,
                    service = null,
                    sourceRef = policy.sourceRef
                )
                // connect policy triggers (events) to policy node
                val edgeType = if (policy.condition != null) FlowEdgeType.CONDITIONAL else FlowEdgeType.TRIGGER
                policy.triggers.forEach { eventName ->
                    addEdge(
                        edgeMap,
                        FlowEdge(
                            id = edgeId(eventId(eventName), policyNodeId(policy)),
                            source = eventId(eventName),
                            target = policyNodeId(policy),
                            type = edgeType,
                            sourceRef = policy.sourceRef
                        )
                    )
                }
                // connect policy node to command (once, not once per trigger)
                addEdge(
                    edgeMap,
                    FlowEdge(
                        id = edgeId(policyNodeId(policy), commandId(policy.command)),
                        source = policyNodeId(policy),
                        target = commandId(policy.command),
                        type = edgeType,
                        sourceRef = policy.sourceRef
                    )
                )
                // connect command to events
                policy.events.forEach { eventName ->
                    addEdge(
                        edgeMap,
                        FlowEdge(
                            id = edgeId(commandId(policy.command), eventId(eventName)),
                            source = commandId(policy.command),
                            target = eventId(eventName),
                            type = FlowEdgeType.CAUSATION,
                            sourceRef = policy.sourceRef
                        )
                    )
                }
            }

            // 5. Always register exactly one END node per flow
            nodeMap[endNodeId] = FlowNode(
                id = endNodeId,
                type = FlowNodeType.END,
                label = "End",
                system = null,
                service = null,
                sourceRef = flow.end.sourceRef
            )

            flow.end.outcomes.forEach { (outcomeName, eventNames) ->
                eventNames.forEach { eventName ->
                    addEdge(
                        edgeMap,
                        FlowEdge(
                            id = labeledEdgeId(eventId(eventName), endNodeId, outcomeName),
                            source = eventId(eventName),
                            target = endNodeId,
                            type = FlowEdgeType.TRIGGER,
                            label = outcomeName,
                            sourceRef = flow.end.sourceRef
                        )
                    )
                }
            }
        }

        return FlowViewModel(
            nodes = nodeMap.values.toList(),
            edges = edgeMap.values.toList()
        )
    }

    private fun addEdge(edgeMap: MutableMap<String, FlowEdge>, edge: FlowEdge) {
        val key = edgeDedupKey(edge)
        if (!edgeMap.containsKey(key)) {
            edgeMap[key] = edge
        }
    }

    private fun edgeDedupKey(edge: FlowEdge): String =
        listOf(edge.source, edge.target, edge.type.name, edge.label ?: "").joinToString("|")

    private fun eventId(event: String): String = "event:${event}"
    private fun commandId(command: String): String = "command:${command}"
    private fun edgeId(source: String, target: String): String = "from[$source]to[$target]"
    private fun labeledEdgeId(source: String, target: String, label: String): String =
        "from[$source]to[$target]label[$label]"
    private fun endId(end: String): String = "end:${end}"
    private fun policyNodeId(policy: ZflPolicy): String =
        "policy:${policy.triggers.joinToString(",")}:${policy.command}"
    private fun policyLabel(policy: ZflPolicy): String =
        "when ${policy.triggers.joinToString(",")} do ${policy.command}" +
                (if (policy.condition != null) " if ${policy.condition}" else "")
}

/** Backward-compatibility alias; use [ZflToFlowViewModelTransformer] for new code. */
typealias ZflToFlowIrTransformer = ZflToFlowViewModelTransformer
