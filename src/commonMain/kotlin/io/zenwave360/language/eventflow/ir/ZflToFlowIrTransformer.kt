package io.zenwave360.language.eventflow.ir

import io.zenwave360.language.zfl.semantic.*

/**
 * Transforms a ZFL semantic model into the canonical EventFlow IR.
 *
 * Mental model:
 * Each when in ZFL expresses:
 *   (trigger event[s]) → [optional policy] → command → emitted event[s]
 *
 * The transformer's job is to make this chain explicit as nodes and edges.
 */
class ZflToFlowIrTransformer {

    fun transform(semanticModel: ZflSemanticModel): FlowIR {
        val nodeMap = mutableMapOf<String, FlowNode>()
        val edgeList = mutableListOf<FlowEdge>()

        semanticModel.flows.forEach { flow ->
            // 1. Register start events as nodes
            flow.starts.forEach { start ->
                nodeMap[startId(start.name)] = FlowNode(
                    id = startId(start.name),
                    type = FlowNodeType.START,
                    label = start.name,
                    system = null,
                    service = null,
                    sourceRef = start.sourceRef
                )
                edgeList.add(FlowEdge(
                    id = edgeId(startId(start.name), eventId(start.name)),
                    source = startId(start.name),
                    target = eventId(start.name),
                    type = FlowEdgeType.TRIGGER,
                    sourceRef = start.sourceRef
                ))
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
                    system = null, // nodeMap[commandId(policy.command)]?.system,
                    service = null,
                    sourceRef = policy.sourceRef
                )
                // connect policy triggers (events) to command
                policy.triggers.forEach { eventName ->
                    val edgeType = if (policy.condition != null) FlowEdgeType.CONDITIONAL else FlowEdgeType.TRIGGER
                    edgeList.add(
                        FlowEdge(
                            id = edgeId(eventId(eventName),policyNodeId(policy)),
                            source = eventId(eventName),
                            target = policyNodeId(policy),
                            type = edgeType,
                            sourceRef = policy.sourceRef
                        )
                    )
                    edgeList.add(
                        FlowEdge(
                            id = edgeId(policyNodeId(policy),commandId(policy.command)),
                            source = policyNodeId(policy),
                            target = commandId(policy.command),
                            type = edgeType,
                            sourceRef = policy.sourceRef
                        )
                    )
                }
                // connect command to events
                policy.events.forEach { eventName ->
                    edgeList.add(FlowEdge(
                        id = edgeId(commandId(policy.command), eventId(eventName)),
                        source = commandId(policy.command),
                        target = eventId(eventName),
                        type = FlowEdgeType.CAUSATION,
                        sourceRef = policy.sourceRef
                    ))
                }
            }

            // 5. Register end nodes
            nodeMap[endId("completed")] = FlowNode(
                id = endId("completed"),
                type = FlowNodeType.END,
                label = "Completed",
                system = null,
                service = null,
                sourceRef = flow.end.sourceRef
            )
            flow.end.completed.forEach { eventName ->
                edgeList.add(FlowEdge(
                    id = edgeId(eventId(eventName), endId("completed")),
                    source = eventId(eventName),
                    target = endId("completed"),
                    type = FlowEdgeType.CAUSATION,
                    sourceRef = flow.end.sourceRef
                ))
            }
        }

        return FlowIR(
            nodes = nodeMap.values.toList(),
            edges = edgeList
        )
    }

    private fun startId(start: String): String =
        "start:${start}"

    private fun eventId(event: String): String =
        "event:${event}"

    private fun commandId(command: String): String =
        "command:${command}"

    private fun edgeId(source: String, target: String): String =
        "from[$source]to[$target]"

    private fun endId(end: String): String =
        "end:${end}"

    private fun policyNodeId(policy: ZflPolicy): String =
        "policy:${policy.triggers.joinToString(",")}:${policy.command}"

    private fun policyLabel(policy: ZflPolicy): String =
        "when ${policy.triggers.joinToString(",")} do ${policy.command}" +
                (if (policy.condition != null) " if ${policy.condition}" else "")
}
