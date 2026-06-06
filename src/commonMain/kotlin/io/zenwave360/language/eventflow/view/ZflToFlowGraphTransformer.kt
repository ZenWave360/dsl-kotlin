package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.semantic.ZflCallStep
import io.zenwave360.language.zfl.semantic.ZflCommand
import io.zenwave360.language.zfl.semantic.ZflPolicy
import io.zenwave360.language.zfl.semantic.ZflSemanticModel

class ZflToFlowGraphTransformer {

    fun transform(semanticModel: ZflSemanticModel): FlowGraph {
        val nodeMap = mutableMapOf<String, FlowGraphNode>()
        val edgeMap = linkedMapOf<String, FlowGraphEdge>()

        semanticModel.flows.forEach { flow ->
            flow.starts.forEach { start ->
                nodeMap[eventId(start.name)] = FlowGraphNode(
                    id = eventId(start.name),
                    type = FlowGraphNodeType.START,
                    label = start.name,
                    system = null,
                    service = null,
                    sourceRef = start.sourceRef
                )
                addEdge(
                    edgeMap,
                    FlowGraphEdge(
                        id = edgeId(eventId(start.name), eventId(start.name)),
                        source = eventId(start.name),
                        target = eventId(start.name),
                        type = FlowGraphEdgeType.TRIGGER,
                        sourceRef = start.sourceRef
                    )
                )
            }

            flow.commands.forEach { command ->
                nodeMap[commandId(command.name)] = FlowGraphNode(
                    id = commandId(command.name),
                    type = FlowGraphNodeType.ACTION,
                    label = command.name,
                    system = command.system,
                    service = command.service,
                    sourceRef = command.sourceRef
                )

                command.steps.filterIsInstance<ZflCallStep>().forEach { call ->
                    val calledCommand = flow.commands.find { it.name == call.action }
                    if (calledCommand == null && call.async) {
                        nodeMap[commandId(call.action)] = FlowGraphNode(
                            id = commandId(call.action),
                            type = FlowGraphNodeType.ACTION,
                            label = call.action,
                            system = command.system,
                            service = command.service,
                            sourceRef = command.sourceRef
                        )
                    }
                    addEdge(
                        edgeMap,
                        FlowGraphEdge(
                            id = edgeId(commandId(command.name), commandId(call.action)),
                            source = commandId(command.name),
                            target = commandId(call.action),
                            type = FlowGraphEdgeType.CALL,
                            label = if (call.async) "async" else null,
                            sourceRef = command.sourceRef
                        )
                    )
                    if (!call.async) {
                        connectOutcomeHandlers(nodeMap, edgeMap, command, call, calledCommand)
                    }
                }

                connectCommandToDirectOutcomes(nodeMap, edgeMap, command)
            }

            flow.events.forEach { event ->
                nodeMap[eventId(event.name)] = FlowGraphNode(
                    id = eventId(event.name),
                    type = FlowGraphNodeType.OUTCOME,
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
                        FlowGraphEdge(
                            id = edgeId(eventId(policy.triggers.single()), commandId(policy.command)),
                            source = eventId(policy.triggers.single()),
                            target = commandId(policy.command),
                            type = FlowGraphEdgeType.TRIGGER,
                            sourceRef = policy.sourceRef
                        )
                    )
                    connectActionToOutcomes(edgeMap, policy)
                    return@forEach
                }

                nodeMap[policyNodeId(policy)] = FlowGraphNode(
                    id = policyNodeId(policy),
                    type = FlowGraphNodeType.POLICY,
                    label = policyLabel(policy),
                    system = null,
                    service = null,
                    sourceRef = policy.sourceRef
                )

                val edgeType = if (policy.condition != null) FlowGraphEdgeType.CONDITIONAL else FlowGraphEdgeType.TRIGGER
                policy.triggers.forEach { eventName ->
                    addEdge(
                        edgeMap,
                        FlowGraphEdge(
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
                    FlowGraphEdge(
                        id = edgeId(policyNodeId(policy), commandId(policy.command)),
                        source = policyNodeId(policy),
                        target = commandId(policy.command),
                        type = edgeType,
                        sourceRef = policy.sourceRef
                    )
                )
                connectActionToOutcomes(edgeMap, policy)
            }

            flow.end.endOutcomes.forEach { (outcomeName, eventNames) ->
                eventNames.forEach { eventName ->
                    val nodeId = eventId(eventName)
                    val existingNode = nodeMap[nodeId]
                    nodeMap[nodeId] = if (existingNode != null) {
                        existingNode.copy(
                            endOutcomeLabels = (existingNode.endOutcomeLabels.orEmpty() + outcomeName).distinct()
                        )
                    } else {
                        FlowGraphNode(
                            id = nodeId,
                            type = FlowGraphNodeType.OUTCOME,
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

        return FlowGraph(
            nodes = nodeMap.values.toList(),
            edges = edgeMap.values.toList()
        )
    }

    private fun isDirectActorStartPolicy(policy: ZflPolicy, actorStartNames: Set<String>): Boolean {
        return policy.condition == null &&
            policy.triggers.size == 1 &&
            policy.triggers.single() in actorStartNames
    }

    private fun connectActionToOutcomes(edgeMap: MutableMap<String, FlowGraphEdge>, policy: ZflPolicy) {
        policy.events.forEach { eventName ->
            addEdge(
                edgeMap,
                FlowGraphEdge(
                    id = edgeId(commandId(policy.command), eventId(eventName)),
                    source = commandId(policy.command),
                    target = eventId(eventName),
                    type = FlowGraphEdgeType.CAUSATION,
                    sourceRef = policy.sourceRef
                )
            )
        }
    }

    private fun connectCommandToDirectOutcomes(
        nodeMap: MutableMap<String, FlowGraphNode>,
        edgeMap: MutableMap<String, FlowGraphEdge>,
        command: ZflCommand
    ) {
        command.emits.forEach { emission ->
            ensureOutcomeNode(nodeMap, emission.eventName, command)
            addEdge(
                edgeMap,
                FlowGraphEdge(
                    id = edgeId(commandId(command.name), eventId(emission.eventName)),
                    source = commandId(command.name),
                    target = eventId(emission.eventName),
                    type = FlowGraphEdgeType.CAUSATION,
                    outcome = emission.outcome,
                    sourceRef = command.sourceRef
                )
            )
        }
    }

    private fun connectOutcomeHandlers(
        nodeMap: MutableMap<String, FlowGraphNode>,
        edgeMap: MutableMap<String, FlowGraphEdge>,
        command: ZflCommand,
        call: ZflCallStep,
        calledCommand: ZflCommand?
    ) {
        call.handlers.forEach { handler ->
            val outcomeIsEmitted = calledCommand?.emits?.any { it.eventName == handler.endOutcome } == true
            if (outcomeIsEmitted) {
                ensureOutcomeNode(nodeMap, handler.endOutcome, calledCommand ?: command)
                addEdge(
                    edgeMap,
                    FlowGraphEdge(
                        id = edgeId(commandId(call.action), eventId(handler.endOutcome)),
                        source = commandId(call.action),
                        target = eventId(handler.endOutcome),
                        type = FlowGraphEdgeType.CAUSATION,
                        sourceRef = calledCommand?.sourceRef ?: command.sourceRef
                    )
                )
            }
            if (handler.action != null) {
                addEdge(
                    edgeMap,
                    FlowGraphEdge(
                        id = edgeId(commandId(command.name), commandId(handler.action)),
                        source = commandId(command.name),
                        target = commandId(handler.action),
                        type = FlowGraphEdgeType.OUTCOME_HANDLER,
                        label = "on ${handler.endOutcome}",
                        sourceRef = command.sourceRef
                    )
                )
            }
            handler.signal?.takeIf { it.emits }?.events.orEmpty().forEach { eventName ->
                val targetId = eventId(eventName)
                ensureOutcomeNode(nodeMap, eventName, command)
                addEdge(
                    edgeMap,
                    FlowGraphEdge(
                        id = edgeId(commandId(command.name), targetId),
                        source = commandId(command.name),
                        target = targetId,
                        type = FlowGraphEdgeType.OUTCOME_HANDLER,
                        label = "on ${handler.endOutcome}",
                        outcome = handler.signal?.outcome,
                        sourceRef = command.sourceRef
                    )
                )
            }
        }
    }

    private fun ensureOutcomeNode(
        nodeMap: MutableMap<String, FlowGraphNode>,
        outcomeName: String,
        command: ZflCommand
    ) {
        val nodeId = eventId(outcomeName)
        if (!nodeMap.containsKey(nodeId)) {
            nodeMap[nodeId] = FlowGraphNode(
                id = nodeId,
                type = FlowGraphNodeType.OUTCOME,
                label = outcomeName,
                system = command.system,
                service = command.service,
                sourceRef = command.sourceRef
            )
        }
    }

    private fun addEdge(edgeMap: MutableMap<String, FlowGraphEdge>, edge: FlowGraphEdge) {
        val key = edgeDedupKey(edge)
        if (!edgeMap.containsKey(key)) {
            edgeMap[key] = edge
        }
    }

    private fun edgeDedupKey(edge: FlowGraphEdge): String =
        listOf(edge.source, edge.target, edge.type.name, edge.label ?: "", edge.outcome ?: "").joinToString("|")

    private fun eventId(event: String): String = "event:$event"
    private fun commandId(command: String): String = "command:$command"
    private fun edgeId(source: String, target: String): String = "from[$source]to[$target]"
    private fun policyNodeId(policy: ZflPolicy): String =
        "policy:${policy.triggers.joinToString(",")}:${policy.command}"
    private fun policyLabel(policy: ZflPolicy): String =
        "when ${policy.triggers.joinToString(",")} do ${policy.command}" +
            (if (policy.condition != null) " if ${policy.condition}" else "")
}
