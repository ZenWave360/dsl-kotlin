package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.zfl.ZflModel

class ZflSemanticAnalyzer {

    fun analyze(model: ZflModel): ZflSemanticModel {
        val actors = mutableMapOf<String, ZflActor>()
        val systems = mutableMapOf<String, ZflSystem>()
        val flows = mutableListOf<ZflFlow>()
        val diagnostics = mutableListOf<ZflSemanticDiagnostic>()

        model.getSystems().values.forEach { systemData ->
            val systemModel = systemData.asMapOrReturn { return@forEach }
            val systemName = systemModel.getString("name")
            systems[systemName] = ZflSystem(systemName)
        }

        model.getFlows().values.forEach { flowData ->
            val flowModel = flowData.asMapOrReturn { return@forEach }
            val flowName = flowModel.getString("name")

            val starts = mutableListOf<ZflStart>()
            flowModel.getMap("starts").values.forEach { startData ->
                val start = startData.asMapOrReturn { return@forEach }
                val startName = start.getString("name")
                val actor = JSONPath.get<String>(start, "options.actor")
                val timer = JSONPath.get<String>(start, "options.time")
                    ?: JSONPath.get<String>(start, "options.timer")
                val system = JSONPath.get<String>(start, "options.system")
                starts += ZflStart(
                    description = start.getString("javadoc"),
                    name = startName,
                    actor = actor,
                    timer = timer,
                    system = system,
                    sourceRef = sourceRefOf(flowName, startName)
                )
                actor?.let { actorName ->
                    actors.getOrPut(actorName) {
                        ZflActor(actorName, sourceRefOf(flowName, startName))
                    }
                }
            }

            val commandByName = mutableMapOf<String, ZflCommand>()
            val actionModels = flowModel.getMap("actions")
            actionModels.values.forEach { actionData ->
                val actionModel = actionData.asMapOrReturn { return@forEach }
                val command = toCommand(flowName, actionModel, diagnostics)
                commandByName[command.name] = command
                command.system?.let { systemName ->
                    if (!systems.containsKey(systemName)) {
                        systems[systemName] = ZflSystem(systemName)
                    }
                }
            }

            val events = mutableMapOf<String, ZflEvent>()
            commandByName.values.forEach { command ->
                command.emits.forEach { outcome ->
                    events.getOrPut(outcome) {
                        ZflEvent(
                            name = outcome,
                            description = null,
                            system = command.system,
                            service = command.service,
                            servicePath = command.servicePath,
                            isError = false,
                            sourceRef = sourceRefOf(flowName, outcome)
                        )
                    }
                }
            }

            val policies = mutableListOf<ZflPolicy>()
            flowModel.getMapList("whens").forEach { whenModel ->
                val actionName = whenModel.getString("action")
                val command = commandByName[actionName]
                if (command == null) {
                    diagnostics += ZflSemanticDiagnostic(
                        message = "Flow '$flowName' references unknown action '$actionName' in when clause.",
                        severity = Severity.ERROR,
                        sourceRef = sourceRefOf(flowName, actionName)
                    )
                    return@forEach
                }

                policies += ZflPolicy(
                    description = whenModel.getString("javadoc"),
                    triggers = whenModel.getList("triggers"),
                    condition = JSONPath.get<String>(whenModel, "options.if"),
                    command = actionName,
                    events = command.emits,
                    sourceRef = sourceRefOf(flowName, actionName)
                )
            }

            diagnostics += validateWhenTriggers(
                flowName = flowName,
                startNames = starts.map { it.name }.toSet(),
                whens = flowModel.getMapList("whens"),
                declaredEvents = events.keys
            )
            diagnostics += validateCalls(flowName, commandByName.values.toList())

            val endOutcomes = flowModel.getStringListMap("end.outcomes")
            val end = ZflEnd(
                outcomes = endOutcomes,
                sourceRef = sourceRefOf(flowName, "end")
            )
            diagnostics += validateEndOutcomes(flowName, endOutcomes, events.keys)

            flows += ZflFlow(
                name = flowName,
                description = flowModel.getString("javadoc"),
                starts = starts,
                end = end,
                commands = commandByName.values.toList(),
                events = events.values.toList(),
                policies = policies,
            )
        }

        return ZflSemanticModel(
            flows = flows,
            systems = systems,
            actors = actors,
            diagnostics = diagnostics
        )
    }

    private fun toCommand(
        flowName: String,
        actionModel: Map<String, Any?>,
        diagnostics: MutableList<ZflSemanticDiagnostic>
    ): ZflCommand {
        val actionName = actionModel.getString("name")
        val systemName = actionModel.getStringOrNull("system")
        val serviceName = actionModel.getStringOrNull("service")
        val servicePath = actionModel.getStringOrNull("servicePath")
        val steps = mutableListOf<ZflActionStep>()
        val directEmits = mutableListOf<String>()
        val directResponses = mutableListOf<String>()

        var pendingCall: MutableCallStep? = null

        actionModel.getMapList("steps").forEach { stepModel ->
            when (stepModel.getString("type")) {
                "service" -> {
                    flushPendingCall(pendingCall, steps)
                    pendingCall = null
                    val stepSystem = stepModel.getString("system")
                    val stepService = stepModel.getStringOrNull("service")
                    val stepServicePath = stepModel.getString("servicePath")
                    steps += ZflServiceStep(stepSystem, stepService, stepServicePath)
                }

                "call" -> {
                    flushPendingCall(pendingCall, steps)
                    pendingCall = MutableCallStep(stepModel.getString("action"))
                }

                "on" -> {
                    if (pendingCall == null) {
                        diagnostics += ZflSemanticDiagnostic(
                            message = "Action '$actionName' has 'on ${stepModel.getString("outcome")}' without a preceding call.",
                            severity = Severity.ERROR,
                            sourceRef = sourceRefOf(flowName, actionName)
                        )
                    } else {
                        pendingCall.handlers += ZflOutcomeHandler(
                            outcome = stepModel.getString("outcome"),
                            action = stepModel.getStringOrNull("action"),
                            emits = stepModel.getStringOrNull("emits")
                        )
                    }
                }

                "signal" -> {
                    flushPendingCall(pendingCall, steps)
                    pendingCall = null
                    val outcome = stepModel.getString("outcome")
                    val emits = stepModel.getBoolean("emits")
                    val response = stepModel.getBoolean("response")
                    steps += ZflSignalStep(
                        outcome = outcome,
                        emits = emits,
                        response = response
                    )
                    if (emits) {
                        directEmits += outcome
                    }
                    if (response) {
                        directResponses += outcome
                    }
                }
            }
        }

        flushPendingCall(pendingCall, steps)

        if (systemName == null || servicePath == null) {
            diagnostics += ZflSemanticDiagnostic(
                message = "Action '$actionName' must declare a service.",
                severity = Severity.ERROR,
                sourceRef = sourceRefOf(flowName, actionName)
            )
        }

        val emittedOutcomes = directEmits.ifEmpty {
            actionModel.getList("emits")
        }
        val responseOutcomes = directResponses.ifEmpty {
            actionModel.getList("responses")
        }

        return ZflCommand(
            name = actionName,
            system = systemName,
            service = serviceName,
            servicePath = servicePath,
            actor = JSONPath.get<String>(actionModel, "options.actor"),
            emits = emittedOutcomes,
            responses = responseOutcomes,
            steps = steps,
            sourceRef = sourceRefOf(flowName, actionName)
        )
    }

    private fun flushPendingCall(call: MutableCallStep?, steps: MutableList<ZflActionStep>) {
        if (call != null) {
            steps += ZflCallStep(call.action, call.handlers.toList())
        }
    }

    private fun validateCalls(
        flowName: String,
        commands: List<ZflCommand>
    ): List<ZflSemanticDiagnostic> {
        val commandNames = commands.map { it.name }.toSet()
        val diagnostics = mutableListOf<ZflSemanticDiagnostic>()

        commands.forEach { command ->
            command.steps.filterIsInstance<ZflCallStep>().forEach { call ->
                val calledCommand = commands.find { it.name == call.action }
                if (call.action !in commandNames) {
                    diagnostics += ZflSemanticDiagnostic(
                        message = "Action '${command.name}' calls unknown action '${call.action}'.",
                        severity = Severity.ERROR,
                        sourceRef = sourceRefOf(flowName, command.name)
                    )
                }

                call.handlers.forEach { handler ->
                    if (calledCommand != null && handler.outcome !in calledCommand.outcomes()) {
                        diagnostics += ZflSemanticDiagnostic(
                            message = "Action '${command.name}' handles unknown outcome '${handler.outcome}' from action '${call.action}'.",
                            severity = Severity.ERROR,
                            sourceRef = sourceRefOf(flowName, command.name)
                        )
                    }
                    if (handler.action != null && handler.action !in commandNames) {
                        diagnostics += ZflSemanticDiagnostic(
                            message = "Action '${command.name}' handles outcome '${handler.outcome}' with unknown action '${handler.action}'.",
                            severity = Severity.ERROR,
                            sourceRef = sourceRefOf(flowName, command.name)
                        )
                    }
                }
            }
        }

        return diagnostics
    }

    private fun validateWhenTriggers(
        flowName: String,
        startNames: Set<String>,
        whens: List<Map<String, Any?>>,
        declaredEvents: Set<String>
    ): List<ZflSemanticDiagnostic> {
        val validTriggers = startNames + declaredEvents
        val diagnostics = mutableListOf<ZflSemanticDiagnostic>()

        whens.forEach { whenModel ->
            whenModel.getList("triggers")
                .filterNot(validTriggers::contains)
                .forEach { trigger ->
                    diagnostics += ZflSemanticDiagnostic(
                        message = "Flow '$flowName' references unknown trigger '$trigger' in when clause.",
                        severity = Severity.ERROR,
                        sourceRef = sourceRefOf(flowName, trigger)
                    )
                }
        }

        return diagnostics
    }

    private fun validateEndOutcomes(
        flowName: String,
        outcomes: Map<String, List<String>>,
        declaredEvents: Set<String>
    ): List<ZflSemanticDiagnostic> {
        val diagnostics = mutableListOf<ZflSemanticDiagnostic>()

        if (!outcomes.containsKey("completed")) {
            diagnostics += ZflSemanticDiagnostic(
                message = "Flow '$flowName' must define a 'completed' end outcome.",
                severity = Severity.ERROR,
                sourceRef = sourceRefOf(flowName, "end")
            )
        }

        outcomes.forEach { (outcomeName, eventNames) ->
            eventNames
                .filterNot(declaredEvents::contains)
                .forEach { eventName ->
                    diagnostics += ZflSemanticDiagnostic(
                        message = "Flow '$flowName' end outcome '$outcomeName' references unknown event '$eventName'.",
                        severity = Severity.ERROR,
                        sourceRef = sourceRefOf(flowName, "end")
                    )
                }
        }

        return diagnostics
    }

    private fun sourceRefOf(flowName: String, name: String): SourceRef =
        SourceRef(
            file = "<zfl>",
            line = 1,
            column = 1
        )

    private data class MutableCallStep(
        val action: String,
        val handlers: MutableList<ZflOutcomeHandler> = mutableListOf()
    )

    private inline fun Any?.asMapOrReturn(block: () -> Nothing): Map<String, Any?> =
        this as? Map<String, Any?> ?: block()

    private fun Map<String, Any?>.getMap(key: String): Map<String, Any?> =
        this[key] as? Map<String, Any?> ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.getList(key: String): List<String> =
        this[key] as? List<String> ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.getMapList(key: String): List<Map<String, Any?>> =
        this[key] as? List<Map<String, Any?>> ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.getStringListMap(path: String): Map<String, List<String>> =
        (JSONPath.get(this, path) as? Map<String, *>)?.mapValues { (_, value) ->
            value as? List<String> ?: emptyList()
        } ?: emptyMap()

    private fun Map<String, Any?>.getString(key: String): String =
        this[key] as? String ?: ""

    private fun Map<String, Any?>.getStringOrNull(key: String): String? =
        this[key] as? String

    private fun Map<String, Any?>.getBoolean(key: String): Boolean =
        this[key] as? Boolean ?: false

    private fun ZflCommand.outcomes(): Set<String> =
        (emits + responses).toSet()
}
