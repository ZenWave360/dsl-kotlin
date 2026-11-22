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
                val startRef = sourceRefOf(flowName, "starts.$startName")
                start.reportRepeatedAnnotations(listOf("actor"), listOf("time", "timer"), listOf("system"), diagnostics = diagnostics, sourceRef = startRef)
                val actor = start.readSingleAnnotation("actor", diagnostics, startRef)
                val timer = start.readSingleAnnotation("time", diagnostics, startRef)
                    ?: start.readSingleAnnotation("timer", diagnostics, startRef)
                val system = start.readSingleAnnotation("system", diagnostics, startRef)
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
                    sourceRef = startRef
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

                val whenRef = sourceModel.sourceRef("flows.$flowName.whens[$whenIndex]")
                whenModel.reportRepeatedAnnotations(listOf("title"), listOf("actor"), listOf("time", "timer"), diagnostics = diagnostics, sourceRef = whenRef)
                val policyTitle = whenModel.readSingleAnnotation("title", diagnostics, whenRef)
                val policyActor = whenModel.readSingleAnnotation("actor", diagnostics, whenRef)
                val policyTimer = whenModel.readSingleAnnotation("time", diagnostics, whenRef)
                    ?: whenModel.readSingleAnnotation("timer", diagnostics, whenRef)
                if (policyActor != null && policyTimer != null) {
                    diagnostics += ZflSemanticDiagnostic(
                        message = "A policy takes either '@actor' or '@timer', not both.",
                        severity = Severity.ERROR,
                        sourceRef = whenRef,
                    )
                }
                policies += ZflPolicy(
                    description = whenModel.getString("javadoc"),
                    triggers = whenModel.getList("triggers"),
                    condition = whenModel.readSingleAnnotation("if", diagnostics, whenRef),
                    compensates = whenModel.readSingleAnnotation("compensates", diagnostics, whenRef)
                        ?: whenModel.readSingleAnnotation("compensate", diagnostics, whenRef),
                    command = actionName,
                    title = policyTitle,
                    actor = policyActor,
                    timer = policyTimer,
                    events = command.emits.map { it.eventName },
                    rules = (whenModel["occurrence"] as? Map<String, Any?>)?.ruleNames().orEmpty(),
                    sourceRef = whenRef
                )
            }

            diagnostics += validateWhenTriggers(
                flowName = flowName,
                startNames = starts.map { it.name }.toSet(),
                whens = flowModel.getMapList("whens"),
                declaredEvents = events.keys
            )
            val view = toView(flowName, flowModel, diagnostics)
            val timers = flowModel.getMapList("timers").mapIndexed { index, timer ->
                ZflTimer(
                    command = timer.getString("action"),
                    after = timer.getStringOrNull("after"),
                    duration = timer.getStringOrNull("duration"),
                    at = timer.getStringOrNull("at"),
                    unless = timer.getList("unless"),
                    description = timer.getStringOrNull("javadoc"),
                    options = timer.getStringMap("options"),
                    sourceRef = sourceModel.sourceRef("flows.$flowName.timers[$index]"),
                )
            }

            diagnostics += validateCalls(flowName, commandByName.values.toList())
            diagnostics += validateUnreachableActions(flowName, commandByName.values.toList(), policies, timers.map { it.command }.toSet())

            val endOutcomes = flowModel.getStringListMap("end.endOutcomes")
            val endLines = flowModel.getMap("end").getMapList("outcomes")
            val end = ZflEnd(
                description = flowModel.getStringOrNull("end.javadoc")
                    ?: flowModel.getMap("end").getStringOrNull("javadoc"),
                endOutcomes = endOutcomes,
                outcomes = toEndOutcomes(flowName, endLines),
                sourceRef = sourceRefOf(flowName, "end")
            )
            diagnostics += validateTimers(flowName, timers, view, starts.map { it.name }.toSet() + events.keys, commandByName)
            diagnostics += validateEndStates(flowName, end, endLines, view)
            diagnostics += validateFailureEvents(flowName, events.values, end, flowModel.getMapList("whens"), timers)
            diagnostics += validateEndOutcomes(
                flowName,
                endOutcomes,
                events.keys + commandByName.values.flatMap { it.responses }
            )

            val rules = flowModel.getMapList("rules").mapIndexed { index, rule ->
                ZflRule(
                    name = rule.getString("name"),
                    value = ruleText(rule["value"]),
                    aggregate = rule.getStringOrNull("aggregate"),
                    options = (rule["options"] as? Map<*, *>).orEmpty().entries.associate { (k, v) -> k.toString() to optionText(v) },
                    javadoc = rule.getStringOrNull("javadoc"),
                    sourceRef = sourceModel.sourceRef("flows.$flowName.rules[$index]"),
                )
            }

            diagnostics += validateRuleNames(flowName, actionModels.values, rules.map { it.name }.toSet())

            flows += ZflFlow(
                name = flowName,
                description = flowModel.getString("javadoc"),
                options = flowModel.getStringMap("options"),
                starts = starts,
                end = end,
                commands = commandByName.values.toList(),
                events = events.values.toList(),
                policies = policies,
                rules = rules,
                view = view,
                timers = timers,
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

    private fun toView(
        flowName: String,
        flowModel: Map<String, Any?>,
        diagnostics: MutableList<ZflSemanticDiagnostic>,
    ): ZflView? {
        val views = flowModel.getMapList("views")
        views.drop(1).forEachIndexed { index, _ ->
            diagnostics += warning(
                "Flow '$flowName' declares more than one view; only '${views.first().getString("name")}' is used.",
                sourceModel.sourceRef("flows.$flowName.views[${index + 1}]"),
            )
        }
        val viewModel = views.firstOrNull() ?: return null
        val path = "flows.$flowName.views[0]"
        val view = ZflView(
            name = viewModel.getString("name"),
            keyAggregate = viewModel.getString("keyAggregate"),
            keyPath = viewModel.getList("keyPath"),
            members = viewModel.getMapList("members").mapIndexed { index, member ->
                ZflViewMember(
                    aggregate = member.getString("aggregate"),
                    path = member.getList("path"),
                    many = member.getBoolean("many"),
                    sourceRef = sourceModel.sourceRef("$path.members[$index]"),
                )
            },
            description = viewModel.getStringOrNull("javadoc"),
            options = viewModel.getStringMap("options"),
            sourceRef = sourceModel.sourceRef(path),
        )
        diagnostics += validateView(flowName, view, viewModel.getBoolean("first"))
        return view
    }

    /** one view, first in the flow, its root listed without a path and every other member with one. */
    private fun validateView(flowName: String, view: ZflView, first: Boolean): List<ZflSemanticDiagnostic> {
        val result = mutableListOf<ZflSemanticDiagnostic>()
        if (!first) {
            result += warning("View '${view.name}' must be the first block of flow '$flowName'.", view.sourceRef)
        }
        if (view.member(view.keyAggregate) == null) {
            result += warning("View '${view.name}' must list its root '${view.keyAggregate}' as a member.", view.sourceRef)
        }
        view.members.forEachIndexed { index, member ->
            if (view.members.take(index).any { it.aggregate == member.aggregate }) {
                result += warning("Aggregate '${member.aggregate}' is listed more than once in view '${view.name}'.", member.sourceRef)
                return@forEachIndexed
            }
            val isRoot = member.aggregate == view.keyAggregate
            if (isRoot && member.path.isNotEmpty()) {
                result += warning("Root '${member.aggregate}' of view '${view.name}' takes no path: it holds the key.", member.sourceRef)
            }
            if (isRoot && member.many) {
                result += warning("Root '${member.aggregate}' of view '${view.name}' is one per instance; it cannot be '[]'.", member.sourceRef)
            }
            if (!isRoot && member.path.isEmpty()) {
                result += warning(
                    "Member '${member.aggregate}' of view '${view.name}' needs the path of a field pointing to the key.",
                    member.sourceRef,
                )
            }
        }
        return result
    }

    private fun toEndOutcomes(flowName: String, lines: List<Map<String, Any?>>): List<ZflEndOutcome> {
        val byName = linkedMapOf<String, MutableList<ZflEndVariant>>()
        lines.forEachIndexed { index, line ->
            byName.getOrPut(line.getString("name")) { mutableListOf() } += ZflEndVariant(
                events = line.getList("events"),
                expectedStates = line.getMapList("states").associate { it.getString("aggregate") to it.getList("values") },
                sourceRef = sourceModel.sourceRef("flows.$flowName.end.outcomes[$index]"),
            )
        }
        return byName.map { (name, variants) -> ZflEndOutcome(name, variants) }
    }

    /** `after`/`unless` events of the flow, a command the flow defines, `at` a datum of a view member. */
    private fun validateTimers(
        flowName: String,
        timers: List<ZflTimer>,
        view: ZflView?,
        flowEvents: Set<String>,
        commands: Map<String, ZflCommand>,
    ): List<ZflSemanticDiagnostic> {
        val result = mutableListOf<ZflSemanticDiagnostic>()
        timers.forEach { timer ->
            timer.after?.takeIf { it !in flowEvents }?.let {
                result += warning("Timer of '${timer.command}' is armed by '$it', which flow '$flowName' does not emit.", timer.sourceRef)
            }
            timer.unless.filterNot(flowEvents::contains).forEach {
                result += warning("Timer of '${timer.command}' is disarmed by '$it', which flow '$flowName' does not emit.", timer.sourceRef)
            }
            val command = commands[timer.command]
            if (command == null || (command.servicePath == null && command.steps.isEmpty())) {
                result += warning("Timer runs '${timer.command}', which flow '$flowName' does not define.", timer.sourceRef)
            }
            timer.at?.let { at ->
                val aggregate = at.substringBefore('.')
                when {
                    view == null -> result += warning("Timer of '${timer.command}' fires at '$at', but flow '$flowName' has no view.", timer.sourceRef)
                    view.member(aggregate) == null -> result += warning(
                        "Timer of '${timer.command}' fires at '$at', but '$aggregate' is not a member of view '${view.name}'.",
                        timer.sourceRef,
                    )
                }
            }
        }
        return result
    }

    /** expected states name view members, once per line; an event ends one outcome line only. */
    private fun validateEndStates(
        flowName: String,
        end: ZflEnd,
        lines: List<Map<String, Any?>>,
        view: ZflView?,
    ): List<ZflSemanticDiagnostic> {
        val result = mutableListOf<ZflSemanticDiagnostic>()
        val outcomeOf = mutableMapOf<String, String>()
        end.outcomes.forEach { outcome ->
            outcome.variants.forEach { variant ->
                variant.events.forEach { event ->
                    when (val previous = outcomeOf[event]) {
                        null -> outcomeOf[event] = outcome.name
                        outcome.name -> result += warning("Event '$event' ends more than one line of outcome '${outcome.name}'.", variant.sourceRef)
                        else -> result += warning("Event '$event' ends both outcome '$previous' and outcome '${outcome.name}'.", variant.sourceRef)
                    }
                }
            }
        }
        lines.forEachIndexed { index, line ->
            val ref = sourceModel.sourceRef("flows.$flowName.end.outcomes[$index]")
            val name = line.getString("name")
            val aggregates = line.getMapList("states").map { it.getString("aggregate") }
            if (aggregates.isNotEmpty() && view == null) {
                result += warning("Outcome '$name' declares expected states, but flow '$flowName' has no view.", ref)
                return@forEachIndexed
            }
            aggregates.forEachIndexed { i, aggregate ->
                if (aggregate in aggregates.take(i)) {
                    result += warning("Outcome '$name' declares the state of '$aggregate' more than once.", ref)
                } else if (view != null && view.member(aggregate) == null) {
                    result += warning("Outcome '$name' expects a state of '$aggregate', which is not a member of view '${view.name}'.", ref)
                }
            }
        }
        return result
    }

    /** a failure event is consumed by a step or ends the flow. */
    private fun validateFailureEvents(
        flowName: String,
        events: Collection<ZflEvent>,
        end: ZflEnd,
        whens: List<Map<String, Any?>>,
        timers: List<ZflTimer>,
    ): List<ZflSemanticDiagnostic> {
        val consumed = whens.flatMap { it.getList("triggers") }.toSet() + timers.mapNotNull { it.after }
        val ends = end.endOutcomes.values.flatten().toSet()
        return events.filter { it.isError && it.name !in consumed && it.name !in ends }.map {
            warning("Failure event '${it.name}' of flow '$flowName' has no consumer and is not an end.", it.sourceRef)
        }
    }

    private fun warning(message: String, sourceRef: SourceRef?) =
        ZflSemanticDiagnostic(message = message, severity = Severity.WARNING, sourceRef = sourceRef)

    /** ZFL-R12: a name in `@rules(...)` with no rule in the same flow is a warning on that name. */
    private fun validateRuleNames(
        flowName: String,
        actions: Collection<Any?>,
        declared: Set<String>,
    ): List<ZflSemanticDiagnostic> {
        val result = mutableListOf<ZflSemanticDiagnostic>()
        actions.forEach { action ->
            (action as? Map<String, Any?>)?.getMapList("occurrences")?.forEach { occurrence ->
                occurrence.getMapList("rules").forEach { rule ->
                    val name = rule.getString("name")
                    if (name !in declared) {
                        result += ZflSemanticDiagnostic(
                            message = "Flow '$flowName' has no rule '$name'.",
                            severity = Severity.WARNING,
                            sourceRef = sourceModel.sourceRef(rule.getString("locationPath")),
                        )
                    }
                }
            }
        }
        return result
    }

    private fun Map<String, Any?>.ruleNames(): List<String> =
        getMapList("rules").map { it.getString("name") }

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

        var pendingRootCall: MutableCallStep? = null
        var pendingCall: MutableCallStep? = null

        actionModel.getMapList("steps").forEach { stepModel ->
            when (stepModel.getString("type")) {
                "service" -> {
                    flushPendingCall(pendingRootCall, steps)
                    pendingRootCall = null
                    pendingCall = null
                    val stepSystem = stepModel.getString("system")
                    val stepService = stepModel.getStringOrNull("service")
                    val stepServicePath = stepModel.getString("servicePath")
                    steps += ZflServiceStep(stepSystem, stepService, stepServicePath,
                        sourceRefOf(flowName, "actions.$actionName"))
                }

                "call" -> {
                    flushPendingCall(pendingRootCall, steps)
                    pendingRootCall = MutableCallStep(
                        action = stepModel.getString("action"),
                        async = stepModel.getBoolean("async")
                    )
                    pendingCall = pendingRootCall
                }

                "on" -> {
                    if (pendingCall == null) {
                        diagnostics += ZflSemanticDiagnostic(
                            message = "Action '$actionName' has 'on ${stepModel.getString("endOutcome")}' without a preceding call.",
                            severity = Severity.ERROR,
                            sourceRef = sourceRefOf(flowName, actionName)
                        )
                    } else {
                        val call = requireNotNull(pendingCall)
                        val endOutcomes = stepModel.getList("endOutcomes")
                            .ifEmpty { listOf(stepModel.getString("endOutcome")) }
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
                        if (call.async) {
                            diagnostics += ZflSemanticDiagnostic(
                                message = "Action '$actionName' cannot handle responses from async call '${call.action}'. Async calls do not receive responses.",
                                severity = Severity.ERROR,
                                sourceRef = sourceRefOf(flowName, actionName)
                            )
                            return@forEach
                        }
                        val chainedCall = stepModel.getStringOrNull("action")?.let { MutableCallStep(it) }
                        endOutcomes.forEach { endOutcome ->
                            call.handlers += MutableEndOutcomeHandler(
                                endOutcome = endOutcome,
                                action = stepModel.getStringOrNull("action"),
                                chainedCall = chainedCall,
                                signal = if (events.isNotEmpty() || emits || response) {
                                    ZflHandlerSignal(
                                        events = events,
                                        emits = emits,
                                        response = response,
                                        options = stepModel.getStringMap("options"),
                                        outcome = stepModel.getStringOrNull("outcome"),
                                        sourceRef = sourceRefOf(flowName, "actions.$actionName"),
                                    )
                                } else {
                                    null
                                },
                                terminal = stepModel.getBoolean("terminal"),
                                sourceRef = sourceRefOf(flowName, "actions.$actionName"),
                            )
                            if (response) directResponses += events
                        }
                        if (chainedCall != null) {
                            pendingCall = chainedCall
                        }
                    }
                }

                "signal" -> {
                    flushPendingCall(pendingRootCall, steps)
                    pendingRootCall = null
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

        flushPendingCall(pendingRootCall, steps)

        if (systemName == null || servicePath == null) {
            diagnostics += ZflSemanticDiagnostic(
                message = "Action '$actionName' does not declare a service.",
                severity = Severity.WARNING,
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
                    rules = occurrenceModel.ruleNames(),
                    system = parsed.system,
                    service = parsed.service,
                    servicePath = parsed.servicePath,
                    steps = parsed.steps,
                    emissions = parsed.emits,
                    responses = mergeResponseDetails(
                        effective.getResponseList("responseDetails").map { response ->
                            response.copy(sourceRef = response.sourceRef ?: occurrenceSource)
                        },
                        terminalHandlerResponses(parsed.steps),
                        parsed.responses.map { response -> ZflResponse(response, sourceRef = occurrenceSource) },
                    ),
                    sourceRef = occurrenceSource,
                )
            }
        } else {
            emptyList()
        }

        val commandRef = sourceRefOf(flowName, actionName)
        return ZflCommand(
            name = actionName,
            description = actionModel.getStringOrNull("javadoc"),
            system = systemName,
            service = serviceName,
            servicePath = servicePath,
            actor = actionModel.readSingleAnnotation("actor", diagnostics, commandRef),
            emits = emittedOutcomes,
            responses = responseOutcomes.distinct(),
            steps = steps,
            occurrences = occurrences,
            rules = actionModel.getMapList("occurrences").flatMap { it.ruleNames() }.distinct(),
            sourceRef = commandRef
        )
    }

    private fun flushPendingCall(call: MutableCallStep?, steps: MutableList<ZflActionStep>) {
        if (call != null) {
            steps += call.toStep()
        }
    }

    private fun validateCalls(
        flowName: String,
        commands: List<ZflCommand>
    ): List<ZflSemanticDiagnostic> {
        val commandNames = commands.map { it.name }.toSet()
        val diagnostics = mutableListOf<ZflSemanticDiagnostic>()

        commands.forEach { command ->
            command.steps.forEachIndexed { callIndex, step ->
                val call = step as? ZflCallStep ?: return@forEachIndexed
                validateCall(command, call, commands, commandNames, flowName, diagnostics)
                if (command.steps.drop(callIndex + 1).isNotEmpty() &&
                    callExhaustivelyTerminates(call, commands)
                ) {
                    diagnostics += ZflSemanticDiagnostic(
                        message = "Action '${command.name}' handles every response from action '${call.action}', so subsequent lines are unreachable.",
                        severity = Severity.WARNING,
                        sourceRef = sourceRefOf(flowName, command.name)
                    )
                }
            }
        }

        return diagnostics
    }

    private fun callExhaustivelyTerminates(call: ZflCallStep, commands: List<ZflCommand>): Boolean {
        val calledCommand = commands.find { it.name == call.action }
        return !call.async && calledCommand != null && calledCommand.responses.isNotEmpty() &&
            calledCommand.responses.all { response ->
                call.handlers.any { handler ->
                    handler.endOutcome == response &&
                        (handler.terminal || handler.chainedCall?.let { callExhaustivelyTerminates(it, commands) } == true)
                }
            }
    }

    private fun terminalHandlerResponses(steps: List<ZflActionStep>): List<ZflResponse> =
        steps.filterIsInstance<ZflCallStep>().flatMap(::terminalHandlerResponses)

    private fun terminalHandlerResponses(call: ZflCallStep): List<ZflResponse> =
        call.handlers.flatMap { handler ->
            val signal = handler.signal
            val responses = if (handler.terminal && signal?.response == true) {
                signal.events.map { eventName ->
                    ZflResponse(
                        name = eventName,
                        outcome = signal.outcome,
                        options = signal.options,
                        sourceRef = signal.sourceRef ?: handler.sourceRef,
                    )
                }
            } else {
                emptyList()
            }
            responses + handler.chainedCall.orEmptyTerminalHandlerResponses()
        }

    private fun ZflCallStep?.orEmptyTerminalHandlerResponses(): List<ZflResponse> =
        this?.let(::terminalHandlerResponses).orEmpty()

    private fun mergeResponseDetails(vararg responseLists: List<ZflResponse>): List<ZflResponse> =
        responseLists.asList().flatten().distinctBy { it.name }

    private fun validateCall(
        command: ZflCommand,
        call: ZflCallStep,
        commands: List<ZflCommand>,
        commandNames: Set<String>,
        flowName: String,
        diagnostics: MutableList<ZflSemanticDiagnostic>,
    ) {
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
            if (!call.async && calledCommand != null && handler.endOutcome !in calledCommand.responses) {
                diagnostics += ZflSemanticDiagnostic(
                    message = "Action '${command.name}' handles event '${handler.endOutcome}' that is not a declared response of action '${call.action}'.",
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
            handler.chainedCall?.let { chainedCall ->
                validateCall(command, chainedCall, commands, commandNames, flowName, diagnostics)
            }
        }
    }

    private fun validateUnreachableActions(
        flowName: String,
        commands: List<ZflCommand>,
        policies: List<ZflPolicy>,
        timerCommands: Set<String> = emptySet(),
    ): List<ZflSemanticDiagnostic> {
        val reachable = (policies.map { it.command } + timerCommands).toMutableSet()
        fun collect(call: ZflCallStep) {
            reachable += call.action
            call.handlers.forEach { handler ->
                handler.action?.let { reachable += it }
                handler.chainedCall?.let(::collect)
            }
        }
        commands.forEach { command ->
            (command.steps + command.occurrences.flatMap { it.steps })
                .filterIsInstance<ZflCallStep>()
                .forEach(::collect)
        }
        return commands.filter { it.name !in reachable }.map { command ->
            ZflSemanticDiagnostic(
                message = "Action '${command.name}' is never triggered nor called.",
                severity = Severity.WARNING,
                sourceRef = sourceRefOf(flowName, command.name)
            )
        }
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
        declaredEndOutcomes: Set<String>
    ): List<ZflSemanticDiagnostic> {
        val diagnostics = mutableListOf<ZflSemanticDiagnostic>()

        if (!endOutcomes.containsKey("completed")) {
            diagnostics += ZflSemanticDiagnostic(
                message = "Flow '$flowName' must define a 'completed' endOutcome.",
                severity = Severity.WARNING,
                sourceRef = sourceRefOf(flowName, "end")
            )
        }

        endOutcomes.forEach { (outcomeName, eventNames) ->
            eventNames
                .filterNot(declaredEndOutcomes::contains)
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
        val handlers: MutableList<MutableEndOutcomeHandler> = mutableListOf(),
        val sourceRef: SourceRef? = null,
    ) {
        fun toStep(): ZflCallStep = ZflCallStep(action, async, handlers.map { it.toHandler() }, sourceRef)
    }

    private data class MutableEndOutcomeHandler(
        val endOutcome: String,
        val action: String? = null,
        val chainedCall: MutableCallStep? = null,
        val signal: ZflHandlerSignal? = null,
        val terminal: Boolean = false,
        val sourceRef: SourceRef? = null,
    ) {
        fun toHandler(): ZflEndOutcomeHandler = ZflEndOutcomeHandler(
            endOutcome = endOutcome,
            action = action,
            chainedCall = chainedCall?.toStep(),
            signal = signal,
            terminal = terminal,
            sourceRef = sourceRef,
        )
    }

    private inline fun Any?.asMapOrReturn(block: () -> Nothing): Map<String, Any?> =
        this as? Map<String, Any?> ?: block()

    /**
     * Reads one annotation option as text without a generic cast, so the JVM and JS agree.
     * A string, number or boolean becomes its text. Absence and null stay null. A list or a map
     * becomes null and adds a warning: those shapes are not a single value.
     */
    /**
     * Warns about each group of annotation names (aliases of each other, like `@time` and `@timer`) written more
     * than once. Reads the ordered `optionsList`: the `options` map has only the last value of a repeat.
     */
    private fun Map<String, Any?>.reportRepeatedAnnotations(
        vararg groups: List<String>,
        diagnostics: MutableList<ZflSemanticDiagnostic>,
        sourceRef: SourceRef,
    ) {
        val written = (this["optionsList"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>)?.get("name") as? String }
        groups.forEach { group ->
            if (written.count { it in group } > 1) {
                diagnostics += ZflSemanticDiagnostic(
                    message = "Annotation '@${group.first()}' is declared more than once; only one is allowed.",
                    severity = Severity.WARNING,
                    sourceRef = sourceRef,
                )
            }
        }
    }

    private fun Map<String, Any?>.readSingleAnnotation(
        annotation: String,
        diagnostics: MutableList<ZflSemanticDiagnostic>,
        sourceRef: SourceRef,
    ): String? {
        val options = this["options"] as? Map<String, Any?> ?: return null
        if (!options.containsKey(annotation)) return null
        return when (val value = options[annotation]) {
            null -> null
            is String -> value
            is Boolean, is Number -> value.toString()
            else -> {
                diagnostics += ZflSemanticDiagnostic(
                    message = "Annotation '@$annotation' expects a single value.",
                    severity = Severity.WARNING,
                    sourceRef = sourceRef,
                )
                null
            }
        }
    }

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

    /** Rule values keep the typed `simple` from the listener. The semantic model stores the text. */
    /** `severity: high` for an annotation with arguments, its value as text otherwise, null without a value. */
    private fun optionText(value: Any?): String? = when (value) {
        null, true -> null
        is Map<*, *> -> value.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        else -> value.toString()
    }

    private fun ruleText(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        else -> value.toString()
    }

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

    // spec:dsl-kotlin/zfl-language#zfl-r1 - `on X call Z` chains, so events are emitted at any depth of chained calls.
    private fun ZflCommand.publishedEvents(): Set<String> =
        (emits.map { it.eventName } + steps.filterIsInstance<ZflCallStep>().flatMap { it.emittedEvents() }).toSet()

    private fun ZflCallStep.emittedEvents(): List<String> =
        handlers.flatMap { handler ->
            handler.signal?.takeIf { it.emits }?.events.orEmpty() +
                handler.chainedCall?.emittedEvents().orEmpty()
        }
}
