package io.zenwave360.language.zfl.internal

import io.zenwave360.language.antlr.ZflBaseListener
import io.zenwave360.language.antlr.ZflParser
import io.zenwave360.language.utils.buildMap
import io.zenwave360.language.utils.with
import io.zenwave360.language.utils.appendTo
import io.zenwave360.language.utils.appendToList
import io.zenwave360.language.utils.appendToWithMap
import io.zenwave360.language.zfl.ZflModel
import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.tree.ErrorNode
import org.antlr.v4.kotlinruntime.tree.TerminalNode
import io.zenwave360.language.zfl.internal.ZflListenerUtils.getText
import io.zenwave360.language.zfl.internal.ZflListenerUtils.getValueText
import io.zenwave360.language.zfl.internal.ZflListenerUtils.getComplexValue
import io.zenwave360.language.zfl.internal.ZflListenerUtils.getOptionValue
import io.zenwave360.language.zfl.internal.ZflListenerUtils.getArray
import io.zenwave360.language.zfl.internal.ZflListenerUtils.camelCase
import io.zenwave360.language.zfl.internal.ZflListenerUtils.javadoc
import io.zenwave360.language.zfl.internal.ZflListenerUtils.getLocations
import io.zenwave360.language.zfl.internal.ZflListenerUtils.first

class ZflListenerImpl : ZflBaseListener() {

    val model = ZflModel()
    private val currentStack = ArrayDeque<MutableMap<String, Any?>>()

    override fun enterZfl(ctx: ZflParser.ZflContext) {
        // Entry point
    }

    override fun enterGlobal_javadoc(ctx: ZflParser.Global_javadocContext) {
        model.put("javadoc", javadoc(ctx))
    }

    override fun enterImport_(ctx: ZflParser.Import_Context) {
        model.appendToList("imports", buildMap()
            .with("key", getText(ctx.import_key()))
            .with("value", getValueText(ctx.import_value()?.string())))
    }

    override fun enterConfig_option(ctx: ZflParser.Config_optionContext) {
        val name = ctx.field_name().text
        val value = getComplexValue(ctx.complex_value())
        model.appendTo("config", name, value)
    }

    override fun enterFlow(ctx: ZflParser.FlowContext) {
        val name = getText(ctx.flow_name())
        val jd = javadoc(ctx.javadoc())
        
        currentStack.addLast(buildMap()
            .with("name", name)
            .with("className", camelCase(name!!))
            .with("javadoc", jd)
            .with("options", buildMap())
            .with("systems", buildMap())
            .with("starts", buildMap())
            .with("actions", buildMap())
            .with("whens", mutableListOf<Any?>())
            .with("end", buildMap())
        )
        model.appendTo("flows", name, currentStack.last())

        val flowLocation = "flows.$name"
        model.setLocation(flowLocation, getLocations(ctx))
        model.setLocation("$flowLocation.name", getLocations(ctx.flow_name()))
    }

    override fun exitFlow(ctx: ZflParser.FlowContext) {
        currentStack.removeLast()
    }

    override fun enterOption(ctx: ZflParser.OptionContext) {
        val name = ctx.option_name().text.replace("@", "")
        val value = getOptionValue(ctx.option_value())
        if (currentStack.isNotEmpty()) {
            currentStack.last().appendTo("options", name, value)
            currentStack.last().appendToList("optionsList", buildMap().with("name", name).with("value", value))
        }
    }

    // Systems block
    override fun enterSystems(ctx: ZflParser.SystemsContext) {
        model.setLocation("systems", getLocations(ctx))
    }

    override fun enterSystem(ctx: ZflParser.SystemContext) {
        val name = getText(ctx.system_name())
        val jd = javadoc(ctx.javadoc())
        
        currentStack.addLast(buildMap()
            .with("name", name)
            .with("javadoc", jd)
            .with("options", buildMap())
            .with("zdl", null)
            .with("services", buildMap())
            .with("events", mutableListOf<Any?>())
        )

        model.setLocation("systems.$name", getLocations(ctx))
        model.setLocation("systems.${name}.name", getLocations(ctx.system_name()))

        @Suppress("UNCHECKED_CAST")
        (model["systems"] as MutableMap<String, Any?>)[name!!] = currentStack.last()
    }

    override fun exitSystem(ctx: ZflParser.SystemContext) {
        currentStack.removeLast()
    }

    override fun enterSystem_service(ctx: ZflParser.System_serviceContext) {
        val serviceName = if (ctx.system_service_name() != null)
            getText(ctx.system_service_name()) else "DefaultService"

        val service = buildMap()
            .with("name", serviceName)
            .with("options", buildMap())
            .with("aggregates", getArray(ctx.system_service_aggregates(), ","))
            .with("commands", mutableSetOf<Any?>())
        
        currentStack.addLast(service)
        val system = currentStack[currentStack.size - 2]
        @Suppress("UNCHECKED_CAST")
        (system["services"] as MutableMap<String, Any?>)[serviceName!!] = service

        val systemName = system["name"]
        model.setLocation("systems.${systemName}.services.$serviceName", getLocations(ctx))
        model.setLocation("systems.${systemName}.services.$serviceName.name", getLocations(ctx.system_service_name()))
    }

    override fun exitSystem_service(ctx: ZflParser.System_serviceContext) {
        currentStack.removeLast()
    }

    override fun enterSystem_service_body(ctx: ZflParser.System_service_bodyContext) {
        val commands = getArray(ctx.system_service_command_list(), ",")
        currentStack.last()["commands"] = commands
    }

    // Start events
    override fun enterFlow_start(ctx: ZflParser.Flow_startContext) {
        val name = getText(ctx.flow_start_name())
        val jd = javadoc(ctx.javadoc())

        val start = buildMap()
            .with("name", name)
            .with("className", camelCase(name!!))
            .with("javadoc", jd)
            .with("options", buildMap())
            .with("fields", buildMap())

        currentStack.addLast(start)
        val flow = currentStack[currentStack.size - 2]
        @Suppress("UNCHECKED_CAST")
        (flow["starts"] as MutableMap<String, Any?>)[name] = start

        model.setLocation("starts.$name", getLocations(ctx))
        model.setLocation("starts.${name}.name", getLocations(ctx.flow_start_name()))
    }

    override fun exitFlow_start(ctx: ZflParser.Flow_startContext) {
        currentStack.removeLast()
    }

    override fun enterField(ctx: ZflParser.FieldContext) {
        val name = getText(ctx.field_name())
        val type = if (ctx.field_type() != null && ctx.field_type().ID() != null)
            ctx.field_type().ID()!!.text else null
        val isArray = ctx.field_type().ARRAY() != null
        val jd = javadoc(first(ctx.javadoc(), ctx.suffix_javadoc()))

        val field = buildMap()
            .with("name", name)
            .with("type", type)
            .with("isArray", isArray)
            .with("javadoc", jd)
            .with("options", buildMap())

        currentStack.last().appendTo("fields", name!!, field)
    }

    // When blocks
    override fun enterFlow_when(ctx: ZflParser.Flow_whenContext) {
        val triggerGroups = mutableListOf<MutableMap<String, Any?>>()
        val collectedTriggers = mutableListOf<String>()
        var triggerSeparator: String? = null

        for (group in ctx.flow_when_trigger().flow_when_trigger_group()) {
            val events = group.flow_when_event_trigger().map { it.text }
            val commaCount = group.COMMA().size
            val orCount = group.OR().size
            val separatorCount = commaCount + orCount
            val hasMixedSeparators = commaCount > 0 && orCount > 0
            val hasTrailingSeparator = separatorCount >= events.size && separatorCount > 0
            val groupSeparator = when {
                commaCount > 0 && orCount == 0 -> ","
                orCount > 0 && commaCount == 0 -> "|"
                else -> null
            }

            if (triggerSeparator == null && groupSeparator != null) {
                triggerSeparator = groupSeparator
            }

            triggerGroups.add(buildMap()
                .with("events", events)
                .with("separator", groupSeparator)
                .with("hasMixedSeparators", hasMixedSeparators)
                .with("hasTrailingSeparator", hasTrailingSeparator))
            collectedTriggers.addAll(events)
        }

        val duplicateTriggers = collectedTriggers
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        val triggers = collectedTriggers.distinct()
        val actionName = getText(ctx.flow_command_name())

        val whenBlock = buildMap()
            .with("triggers", triggers)
            .with("triggerGroups", triggerGroups)
            .with("triggerSeparator", triggerSeparator)
            .with("action", actionName)

        currentStack.addLast(whenBlock)
        val flow = currentStack[currentStack.size - 2]

        @Suppress("UNCHECKED_CAST")
        val whens = (flow["whens"] as MutableList<Any?>)
        whens.add(whenBlock)

        model.setLocation("whens[${whens.size - 1}]", getLocations(ctx))
        model.setLocation("whens[${whens.size - 1}].triggers", getLocations(ctx.flow_when_trigger()))

        val triggerPath = "whens[${whens.size - 1}].triggers"
        triggerGroups.forEach { group ->
            if (group["hasMixedSeparators"] == true) {
                model.addProblem(triggerPath, null, "Mixed separators are not allowed in when triggers")
            }
            if (group["hasTrailingSeparator"] == true) {
                model.addProblem(triggerPath, null, "Trailing separators are not allowed in when triggers")
            }
        }
        duplicateTriggers.forEach { duplicate ->
            model.addProblem(triggerPath, duplicate, "Duplicate trigger event '%s' in when clause")
        }

    }

    override fun exitFlow_when(ctx: ZflParser.Flow_whenContext) {
        currentStack.removeLast()
    }

    override fun enterFlow_do(ctx: ZflParser.Flow_doContext) {
        val flow = currentStack.last()
        val actionName = getText(ctx.flow_command_name())
        currentStack.addLast(getOrCreateAction(flow, actionName, javadoc(ctx.javadoc())))
    }

    override fun exitFlow_do(ctx: ZflParser.Flow_doContext) {
        currentStack.removeLast()
    }

    override fun enterFlow_do_body(ctx: ZflParser.Flow_do_bodyContext) {
        val current = currentStack.lastOrNull()
        if (current != null && current.containsKey("triggers") && current.containsKey("action")) {
            val flow = currentStack[currentStack.size - 2]
            val actionName = current["action"] as? String
            currentStack.addLast(getOrCreateAction(flow, actionName, null))
        }
    }

    override fun exitFlow_do_body(ctx: ZflParser.Flow_do_bodyContext) {
        val current = currentStack.lastOrNull()
        if (current != null && current.containsKey("steps") && currentStack.size >= 2) {
            val maybeWhen = currentStack[currentStack.size - 2]
            if (maybeWhen.containsKey("triggers") && maybeWhen.containsKey("action")) {
                currentStack.removeLast()
            }
        }
    }

    override fun enterFlow_do_service(ctx: ZflParser.Flow_do_serviceContext) {
        val action = currentStack.last()
        val segments = ctx.flow_service_path().flow_service_segment().mapNotNull { getText(it) }
        val systemName = segments.firstOrNull() ?: "DefaultSystem"
        val serviceName = segments.getOrNull(1)
        val servicePath = segments.joinToString("/")

        action["system"] = systemName
        action["service"] = serviceName
        action["servicePath"] = servicePath
        appendStep(action, buildMap()
            .with("type", "service")
            .with("system", systemName)
            .with("service", serviceName)
            .with("servicePath", servicePath))

        registerCommand(systemName, serviceName ?: systemName, action["name"] as? String)
    }

    override fun enterFlow_do_call(ctx: ZflParser.Flow_do_callContext) {
        appendStep(currentStack.last(), buildMap()
            .with("type", "call")
            .with("async", ctx.ASYNC() != null)
            .with("action", getText(ctx.flow_command_name())))
    }

    override fun enterFlow_do_on(ctx: ZflParser.Flow_do_onContext) {
        val endOutcome = getText(ctx.flow_event_name())
        val step = buildMap()
            .with("type", "on")
            .with("endOutcome", endOutcome)

        when {
            ctx.CALL() != null -> {
                step["kind"] = "call"
                step["action"] = getText(ctx.flow_command_name())
            }
            ctx.flow_signal_body() != null -> {
                val options = getOptions(ctx.annotations())
                val signalBody = ctx.flow_signal_body() ?: return
                val eventNames = signalBody.flow_event_list().flow_event_name().mapNotNull { getText(it) }
                val explicitOutcome = options["outcome"] as? String
                step["kind"] = "signal"
                step["events"] = eventNames
                step["emits"] = signalBody.EMITS() != null
                step["response"] = signalBody.RESPONSE() != null
                step["options"] = options
                step["outcome"] = if (signalBody.EMITS() != null) explicitOutcome ?: endOutcome else null
            }
        }

        appendStep(currentStack.last(), step)
    }

    override fun enterFlow_do_signal(ctx: ZflParser.Flow_do_signalContext) {
        val action = currentStack.last()
        val options = getOptions(ctx.annotations())
        val outcome = options["outcome"] as? String
        val signalBody = ctx.flow_signal_body()
        val eventNames = signalBody.flow_event_list().flow_event_name().mapNotNull { getText(it) }
        eventNames.forEachIndexed { index, endOutcome ->
            appendStep(action, buildMap()
                .with("type", "signal")
                .with("endOutcome", endOutcome)
                .with("emits", signalBody.EMITS() != null)
                .with("response", signalBody.RESPONSE() != null)
                .with("eventCount", eventNames.size)
                .with("eventIndex", index)
                .with("options", options)
                .with("outcome", if (signalBody.EMITS() != null) outcome else null))
            @Suppress("UNCHECKED_CAST")
            if (signalBody.EMITS() != null) {
                (action["emits"] as MutableList<Any?>).add(endOutcome)
                (action["emissions"] as MutableList<Any?>).add(buildMap()
                    .with("eventName", endOutcome)
                    .with("outcome", outcome))
            }
            @Suppress("UNCHECKED_CAST")
            if (signalBody.RESPONSE() != null) {
                // TODO: Preserve response @outcome metadata when responses gain edge representation.
                (action["responses"] as MutableList<Any?>).add(endOutcome)
            }
        }
    }

    private fun getOptions(ctx: ZflParser.AnnotationsContext?): MutableMap<String, Any?> {
        val options = buildMap()
        ctx?.option()?.forEach { option ->
            val name = option.option_name().text.replace("@", "")
            val value = getOptionValue(option.option_value())
            options[name] = value
        }
        return options
    }

    // End block
    override fun enterFlow_end(ctx: ZflParser.Flow_endContext) {
        val end = buildMap()
            .with("endOutcomes", buildMap())

        currentStack.addLast(end)
        val flow = currentStack[currentStack.size - 2]
        flow["end"] = end
    }

    override fun exitFlow_end(ctx: ZflParser.Flow_endContext) {
        currentStack.removeLast()
    }

    override fun enterFlow_end_outcomes(ctx: ZflParser.Flow_end_outcomesContext) {
        val endOutcomes = buildMap()
        ctx.flow_end_outcome().forEach { endOutcome ->
            val outcomeName = endOutcome.flow_end_outcome_name()?.text
            val outcomeEvents = getOutcomeEvents(endOutcome.flow_end_outcome_list())
            if (outcomeName != null) {
                endOutcomes.with(outcomeName, outcomeEvents)
            }
        }

        currentStack.last().appendToWithMap("endOutcomes", endOutcomes)
    }

    private fun getOutcomeEvents(ctx: ZflParser.Flow_end_outcome_listContext?): List<String>? {
        if (ctx == null) return null
        return getArray(ctx, ",")
    }

    private fun getOrCreateAction(
        flow: MutableMap<String, Any?>,
        actionName: String?,
        jd: String?
    ): MutableMap<String, Any?> {
        @Suppress("UNCHECKED_CAST")
        val actions = flow.getOrPut("actions") { buildMap() } as MutableMap<String, Any?>
        val existing = actions[actionName] as? MutableMap<String, Any?>
        if (existing != null) {
            if (jd != null && existing["javadoc"] == null) {
                existing["javadoc"] = jd
            }
            return existing
        }

        val action = buildMap()
            .with("name", actionName)
            .with("className", camelCase(actionName ?: ""))
            .with("javadoc", jd)
            .with("options", buildMap())
            .with("steps", mutableListOf<Any?>())
            .with("emits", mutableListOf<Any?>())
            .with("emissions", mutableListOf<Any?>())
            .with("responses", mutableListOf<Any?>())

        actions[actionName ?: ""] = action
        return action
    }

    private fun appendStep(action: MutableMap<String, Any?>, step: MutableMap<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        (action["steps"] as MutableList<Any?>).add(step)
    }

    private fun registerCommand(systemName: String, serviceName: String, commandName: String?) {
        val systems = model.getOrPut("systems") { mutableMapOf<String, Any>() } as MutableMap<String, Any>
        val system = systems.getOrPut(systemName) {
            buildMap()
                .with("name", systemName)
                .with("services", buildMap())
        } as MutableMap<String, Any>
        val services = system.getOrPut("services") { mutableMapOf<String, Any>() } as MutableMap<String, Any>
        val service = services.getOrPut(serviceName) {
            buildMap()
                .with("name", serviceName)
                .with("commands", mutableSetOf<Any?>())
        } as MutableMap<String, Any>
        @Suppress("UNCHECKED_CAST")
        (service.getOrPut("commands") { mutableSetOf<Any?>() } as MutableCollection<Any?>).add(commandName)
    }

    override fun exitEveryRule(ctx: ParserRuleContext) {
        super.exitEveryRule(ctx)
    }

    override fun visitTerminal(node: TerminalNode) {
        super.visitTerminal(node)
    }

    override fun visitErrorNode(node: ErrorNode) {
        super.visitErrorNode(node)
    }
}

