package io.zenwave360.language.eventflow.view

import io.zenwave360.language.source.SourceRef
import io.zenwave360.language.zfl.semantic.ZflCallStep
import io.zenwave360.language.zfl.semantic.ZflCommand
import io.zenwave360.language.zfl.semantic.ZflPolicy
import io.zenwave360.language.zfl.semantic.ZflSignalStep
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
                    sourceRef = start.sourceRef,
                    startKind = startKind(start),
                    actor = start.actor
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
                    if (flow.commands.none { it.name == call.action } && call.async) {
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
                        connectOutcomeHandlers(edgeMap, command, call, flow.commands)
                    }
                }
                ensureHandlerSignalNodes(nodeMap, edgeMap, command)

                connectCommandToDirectOutcomes(nodeMap, edgeMap, command)
                connectCommandToDirectResponses(nodeMap, edgeMap, command)
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
                    connectActionToOutcomes(edgeMap, policy, flow.commands)
                    return@forEach
                }

                nodeMap[policyNodeId(policy)] = FlowGraphNode(
                    id = policyNodeId(policy),
                    type = FlowGraphNodeType.POLICY,
                    label = policyLabel(policy),
                    system = null,
                    service = null,
                    sourceRef = policy.sourceRef,
                    actor = policy.actor,
                    title = policy.title,
                    policyKind = when {
                        policy.timer != null -> "timer"
                        policy.actor != null -> "actor"
                        else -> null
                    },
                    timer = policy.timer
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
                connectActionToOutcomes(edgeMap, policy, flow.commands)
            }

            flow.end.endOutcomes.forEach { (outcomeName, eventNames) ->
                eventNames.forEach { eventName ->
                    val nodeId = eventId(eventName).takeIf(nodeMap::containsKey)
                        ?: readModelId(eventName).takeIf(nodeMap::containsKey)
                        ?: eventId(eventName)
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

    private fun connectActionToOutcomes(
        edgeMap: MutableMap<String, FlowGraphEdge>,
        policy: ZflPolicy,
        commands: List<ZflCommand>
    ) {
        // EF-R18: with `emits response Y` the event hangs from the read model, not from the command
        val emitsResponseEvents = commands.find { it.name == policy.command }
            ?.let { emitsResponseNames(it) }
            .orEmpty()
        policy.events.filterNot { it in emitsResponseEvents }.forEach { eventName ->
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
        val emitsResponses = emitsResponses(command)
        command.emits.forEach { emission ->
            ensureOutcomeNode(nodeMap, emission.eventName, command)
            val emitsResponse = emitsResponses[emission.eventName]
            if (emitsResponse != null) {
                ensureReadModelNode(nodeMap, emission.eventName, command)
                addEdge(
                    edgeMap,
                    FlowGraphEdge(
                        id = edgeId(readModelId(emission.eventName), eventId(emission.eventName)),
                        source = readModelId(emission.eventName),
                        target = eventId(emission.eventName),
                        type = FlowGraphEdgeType.CAUSATION,
                        sourceRef = emitsResponse.sourceRef
                    )
                )
                return@forEach
            }
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

    private class ResponseSignal(val names: List<String>, val emits: Boolean, val response: Boolean, val outcome: String?, val sourceRef: SourceRef?)

    /** Top-level `response`/`emits` steps and the ones nested in `on` handlers, however deep the chained calls. */
    private fun handlerSignals(call: ZflCallStep): List<ResponseSignal> = call.handlers.flatMap { handler ->
        listOfNotNull(handler.signal?.let { ResponseSignal(it.events, it.emits, it.response, it.outcome ?: it.options["outcome"], it.sourceRef) }) +
            handler.chainedCall?.let { handlerSignals(it) }.orEmpty()
    }

    private fun handlerSignals(command: ZflCommand): List<ResponseSignal> =
        command.steps.filterIsInstance<ZflCallStep>().filter { !it.async }.flatMap { handlerSignals(it) }

    private fun emitsResponses(command: ZflCommand): Map<String, ResponseSignal> {
        val direct = command.steps.filterIsInstance<ZflSignalStep>().filter { it.emits && it.response }
            .map { ResponseSignal(listOf(it.endOutcome), true, true, null, it.sourceRef) }
        val nested = handlerSignals(command).filter { it.emits && it.response }
        return (direct + nested).flatMap { signal -> signal.names.map { it to signal } }.toMap()
    }

    private fun emitsResponseNames(command: ZflCommand): Set<String> = emitsResponses(command).keys

    // EF-R16/R18: a response S handled by `on R` still draws its read model (and its event with `emits response`);
    // only the routing edge from the called command's response R to the caller's outcome is gone (EF-R19)
    private fun ensureHandlerSignalNodes(
        nodeMap: MutableMap<String, FlowGraphNode>,
        edgeMap: MutableMap<String, FlowGraphEdge>,
        command: ZflCommand
    ) {
        handlerSignals(command).filter { it.response }.forEach { signal ->
            signal.names.forEach { name ->
                ensureReadModelNode(nodeMap, name, command)
                addEdge(
                    edgeMap,
                    FlowGraphEdge(
                        id = edgeId(commandId(command.name), readModelId(name)),
                        source = commandId(command.name),
                        target = readModelId(name),
                        type = FlowGraphEdgeType.CAUSATION,
                        outcome = signal.outcome ?: name,
                        sourceRef = signal.sourceRef
                    )
                )
                if (signal.emits) {
                    ensureOutcomeNode(nodeMap, name, command)
                    addEdge(
                        edgeMap,
                        FlowGraphEdge(
                            id = edgeId(readModelId(name), eventId(name)),
                            source = readModelId(name),
                            target = eventId(name),
                            type = FlowGraphEdgeType.CAUSATION,
                            sourceRef = signal.sourceRef
                        )
                    )
                }
            }
        }
    }

    private fun connectCommandToDirectResponses(
        nodeMap: MutableMap<String, FlowGraphNode>,
        edgeMap: MutableMap<String, FlowGraphEdge>,
        command: ZflCommand
    ) {
        command.steps.filterIsInstance<ZflSignalStep>()
            .filter { it.response }
            .forEach { response ->
                ensureReadModelNode(nodeMap, response.endOutcome, command)
                addEdge(
                    edgeMap,
                    FlowGraphEdge(
                        id = edgeId(commandId(command.name), readModelId(response.endOutcome)),
                        source = commandId(command.name),
                        target = readModelId(response.endOutcome),
                        type = FlowGraphEdgeType.CAUSATION,
                        outcome = response.options["outcome"] ?: response.endOutcome,
                        sourceRef = response.sourceRef
                    )
                )
            }
    }

    private fun connectOutcomeHandlers(
        edgeMap: MutableMap<String, FlowGraphEdge>,
        command: ZflCommand,
        call: ZflCallStep,
        commands: List<ZflCommand>
    ) {
        call.handlers.forEach { handler ->
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
                handler.chainedCall?.let { chainedCall ->
                    connectOutcomeHandlers(
                        edgeMap,
                        command,
                        chainedCall,
                        commands
                    )
                }
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

    private fun ensureReadModelNode(
        nodeMap: MutableMap<String, FlowGraphNode>,
        name: String,
        command: ZflCommand
    ) {
        val nodeId = readModelId(name)
        if (!nodeMap.containsKey(nodeId)) {
            nodeMap[nodeId] = FlowGraphNode(
                id = nodeId,
                type = FlowGraphNodeType.READ_MODEL,
                label = name,
                system = command.system,
                service = command.service,
                sourceRef = command.sourceRef
            )
        }
    }

    private fun addEdge(edgeMap: MutableMap<String, FlowGraphEdge>, edge: FlowGraphEdge) {
        val key = edgeDedupKey(edge)
        if (!edgeMap.containsKey(key)) {
            edgeMap[key] = edge.copy(id = edgeId(edge))
        }
    }

    private fun edgeDedupKey(edge: FlowGraphEdge): String =
        listOf(edge.source, edge.target, edge.type.name, edge.label ?: "", edge.outcome ?: "").joinToString("|")

    private fun eventId(event: String): String = "event:$event"
    private fun readModelId(readModel: String): String = "readModel:$readModel"
    private fun commandId(command: String): String = "command:$command"
    private fun startKind(start: io.zenwave360.language.zfl.semantic.ZflStart): String = when {
        start.timer != null -> "timer"
        start.actor != null -> "actor"
        else -> "system"
    }
    private fun edgeId(edge: FlowGraphEdge): String = buildString {
        append("from[").append(edge.source).append("]to[").append(edge.target).append(']')
        append(":").append(edge.type.name)
        edge.label?.let { append(":label[").append(it).append(']') }
        edge.outcome?.let { append(":outcome[").append(it).append(']') }
    }
    private fun edgeId(source: String, target: String): String = "from[$source]to[$target]"
    private fun policyNodeId(policy: ZflPolicy): String =
        "policy:${policy.triggers.joinToString(",")}:${policy.command}"
    private fun policyLabel(policy: ZflPolicy): String =
        "when ${policy.triggers.joinToString(",")} do ${policy.command}" +
            (if (policy.condition != null) " if ${policy.condition}" else "")
}
