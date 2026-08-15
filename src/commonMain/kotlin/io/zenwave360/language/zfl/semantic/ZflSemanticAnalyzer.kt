package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.zfl.ZflModel

class ZflSemanticAnalyzer {

    private lateinit var sourceModel: ZflModel

    fun analyze(model: ZflModel): ZflSemanticModel {
        sourceModel = model
        val actors = mutableMapOf<String, ZflActor>()
        val systems = mutableMapOf<String, ZflSystem>()
        val flows = mutableListOf<ZflFlow>()
        val diagnostics = mutableListOf<ZflSemanticDiagnostic>()

        model.getSystems().values.forEach { systemData ->
            val systemModel = systemData.asMapOrReturn { return@forEach }
            val systemName = systemModel.getString("name")
            val services = systemModel.getMap("services").values.mapNotNull { serviceData ->
                val service = serviceData as? Map<String, Any?> ?: return@mapNotNull null
                val name = service.getString("name")
                name.takeIf(String::isNotBlank)?.let { it to ZflService(it) }
            }.toMap().toMutableMap()
            systems[systemName] = ZflSystem(systemName, services)
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
                    fields = start.getMap("fields").mapValues { (_, fieldData) ->
                        val field = fieldData as? Map<String, Any?> ?: emptyMap()
                        ZflField(
                            name = field.getString("name"),
                            type = field.getStringOrNull("type"),
                            isArray = field.getBoolean("isArray"),
                            description = field.getStringOrNull("javadoc"),
                            options = field.getStringMap("options"),
                        )
                    },
                    sourceRef = sourceRefOf(flowName, "starts.$startName")
                )
                actor?.let { actorName ->
                    actors.getOrPut(actorName) {
                        ZflActor(actorName, sourceRefOf(flowName, "starts.$startName"))
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
                command.publishedEvents().forEach { eventName ->
                    events.getOrPut(eventName) {
                        ZflEvent(
                            name = eventName,
                            description = null,
                            system = command.system,
                            service = command.service,
                            servicePath = command.servicePath,
                            isError = command.emits.any { it.eventName == eventName && it.failure } ||
                                command.occurrences.any { occurrence ->
                                    occurrence.emissions.any { it.eventName == eventName && it.failure }
                                },
                            sourceRef = sourceRefOf(flowName, eventName)
                        )
                    }
                }
            }

            val policies = mutableListOf<ZflPolicy>()
            flowModel.getMapList("whens").forEachIndexed { whenIndex, whenModel ->
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
                    compensates = JSONPath.get<String>(whenModel, "options.compensates")
                        ?: JSONPath.get<String>(whenModel, "options.compensate"),
                    command = actionName,
                    events = command.emits.map { it.eventName },
                    sourceRef = sourceModel.sourceRef("flows.$flowName.whens[$whenIndex]")
                )
            }

            diagnostics += validateWhenTriggers(
                flowName = flowName,
                startNames = starts.map { it.name }.toSet(),
                whens = flowModel.getMapList("whens"),
                declaredEvents = events.keys
            )
            diagnostics += validateCalls(flowName, commandByName.values.toList())

            val endOutcomes = flowModel.getStringListMap("end.endOutcomes")
            val end = ZflEnd(
                description = flowModel.getStringOrNull("end.javadoc")
                    ?: flowModel.getMap("end").getStringOrNull("javadoc"),
                endOutcomes = endOutcomes,
                sourceRef = sourceRefOf(flowName, "end")
            )
            diagnostics += validateEndOutcomes(flowName, endOutcomes, events.keys)

            flows += ZflFlow(
                name = flowName,
                description = flowModel.getString("javadoc"),
                options = flowModel.getStringMap("options"),
                starts = starts,
                end = end,
                commands = commandByName.values.toList(),
                events = events.values.toList(),
                policies = policies,
                sourceRef = sourceModel.sourceRef("flows.$flowName"),
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
        diagnostics: MutableList<ZflSemanticDiagnostic>,
        includeOccurrences: Boolean = true,
    ): ZflCommand {
        val actionName = actionModel.getString("name")
        val systemName = actionModel.getStringOrNull("system")
        val serviceName = actionModel.getStringOrNull("service")
        val servicePath = actionModel.getStringOrNull("servicePath")
        val steps = mutableListOf<ZflActionStep>()
        val directEmits = mutableListOf<ZflEmission>()
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
                    steps += ZflServiceStep(stepSystem, stepService, stepServicePath,
                        sourceRefOf(flowName, "actions.$actionName"))
                }

                "call" -> {
                    flushPendingCall(pendingCall, steps)
                    pendingCall = MutableCallStep(
                        action = stepModel.getString("action"),
                        async = stepModel.getBoolean("async")
                    )
                }

                "on" -> {
                    if (pendingCall == null) {
                        diagnostics += ZflSemanticDiagnostic(
                            message = "Action '$actionName' has 'on ${stepModel.getString("endOutcome")}' without a preceding call.",
                            severity = Severity.ERROR,
                            sourceRef = sourceRefOf(flowName, actionName)
                        )
                    } else {
                        val events = stepModel.getList("events")
                        val emits = stepModel.getBoolean("emits")
                        val response = stepModel.getBoolean("response")
                        if (response && events.size > 1) {
                            diagnostics += ZflSemanticDiagnostic(
                                message = "Action '$actionName' handles 'on ${stepModel.getString("endOutcome")}' with a response signal declaring multiple events. Response signals must declare exactly one event.",
                                severity = Severity.ERROR,
                                sourceRef = sourceRefOf(flowName, actionName)
                            )
                        }
                        pendingCall.handlers += ZflEndOutcomeHandler(
                            endOutcome = stepModel.getString("endOutcome"),
                            action = stepModel.getStringOrNull("action"),
                            signal = if (events.isNotEmpty() || emits || response) {
                                ZflHandlerSignal(
                                    events = events,
                                    emits = emits,
                                    response = response,
                                    options = stepModel.getStringMap("options"),
                                    outcome = stepModel.getStringOrNull("outcome")
                                )
                            } else {
                                null
                            },
                            sourceRef = sourceRefOf(flowName, "actions.$actionName"),
                        )
                    }
                }

                "signal" -> {
                    flushPendingCall(pendingCall, steps)
                    pendingCall = null
                    val endOutcome = stepModel.getString("endOutcome")
                    val outcome = stepModel.getStringOrNull("outcome")
                    val emits = stepModel.getBoolean("emits")
                    val response = stepModel.getBoolean("response")
                    val options = stepModel.getStringMap("options")
                    if (response && stepModel.getInt("eventCount") > 1 && stepModel.getInt("eventIndex") == 0) {
                        diagnostics += ZflSemanticDiagnostic(
                            message = "Action '$actionName' has a response signal declaring multiple events. Response signals must declare exactly one event.",
                            severity = Severity.ERROR,
                            sourceRef = sourceRefOf(flowName, actionName)
                        )
                    }
                    steps += ZflSignalStep(
                        endOutcome = endOutcome,
                        emits = emits,
                        response = response,
                        options = options,
                        sourceRef = sourceRefOf(flowName, "actions.$actionName"),
                    )
                    if (emits) {
                        directEmits += ZflEmission(
                            eventName = endOutcome,
                            outcome = outcome,
                            failure = options.containsKey("failure"),
                        )
                    }
                    if (response) {
                        directResponses += endOutcome
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
            actionModel.getEmissionList("emissions").ifEmpty {
                actionModel.getList("emits").map { ZflEmission(eventName = it) }
            }
        }
        val responseOutcomes = directResponses.ifEmpty {
            actionModel.getList("responses")
        }
        if (emittedOutcomes.any { it.outcome == null } && emittedOutcomes.any { it.outcome != null }) {
            diagnostics += ZflSemanticDiagnostic(
                message = "Action '$actionName' mixes @outcome annotated and unannotated emits.",
                severity = Severity.WARNING,
                sourceRef = sourceRefOf(flowName, actionName)
            )
        }

        val responseDetails = actionModel.getResponseList("responseDetails").ifEmpty {
            responseOutcomes.map { ZflResponse(it) }
        }
        val occurrences = if (includeOccurrences) {
            actionModel.getMapList("occurrences").mapIndexed { index, occurrenceModel ->
                val effective = LinkedHashMap(actionModel)
                occurrenceModel.forEach { (key, value) ->
                    if (key !in setOf("steps", "emissions", "emits", "responses", "responseDetails") ||
                        (value as? Collection<*>)?.isNotEmpty() == true) {
                        effective[key] = value
                    }
                }
                effective["occurrences"] = emptyList<Map<String, Any?>>()
                val parsed = toCommand(flowName, effective, mutableListOf(), includeOccurrences = false)
                val occurrenceSource = occurrenceModel.getStringOrNull("locationPath")
                    ?.let(sourceModel::sourceRef) ?: parsed.sourceRef
                val occurrenceOptions = occurrenceModel.getStringMap("options")
                ZflCommandOccurrence(
                    key = occurrenceModel.getStringOrNull("occurrenceKey")
                        ?: "$actionName@definition",
                    index = occurrenceModel.getInt("occurrenceIndex").takeIf { it >= 0 } ?: index,
                    description = occurrenceModel.getStringOrNull("javadoc") ?: parsed.description,
                    triggers = occurrenceModel.getList("triggers"),
                    compensates = occurrenceOptions["compensates"] ?: occurrenceOptions["compensate"],
                    actor = occurrenceOptions["actor"],
                    timer = occurrenceOptions["time"] ?: occurrenceOptions["timer"],
                    options = occurrenceOptions,
                    system = parsed.system,
                    service = parsed.service,
                    servicePath = parsed.servicePath,
                    steps = parsed.steps,
                    emissions = parsed.emits,
                    responses = effective.getResponseList("responseDetails").ifEmpty {
                        parsed.responses.map { ZflResponse(it, sourceRef = occurrenceSource) }
                    },
                    sourceRef = occurrenceSource,
                )
            }
        } else {
            emptyList()
        }

        return ZflCommand(
            name = actionName,
            description = actionModel.getStringOrNull("javadoc"),
            system = systemName,
            service = serviceName,
            servicePath = servicePath,
            actor = JSONPath.get<String>(actionModel, "options.actor"),
            emits = emittedOutcomes,
            responses = responseOutcomes,
            steps = steps,
            occurrences = occurrences,
            sourceRef = sourceRefOf(flowName, actionName)
        )
    }

    private fun flushPendingCall(call: MutableCallStep?, steps: MutableList<ZflActionStep>) {
        if (call != null) {
            steps += ZflCallStep(call.action, call.async, call.handlers.toList(), call.sourceRef)
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
                if (!call.async && call.action !in commandNames) {
                    diagnostics += ZflSemanticDiagnostic(
                        message = "Action '${command.name}' calls unknown action '${call.action}'.",
                        severity = Severity.ERROR,
                        sourceRef = sourceRefOf(flowName, command.name)
                    )
                }
                if (call.async && calledCommand != null && calledCommand.emits.isEmpty() && calledCommand.responses.isNotEmpty()) {
                    diagnostics += ZflSemanticDiagnostic(
                        message = "Action '${command.name}' async-calls '${call.action}', but '${call.action}' only declares response outcomes. Async responses are not published events.",
                        severity = Severity.WARNING,
                        sourceRef = sourceRefOf(flowName, command.name)
                    )
                }

                call.handlers.forEach { handler ->
                    if (!call.async && calledCommand != null && handler.endOutcome !in calledCommand.endOutcomes()) {
                        diagnostics += ZflSemanticDiagnostic(
                            message = "Action '${command.name}' handles unknown endOutcome '${handler.endOutcome}' from action '${call.action}'.",
                            severity = Severity.ERROR,
                            sourceRef = sourceRefOf(flowName, command.name)
                        )
                    }
                    if (handler.action != null && handler.action !in commandNames) {
                        diagnostics += ZflSemanticDiagnostic(
                            message = "Action '${command.name}' handles endOutcome '${handler.endOutcome}' with unknown action '${handler.action}'.",
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
        endOutcomes: Map<String, List<String>>,
        declaredEvents: Set<String>
    ): List<ZflSemanticDiagnostic> {
        val diagnostics = mutableListOf<ZflSemanticDiagnostic>()

        if (!endOutcomes.containsKey("completed")) {
            diagnostics += ZflSemanticDiagnostic(
                message = "Flow '$flowName' must define a 'completed' endOutcome.",
                severity = Severity.ERROR,
                sourceRef = sourceRefOf(flowName, "end")
            )
        }

        endOutcomes.forEach { (outcomeName, eventNames) ->
            eventNames
                .filterNot(declaredEvents::contains)
                .forEach { eventName ->
                    diagnostics += ZflSemanticDiagnostic(
                        message = "Flow '$flowName' endOutcome '$outcomeName' references unknown event '$eventName'.",
                        severity = Severity.ERROR,
                        sourceRef = sourceRefOf(flowName, "end")
                    )
                }
        }

        return diagnostics
    }

    private fun sourceRefOf(flowName: String, name: String): SourceRef {
        val relative = if (name.startsWith("starts.") || name.startsWith("actions.") || name == "end") {
            name
        } else {
            "actions.$name"
        }
        val preferred = "flows.$flowName.$relative"
        return if (preferred in sourceModel.getLocations()) sourceModel.sourceRef(preferred)
        else sourceModel.sourceRef("flows.$flowName")
    }

    private data class MutableCallStep(
        val action: String,
        val async: Boolean = false,
        val handlers: MutableList<ZflEndOutcomeHandler> = mutableListOf(),
        val sourceRef: SourceRef? = null,
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
    private fun Map<String, Any?>.getStringMap(key: String): Map<String, String?> =
        (this[key] as? Map<String, Any?>)?.mapValues { (_, value) -> value?.toString() } ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.getStringListMap(path: String): Map<String, List<String>> =
        (JSONPath.get(this, path) as? Map<String, *>)?.mapValues { (_, value) ->
            value as? List<String> ?: emptyList()
        } ?: emptyMap()

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.getEmissionList(key: String): List<ZflEmission> =
        (this[key] as? List<Map<String, Any?>>)?.map { emission ->
            ZflEmission(
                eventName = emission["eventName"] as? String ?: "",
                outcome = emission["outcome"] as? String,
                failure = emission["failure"] as? Boolean ?: false,
                sourceRef = emission["locationPath"]?.toString()?.let(sourceModel::sourceRef),
            )
        }.orEmpty().filter { it.eventName.isNotEmpty() }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.getResponseList(key: String): List<ZflResponse> =
        (this[key] as? List<Map<String, Any?>>)?.mapNotNull { response ->
            val name = response["name"] as? String ?: return@mapNotNull null
            ZflResponse(
                name = name,
                outcome = response["outcome"] as? String,
                options = (response["options"] as? Map<String, Any?>)
                    ?.mapValues { it.value?.toString() }.orEmpty(),
                sourceRef = response["locationPath"]?.toString()?.let(sourceModel::sourceRef),
            )
        }.orEmpty()

    private fun Map<String, Any?>.getString(key: String): String =
        this[key] as? String ?: ""

    private fun Map<String, Any?>.getStringOrNull(key: String): String? =
        this[key] as? String

    private fun Map<String, Any?>.getBoolean(key: String): Boolean =
        this[key] as? Boolean ?: false

    private fun Map<String, Any?>.getInt(key: String): Int =
        this[key] as? Int ?: 0

    private fun ZflCommand.endOutcomes(): Set<String> =
        (emits.map { it.eventName } + responses).toSet()

    private fun ZflCommand.publishedEvents(): Set<String> =
        (emits.map { it.eventName } +
            steps.filterIsInstance<ZflCallStep>()
                .flatMap { call ->
                    call.handlers.flatMap { handler ->
                        handler.signal?.takeIf { it.emits }?.events.orEmpty()
                    }
                }).toSet()
}
