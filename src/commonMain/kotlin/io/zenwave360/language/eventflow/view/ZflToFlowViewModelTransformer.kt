package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.semantic.ZflPolicy
import io.zenwave360.language.zfl.semantic.ZflSemanticModel

/**
 * Transforms a ZFL semantic model into a FlowViewModel (without layout).
 *
 * Mental model:
 * Each when in ZFL expresses:
 *   (trigger event[s]) -> [optional policy] -> command -> emitted event[s]
 *
 * Actor starts are rendered as a direct START -> COMMAND trigger when they have
 * a single unconditional `when`.
 */
class ZflToFlowViewModelTransformer {

    fun transform(semanticModel: ZflSemanticModel): FlowViewModel {
        val nodeMap = mutableMapOf<String, FlowNode>()
        val edgeMap = linkedMapOf<String, FlowEdge>()

        semanticModel.flows.forEach { flow ->
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

            val actorStartNames = flow.starts.filter { it.actor != null }.map { it.name }.toSet()

            flow.policies.forEach { policy ->
                if (isDirectActorStartPolicy(policy, actorStartNames)) {
                    addEdge(
                        edgeMap,
                        FlowEdge(
                            id = edgeId(eventId(policy.triggers.single()), commandId(policy.command)),
                            source = eventId(policy.triggers.single()),
                            target = commandId(policy.command),
                            type = FlowEdgeType.TRIGGER,
                            sourceRef = policy.sourceRef
                        )
                    )
                    connectCommandToEvents(edgeMap, policy)
                    return@forEach
                }

                nodeMap[policyNodeId(policy)] = FlowNode(
                    id = policyNodeId(policy),
                    type = FlowNodeType.POLICY,
                    label = policyLabel(policy),
                    system = null,
                    service = null,
                    sourceRef = policy.sourceRef
                )

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
                connectCommandToEvents(edgeMap, policy)
            }

            flow.end.outcomes.forEach { (outcomeName, eventNames) ->
                eventNames.forEach { eventName ->
                    val nodeId = eventId(eventName)
                    val existingNode = nodeMap[nodeId]
                    nodeMap[nodeId] = if (existingNode != null) {
                        existingNode.copy(
                            endOutcomeLabels = (existingNode.endOutcomeLabels.orEmpty() + outcomeName).distinct()
                        )
                    } else {
                        FlowNode(
                            id = nodeId,
                            type = FlowNodeType.EVENT,
                            label = eventName,
                            system = null,
                            service = null,
                            sourceRef = flow.end.sourceRef,
                            endOutcomeLabels = listOf(outcomeName)
                        )
                    }
                }
            }
        }

        return FlowViewModel(
            nodes = nodeMap.values.toList(),
            edges = edgeMap.values.toList()
        )
    }

    private fun isDirectActorStartPolicy(policy: ZflPolicy, actorStartNames: Set<String>): Boolean {
        return policy.condition == null &&
                policy.triggers.size == 1 &&
                policy.triggers.single() in actorStartNames
    }

    private fun connectCommandToEvents(edgeMap: MutableMap<String, FlowEdge>, policy: ZflPolicy) {
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
    private fun policyNodeId(policy: ZflPolicy): String =
        "policy:${policy.triggers.joinToString(",")}:${policy.command}"
    private fun policyLabel(policy: ZflPolicy): String =
        "when ${policy.triggers.joinToString(",")} do ${policy.command}" +
                (if (policy.condition != null) " if ${policy.condition}" else "")
}

/** Backward-compatibility alias; use [ZflToFlowViewModelTransformer] for new code. */
typealias ZflToFlowIrTransformer = ZflToFlowViewModelTransformer
