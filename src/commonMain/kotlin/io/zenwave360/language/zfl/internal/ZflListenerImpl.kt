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
    override fun enterFlow_systems(ctx: ZflParser.Flow_systemsContext) {
        // Systems container - no action needed
    }

    override fun enterFlow_system(ctx: ZflParser.Flow_systemContext) {
        val name = getText(ctx.flow_system_name())
        val jd = javadoc(ctx.javadoc())
        
        currentStack.addLast(buildMap()
            .with("name", name)
            .with("javadoc", jd)
            .with("options", buildMap())
            .with("zdl", null)
            .with("services", buildMap())
            .with("events", mutableListOf<Any?>())
        )
        
        val flow = currentStack[currentStack.size - 2]
        @Suppress("UNCHECKED_CAST")
        (flow["systems"] as MutableMap<String, Any?>)[name!!] = currentStack.last()
    }

    override fun exitFlow_system(ctx: ZflParser.Flow_systemContext) {
        currentStack.removeLast()
    }

    override fun enterFlow_system_zdl(ctx: ZflParser.Flow_system_zdlContext) {
        val zdlPath = getValueText(ctx.string())
        currentStack.last()["zdl"] = zdlPath
    }

    override fun enterFlow_system_service(ctx: ZflParser.Flow_system_serviceContext) {
        val serviceName = if (ctx.flow_system_service_name() != null) 
            getText(ctx.flow_system_service_name()) else "DefaultService"

        val service = buildMap()
            .with("name", serviceName)
            .with("options", buildMap())
            .with("commands", mutableListOf<Any?>())
        
        currentStack.addLast(service)
        val system = currentStack[currentStack.size - 2]
        @Suppress("UNCHECKED_CAST")
        (system["services"] as MutableMap<String, Any?>)[serviceName!!] = service
    }

    override fun exitFlow_system_service(ctx: ZflParser.Flow_system_serviceContext) {
        currentStack.removeLast()
    }

    override fun enterFlow_system_service_body(ctx: ZflParser.Flow_system_service_bodyContext) {
        val commands = getArray(ctx.flow_system_service_command_list(), ",")
        currentStack.last()["commands"] = commands
    }

    override fun enterFlow_system_events(ctx: ZflParser.Flow_system_eventsContext) {
        val events = getArray(ctx.flow_system_event_list(), ",")
        currentStack.last()["events"] = events
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
        val triggers = mutableListOf<String>()
        for (trigger in ctx.flow_when_trigger().flow_when_event_trigger()) {
            triggers.add(trigger.text)
        }

        val whenBlock = buildMap()
            .with("triggers", triggers)
            .with("commands", mutableListOf<Any?>())
            .with("events", mutableListOf<Any?>())
            .with("ifs", mutableListOf<Any?>())
            .with("policies", mutableListOf<Any?>())

        currentStack.addLast(whenBlock)
        val flow = currentStack[currentStack.size - 2]
        @Suppress("UNCHECKED_CAST")
        (flow["whens"] as MutableList<Any?>).add(whenBlock)
    }

    override fun exitFlow_when(ctx: ZflParser.Flow_whenContext) {
        currentStack.removeLast()
    }

    override fun enterFlow_when_command(ctx: ZflParser.Flow_when_commandContext) {
        val commandName = getText(ctx.flow_command_name())
        @Suppress("UNCHECKED_CAST")
        (currentStack.last()["commands"] as MutableList<Any?>).add(commandName)
    }

    override fun enterFlow_when_event(ctx: ZflParser.Flow_when_eventContext) {
        val eventName = getText(ctx.flow_event_name())
        @Suppress("UNCHECKED_CAST")
        (currentStack.last()["events"] as MutableList<Any?>).add(eventName)
    }

    override fun enterFlow_when_policy(ctx: ZflParser.Flow_when_policyContext) {
        val policyName = getValueText(ctx.string())
        @Suppress("UNCHECKED_CAST")
        (currentStack.last()["policies"] as MutableList<Any?>).add(policyName)
    }

    override fun enterFlow_when_if(ctx: ZflParser.Flow_when_ifContext) {
        val condition = getValueText(ctx.string())

        val ifBlock = buildMap()
            .with("condition", condition)
            .with("commands", mutableListOf<Any?>())
            .with("events", mutableListOf<Any?>())
            .with("policies", mutableListOf<Any?>())
            .with("elseIfs", mutableListOf<Any?>())
            .with("else", null)

        currentStack.addLast(ifBlock)
        val whenBlock = currentStack[currentStack.size - 2]
        @Suppress("UNCHECKED_CAST")
        (whenBlock["ifs"] as MutableList<Any?>).add(ifBlock)
    }

    override fun exitFlow_when_if(ctx: ZflParser.Flow_when_ifContext) {
        currentStack.removeLast()
    }

    override fun enterFlow_when_else_if(ctx: ZflParser.Flow_when_else_ifContext) {
        val condition = getValueText(ctx.string())

        val elseIfBlock = buildMap()
            .with("condition", condition)
            .with("commands", mutableListOf<Any?>())
            .with("events", mutableListOf<Any?>())
            .with("policies", mutableListOf<Any?>())

        val ifBlock = currentStack.last()
        @Suppress("UNCHECKED_CAST")
        (ifBlock["elseIfs"] as MutableList<Any?>).add(elseIfBlock)
        currentStack.addLast(elseIfBlock)
    }

    override fun exitFlow_when_else_if(ctx: ZflParser.Flow_when_else_ifContext) {
        currentStack.removeLast()
    }

    override fun enterFlow_when_else(ctx: ZflParser.Flow_when_elseContext) {
        val elseBlock = buildMap()
            .with("commands", mutableListOf<Any?>())
            .with("events", mutableListOf<Any?>())
            .with("policies", mutableListOf<Any?>())

        val ifBlock = currentStack.last()
        ifBlock["else"] = elseBlock
        currentStack.addLast(elseBlock)
    }

    override fun exitFlow_when_else(ctx: ZflParser.Flow_when_elseContext) {
        currentStack.removeLast()
    }

    // End block
    override fun enterFlow_end(ctx: ZflParser.Flow_endContext) {
        val end = buildMap()
            .with("outcomes", buildMap())

        currentStack.addLast(end)
        val flow = currentStack[currentStack.size - 2]
        flow["end"] = end
    }

    override fun exitFlow_end(ctx: ZflParser.Flow_endContext) {
        currentStack.removeLast()
    }

    override fun enterFlow_end_outcomes(ctx: ZflParser.Flow_end_outcomesContext) {
        val completedEvents = if (ctx.flow_end_completed() != null)
            getOutcomeEvents(ctx.flow_end_completed()?.flow_end_outcome_list()) else null
        val suspendedEvents = if (ctx.flow_end_suspended() != null)
            getOutcomeEvents(ctx.flow_end_suspended()?.flow_end_outcome_list()) else null
        val cancelledEvents = if (ctx.flow_end_cancelled() != null)
            getOutcomeEvents(ctx.flow_end_cancelled()?.flow_end_outcome_list()) else null

        val outcomes = buildMap()
            .with("completed", completedEvents)
            .with("suspended", suspendedEvents)
            .with("cancelled", cancelledEvents)

        currentStack.last().appendToWithMap("outcomes", outcomes)
    }

    private fun getOutcomeEvents(ctx: ZflParser.Flow_end_outcome_listContext?): List<String>? {
        if (ctx == null) return null
        return getArray(ctx, ",")
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

