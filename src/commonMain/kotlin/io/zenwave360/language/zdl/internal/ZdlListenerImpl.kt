package io.zenwave360.language.zdl.internal

import io.zenwave360.language.antlr.ZdlBaseListener
import io.zenwave360.language.antlr.ZdlParser
import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.utils.appendTo
import io.zenwave360.language.utils.appendToList
import io.zenwave360.language.utils.appendToWithMap
import io.zenwave360.language.utils.buildMap
import io.zenwave360.language.utils.with
import io.zenwave360.language.zdl.ZdlModel
import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.tree.ErrorNode
import org.antlr.v4.kotlinruntime.tree.TerminalNode
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.camelCase
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.createCRUDMethods
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.first
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.getArray
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.getComplexValue
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.getLocations
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.getOptionValue
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.getText
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.getValueText
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.javadoc
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.kebabCase
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.lowerCamelCase
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.pluralize
import io.zenwave360.language.zdl.internal.ZdlListenerUtils.snakeCase

class ZdlListenerImpl : ZdlBaseListener() {

    val model = ZdlModel()
    private val currentStack = ArrayDeque<MutableMap<String, Any?>>()
    private var currentCollection: String? = null

//    fun getModel(): ZdlModel = model

    override fun enterZdl(ctx: ZdlParser.ZdlContext) {}

    override fun enterSuffix_javadoc(ctx: ZdlParser.Suffix_javadocContext) {
        super.enterSuffix_javadoc(ctx)
    }

    override fun enterGlobal_javadoc(ctx: ZdlParser.Global_javadocContext) {
        model.put("javadoc", javadoc(ctx))
    }

    override fun enterLegacy_constants(ctx: ZdlParser.Legacy_constantsContext) {
        ctx.LEGACY_CONSTANT().map { it.text }.map { it.split(Regex(" *= *")) }.forEach { c ->
            model.appendTo("constants", c[0], c[1])
        }
    }

    override fun enterImport_(ctx: ZdlParser.Import_Context) {
        val name = getText(ctx.import_key())
        val value = getValueText(ctx.import_value()?.string())
        model.appendToList("imports", buildMap()
            .with("key", name)
            .with("value", value))
    }

    override fun enterConfig_option(ctx: ZdlParser.Config_optionContext) {
        val name = ctx.field_name().text
        val value = getComplexValue(ctx.complex_value())
        model.appendTo("config", name, value)
    }

    override fun enterApi(ctx: ZdlParser.ApiContext) {
        val name = getText(ctx.api_name())!!
        val type = getText(ctx.api_type())
        val role = getText(ctx.api_role(), "provider")
        val jd = javadoc(ctx.javadoc())
        currentStack.addLast(
            buildMap()
            .with("name", name)
            .with("type", type)
            .with("role", role)
            .with("javadoc", jd)
            .with("options", buildMap())
            .with("config", buildMap())
        )
        model.appendTo("apis", name, currentStack.last())

        val apiLocation = "apis.$name"
        model.setLocation(apiLocation, getLocations(ctx))
        model.setLocation("$apiLocation.name", getLocations(ctx.api_name()))
        model.setLocation("$apiLocation.type", getLocations(ctx.api_type()))
        ctx.api_role()?.let { model.setLocation("$apiLocation.role", getLocations(it)) }
    }

    override fun enterApi_config(ctx: ZdlParser.Api_configContext) {
        val name = ctx.field_name().text
        val value = getComplexValue(ctx.complex_value())
        currentStack.last().appendTo("config", name, value)
    }

    override fun exitApi(ctx: ZdlParser.ApiContext) { currentStack.removeLast() }

    override fun enterPlugin(ctx: ZdlParser.PluginContext) {
        val name = getText(ctx.plugin_name())!!
        val jd = javadoc(ctx.javadoc())
        val disabled = ctx.plugin_disabled().DISABLED() != null
        val inherit = ctx.plugin_options()?.plugin_options_inherit()?.text ?: true
        currentStack.addLast(
            buildMap()
            .with("name", name)
            .with("javadoc", jd)
            .with("disabled", disabled)
            .with("options", buildMap().with("inherit", inherit))
        )
        model.appendTo("plugins", name, currentStack.last())

        val location = "plugins.$name"
        model.setLocation(location, getLocations(ctx))
        model.setLocation("$location.name", getLocations(ctx.plugin_name()))
        model.setLocation("$location.javadoc", getLocations(ctx.javadoc()))
        if (ctx.plugin_disabled().DISABLED() != null) {
            model.setLocation("$location.disabled", getLocations(ctx.plugin_disabled()))
        }
        ctx.plugin_options()?.let { po ->
            model.setLocation("$location.options", getLocations(po))
            po.plugin_options_inherit()?.let { model.setLocation("$location.options.inherit", getLocations(it)) }
        }
    }

    override fun enterPlugin_config_option(ctx: ZdlParser.Plugin_config_optionContext) {
        val name = getText(ctx.field_name())!!
        val value = getComplexValue(ctx.complex_value())
        currentStack.last().appendTo("config", name, value)
    }

    override fun enterPlugin_config_cli_option(ctx: ZdlParser.Plugin_config_cli_optionContext) {
        val keyword = getText(ctx.keyword())!!
        val value = getText(ctx.simple())
        currentStack.last().appendTo("cliOptions", keyword, value)
    }

    override fun exitPlugin(ctx: ZdlParser.PluginContext) { currentStack.removeLast() }

    override fun enterPolicie_body(ctx: ZdlParser.Policie_bodyContext) {
        val name = getText(ctx.policie_name())!!
        val value = getValueText(ctx.policie_value().simple())
        val aggregate = (ctx.getParent()?.getParent() as ZdlParser.PoliciesContext).policy_aggregate()
        model.appendToWithMap("policies", buildMap().with(name, buildMap().with("name", name).with("value", value).with("aggregate", aggregate)))
        super.enterPolicie_body(ctx)
    }

    override fun enterEntity(ctx: ZdlParser.EntityContext) {
        val entity = ctx.entity_definition()
        val name = entity.entity_name().text
        val jd = javadoc(ctx.javadoc())
        val tableName = getText(entity.entity_table_name())
        currentStack.addLast(processEntity(name, jd, tableName).with("type", "entities"))
        model.appendTo("entities", name, currentStack.last())
        currentCollection = "entities"

        val entityLocation = "$currentCollection.$name"
        model.setLocation(entityLocation, getLocations(ctx))
        model.setLocation("$entityLocation.name", getLocations(entity.entity_name()))
        model.setLocation("$entityLocation.tableName", getLocations(entity.entity_table_name()))
        model.setLocation("$entityLocation.body", getLocations(ctx.entity_body()))
    }

    override fun exitEntity(ctx: ZdlParser.EntityContext) { currentStack.removeLast() }

    private fun processEntity(name: String, javadoc: String?, tableName: String?): MutableMap<String, Any?> {
        val className = camelCase(name)!!
        val instanceName = lowerCamelCase(className)!!
        val kebab = kebabCase(name)!!
        return buildMap()
            .with("name", name)
            .with("className", className)
            .with("tableName", tableName ?: snakeCase(name))
            .with("instanceName", instanceName)
            .with("classNamePlural", pluralize(name))
            .with("instanceNamePlural", pluralize(instanceName))
            .with("kebabCase", kebab)
            .with("kebabCasePlural", pluralize(kebab))
            .with("javadoc", javadoc)
            .with("options", buildMap())
            .with("fields", buildMap())
    }

    override fun enterOption(ctx: ZdlParser.OptionContext) {
        val name = ctx.option_name().text.replace("@", "")
        val value = getOptionValue(ctx.option_value())
        if (currentStack.isNotEmpty()) {
            currentStack.last().appendTo("options", name, value)
            currentStack.last().appendToList("optionsList", buildMap().with("name", name).with("value", value))
        }
        super.enterOption(ctx)
    }

    override fun enterField(ctx: ZdlParser.FieldContext) {
        val name = getText(ctx.field_name())!!
        var type = ctx.field_type()?.ID()?.text
        val initialValue = ctx.field_initialization()?.field_initial_value()?.let { getValueText(it.simple()) }
        val jd = javadoc(first(ctx.javadoc(), ctx.suffix_javadoc()))
        val isEnum = false
        val isEntity = false
        var isArray = ctx.field_type().ARRAY() != null
        val validations = processFieldValidations(ctx.field_validations())
        if ("byte" == type && isArray) {
            type = "byte[]"
            isArray = false
        }
        val field = buildMap()
            .with("name", name)
            .with("type", type)
            .with("initialValue", initialValue)
            .with("javadoc", jd)
            .with("comment", jd)
            .with("isEnum", isEnum)
            .with("isEntity", isEntity)
            .with("isArray", isArray)
            .with("isComplexType", false)
            .with("options", buildMap())
            .with("validations", validations)
        currentStack.last().appendTo("fields", name, field)

        val entityName = currentStack.last()["name"]
        val entityLocation = "$currentCollection.$entityName.fields.$name"
        model.setLocation(entityLocation, getLocations(ctx))
        model.setLocation("$entityLocation.name", getLocations(ctx.field_name()))
        model.setLocation("$entityLocation.type", getLocations(ctx.field_type()))
        for (fieldValidation in ctx.field_validations()) {
            model.setLocation("$entityLocation.validations.${getText(fieldValidation.field_validation_name())}", getLocations(fieldValidation))
        }
        model.setLocation("$entityLocation.javadoc", getLocations(first(ctx.javadoc(), ctx.suffix_javadoc())))

        currentStack.addLast(field)
    }

    private fun processFieldValidations(field_validations: List<ZdlParser.Field_validationsContext>?): Map<String, Any?> {
        val validations = buildMap()
        field_validations?.forEach { v ->
            val name = getText(v.field_validation_name())!!
            var value: Any? = first(getText(v.field_validation_value()), "")
            if ("pattern" == name && value != null) {
                val s = value as String
                value = s.substring(1, s.length - 2)
            }
            validations.with(name, mapOf("name" to name, "value" to value))
        }
        return validations
    }

    override fun exitField(ctx: ZdlParser.FieldContext) {
        currentStack.removeLast()
        super.exitField(ctx)
    }

    override fun enterNested_field(ctx: ZdlParser.Nested_fieldContext) {
        val parent = ctx.getParent() as ZdlParser.FieldContext
        val parentEntity = currentStack[currentStack.size - 2]
        @Suppress("UNCHECKED_CAST")
        val parentEntityFields = parentEntity["fields"] as MutableMap<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val parentField = ArrayList(parentEntityFields.values)[parentEntityFields.size - 1] as MutableMap<String, Any?>
        val entityName = parent.field_type().ID()!!.text
        val entityJavadoc = javadoc(parent.javadoc())
        val tableName = getText(parent.entity_table_name())
        val validations = processNestedFieldValidations(ctx.nested_field_validations())
        parentField.appendToWithMap("validations", validations)
        currentStack.addLast(processEntity(entityName, entityJavadoc, tableName).with("type", currentCollection!!.split(".")[0]))
        currentStack.last().appendTo("options", "embedded", true)
        @Suppress("UNCHECKED_CAST")
        val parentFieldOptions = JSONPath.get(parentField, "options", mapOf<String, Any>()) as Map<String, Any>
        for ((k, v) in parentFieldOptions.entries) {
            currentStack.last().appendTo("options", k, v)
        }
        model.appendTo(currentCollection!!, entityName, currentStack.last())

        val entityLocation = "$currentCollection.$entityName"
        val startLocation = getLocations(parent.field_type())
        val endLocation = getLocations(ctx)
        model.setLocation(entityLocation, mergeLocations(startLocation, endLocation))
        model.setLocation("$entityLocation.name", getLocations(parent.field_type()))
        model.setLocation("$entityLocation.tableName", getLocations(parent.entity_table_name()))
        model.setLocation("$entityLocation.body", getLocations(ctx))
    }

    private fun mergeLocations(startLocation: IntArray?, endLocation: IntArray?): IntArray? {
        if (startLocation == null || endLocation == null) return null
        return intArrayOf(startLocation[0], endLocation[1], startLocation[2], startLocation[3], endLocation[4], endLocation[5])
    }

    private fun processNestedFieldValidations(field_validations: List<ZdlParser.Nested_field_validationsContext>?): Map<String, Any?> {
        val validations = buildMap()
        field_validations?.forEach { v ->
            val name = getText(v.nested_field_validation_name())!!
            val value = first(getText(v.nested_field_validation_value()), "")
            validations.with(name, mapOf("name" to name, "value" to value))
        }
        return validations
    }

    override fun exitNested_field(ctx: ZdlParser.Nested_fieldContext) { currentStack.removeLast() }

    override fun enterEnum(ctx: ZdlParser.EnumContext) {
        val name = getText(ctx.enum_name())!!
        val jd = javadoc(ctx.javadoc())
        currentStack.addLast(
            buildMap()
            .with("name", name)
            .with("type", "enums")
            .with("className", camelCase(name))
            .with("javadoc", jd)
            .with("comment", jd))
        model.appendTo("enums", name, currentStack.last())

        val entityLocation = "enums.$name"
        model.setLocation(entityLocation, getLocations(ctx))
        model.setLocation("$entityLocation.name", getLocations(ctx.enum_name()))
        model.setLocation("$entityLocation.body", getLocations(ctx.enum_body()))
    }

    override fun exitEnum(ctx: ZdlParser.EnumContext) { currentStack.removeLast() }

    override fun enterEnum_value(ctx: ZdlParser.Enum_valueContext) {
        val name = getText(ctx.enum_value_name())!!
        val jd = javadoc(first(ctx.javadoc(), ctx.suffix_javadoc()))
        val value = getText(ctx.enum_value_value())
        if (value != null) {
            currentStack.last().with("hasValue", true)
        }
        currentStack.last().appendTo("values", name, buildMap()
            .with("name", name)
            .with("javadoc", jd)
            .with("comment", jd)
            .with("value", value)
        )
    }

    override fun enterRelationship(ctx: ZdlParser.RelationshipContext) {
        val parent = ctx.getParent() as ZdlParser.RelationshipsContext
        val relationshipType = parent.relationship_type().text
        val relationshipName = removeJavadoc(relationshipType + "_" + relationshipDescription(ctx.relationship_from().relationship_definition()) + "_" + relationshipDescription(ctx.relationship_to().relationship_definition()))

        val relationship = buildMap().with("type", relationshipType).with("name", relationshipName)
        val location = "relationships.$relationshipName"
        model.setLocation(location, getLocations(ctx))

        if (ctx.relationship_from().relationship_definition() != null) {
            val from = getText(ctx.relationship_from().relationship_definition().relationship_entity_name())
            val fromField = getText(ctx.relationship_from().relationship_definition().relationship_field_name())
            val commentInFrom = javadoc(ctx.relationship_from().javadoc())
            val fromOptions = relationshipOptions(ctx.relationship_from().annotations().option())
            val isInjectedFieldInFromRequired = isRequired(ctx.relationship_from().relationship_definition())
            val injectedFieldInFromDescription = getText(ctx.relationship_from().relationship_definition().relationship_description_field())
            val relationshipValidations = relationshipValidations(ctx.relationship_from().relationship_definition())
            model.setLocation("$location.from.entity", getLocations(ctx.relationship_from().relationship_definition().relationship_entity_name()))
            model.setLocation("$location.from.field", getLocations(ctx.relationship_from().relationship_definition().relationship_field_name()))
            ctx.relationship_from().relationship_definition().relationship_field_validations()?.let { rf ->
                model.setLocation("$location.from.validations", getLocations(rf))
                rf.relationship_field_min()?.let { model.setLocation("$location.from.validations.min", getLocations(it)) }
                rf.relationship_field_max()?.let { model.setLocation("$location.from.validations.max", getLocations(it)) }
            }
            relationship.with("from", from)
                .with("commentInFrom", commentInFrom)
                .with("injectedFieldInFrom", fromField)
                .with("fromOptions", fromOptions)
                .with("fromValidations", relationshipValidations)
                .with("injectedFieldInFromDescription", injectedFieldInFromDescription)
                .with("isInjectedFieldInFromRequired", isInjectedFieldInFromRequired)
        }

        if (ctx.relationship_to() != null && ctx.relationship_to().relationship_definition() != null) {
            val to = getText(ctx.relationship_to().relationship_definition().relationship_entity_name())
            val toField = getText(ctx.relationship_to().relationship_definition().relationship_field_name())
            val commentInTo = javadoc(ctx.relationship_to().javadoc())
            val toOptions = relationshipOptions(ctx.relationship_to().annotations().option())
            val isInjectedFieldInToRequired = isRequired(ctx.relationship_to().relationship_definition())
            val injectedFieldInToDescription = getText(ctx.relationship_to().relationship_definition().relationship_description_field())
            val relationshipValidations = relationshipValidations(ctx.relationship_to().relationship_definition())
            model.setLocation("$location.to.entity", getLocations(ctx.relationship_to().relationship_definition().relationship_entity_name()))
            model.setLocation("$location.to.field", getLocations(ctx.relationship_to().relationship_definition().relationship_field_name()))
            ctx.relationship_to().relationship_definition().relationship_field_validations()?.let { rf ->
                model.setLocation("$location.to.validations", getLocations(rf))
                rf.relationship_field_min()?.let { model.setLocation("$location.to.validations.min", getLocations(it)) }
                rf.relationship_field_max()?.let { model.setLocation("$location.to.validations.max", getLocations(it)) }
            }
            relationship.with("to", to)
                .with("commentInTo", commentInTo)
                .with("injectedFieldInTo", toField)
                .with("toOptions", toOptions)
                .with("toValidations", relationshipValidations)
                .with("injectedFieldInToDescription", injectedFieldInToDescription)
                .with("isInjectedFieldInToRequired", isInjectedFieldInToRequired)
        }

        model.getRelationships().appendTo(relationshipType, relationshipName, relationship)
    }

    private fun isRequired(relationshipDefinitionContext: ZdlParser.Relationship_definitionContext): Boolean {
        val rf = relationshipDefinitionContext.relationship_field_validations()
        return rf?.relationship_field_required() != null
    }

    private fun relationshipValidations(relationshipDefinitionContext: ZdlParser.Relationship_definitionContext): Any? {
        val validations = buildMap()
        relationshipDefinitionContext.relationship_field_validations()?.let { rf ->
            rf.relationship_field_required()?.let {
                val name = "required"
                validations.with(name, mapOf("name" to name, "value" to true))
            }
            rf.relationship_field_min()?.let { min ->
                val name = "minlength"
                val value = getText(min.relationship_field_value())
                validations.with(name, mapOf("name" to name, "value" to value))
            }
            rf.relationship_field_max()?.let { max ->
                val name = "maxlength"
                val value = getText(max.relationship_field_value())
                validations.with(name, mapOf("name" to name, "value" to value))
            }
        }
        return validations
    }

    private fun removeJavadoc(text: String): String {
        val regex = "/\\*\\*.+?\\*/"
        var t = text.replace("\r\n", "").replace("\n", "")
        return t.replace(Regex(regex), "")
    }

    private fun relationshipDescription(ctx: ZdlParser.Relationship_definitionContext?): String {
        var description = ""
        if (ctx != null) {
            description += getText(ctx.relationship_entity_name())
            if (ctx.relationship_field_name() != null) {
                description += "{" + getText(ctx.relationship_field_name()) + "}"
            }
        }
        return description
    }

    private fun relationshipOptions(options: List<ZdlParser.OptionContext>): Map<String, Any?> {
        return options.associate { o ->
            getText(o.option_name())!!.replace("@", "") to getOptionValue(o.option_value())
        }
    }

    override fun enterService_legacy(ctx: ZdlParser.Service_legacyContext) {
        val serviceName = ctx.ID().text
        val serviceJavadoc = "Legacy service"
        val serviceAggregates = getArray(ctx.service_aggregates(), ",")
        currentStack.addLast(
            buildMap()
            .with("name", serviceName)
            .with("isLegacy", true)
            .with("className", camelCase(serviceName!!))
            .with("javadoc", serviceJavadoc)
            .with("aggregates", serviceAggregates)
            .with("options", mapOf("rest" to true))
            .with("methods", createCRUDMethods(serviceName, serviceAggregates!!))
        )
        model.appendTo("services", serviceName, currentStack.last())
    }

    override fun exitService_legacy(ctx: ZdlParser.Service_legacyContext) { currentStack.removeLast() }

    override fun enterAggregate(ctx: ZdlParser.AggregateContext) {
        val aggregateName = getText(ctx.aggregate_name())
        val jd = javadoc(ctx.javadoc())
        val aggregateRoot = getText(ctx.aggregate_root())
        currentStack.addLast(
            buildMap()
            .with("name", aggregateName)
            .with("type", "aggregates")
            .with("className", camelCase(aggregateName!!))
            .with("javadoc", jd)
            .with("aggregateRoot", aggregateRoot)
            .with("commands", buildMap())
        )
        model.appendTo("aggregates", aggregateName, currentStack.last())

        val name = currentStack.last()["name"]
        val location = "aggregates.$name"
        model.setLocation(location, getLocations(ctx))
        model.setLocation("$location.name", getLocations(ctx.aggregate_name()))
        model.setLocation("$location.aggregateRoot", getLocations(ctx.aggregate_root()))
    }

    override fun exitAggregate(ctx: ZdlParser.AggregateContext) { currentStack.removeLast() }

    override fun enterAggregate_command(ctx: ZdlParser.Aggregate_commandContext) {
        val aggregateName = getText((ctx.getParent() as ZdlParser.AggregateContext).aggregate_name())!!
        val commandName = getText(ctx.aggregate_command_name())!!
        val location = "aggregates.$aggregateName.commands.$commandName"
        val parameter = ctx.aggregate_command_parameter()?.ID()?.text
        val parameterIsOptional = ctx.aggregate_command_parameter()?.OPTIONAL() != null
        val withEvents = getServiceMethodEvents(location, ctx.with_events())
        val jd = javadoc(first(ctx.javadoc(), ctx.suffix_javadoc()))

        val method = buildMap()
            .with("name", commandName)
            .with("aggregateName", aggregateName)
            .with("parameter", parameter)
            .with("parameterIsOptional", parameterIsOptional)
            .with("withEvents", withEvents)
            .with("javadoc", jd)
        currentStack.last().appendTo("commands", commandName, method)
        currentStack.addLast(method)

        model.setLocation(location, getLocations(ctx))
        model.setLocation("$location.name", getLocations(ctx.aggregate_command_name()))
        model.setLocation("$location.parameter", getLocations(ctx.aggregate_command_parameter()))
    }

    override fun exitAggregate_command(ctx: ZdlParser.Aggregate_commandContext) { currentStack.removeLast() }

    override fun enterService(ctx: ZdlParser.ServiceContext) {
        val serviceName = getText(ctx.service_name())!!
        val serviceJavadoc = javadoc(ctx.javadoc())
        val serviceAggregates = getArray(ctx.service_aggregates(), ",")
        currentStack.addLast(
            buildMap()
            .with("name", serviceName)
            .with("className", camelCase(serviceName))
            .with("javadoc", serviceJavadoc)
            .with("aggregates", serviceAggregates)
            .with("methods", buildMap())
        )
        model.appendTo("services", serviceName, currentStack.last())

        val name = currentStack.last()["name"]
        val location = "services.$name"
        model.setLocation(location, getLocations(ctx))
        model.setLocation("$location.name", getLocations(ctx.service_name()))
        model.setLocation("$location.aggregates", getLocations(ctx.service_aggregates()))
    }

    override fun exitService(ctx: ZdlParser.ServiceContext) { currentStack.removeLast() }

    override fun enterService_method(ctx: ZdlParser.Service_methodContext) {
        val serviceName = getText((ctx.getParent() as ZdlParser.ServiceContext).service_name())!!
        val methodName = getText(ctx.service_method_name())!!
        val location = "services.$serviceName.methods.$methodName"
        val naturalId = if (ctx.service_method_parameter_natural() != null) true else null
        val methodParamId = if (ctx.service_method_parameter_id() != null) "id" else null
        val methodParamIdIsOptional = ctx.service_method_parameter_id()?.OPTIONAL() != null
        val methodParameter = ctx.service_method_parameter()?.ID()?.text
        val methodParameterIsOptional = ctx.service_method_parameter()?.OPTIONAL() != null
        val returnType = ctx.service_method_return()?.ID()?.text
        val returnTypeIsArray = ctx.service_method_return()?.ARRAY() != null
        val returnTypeIsOptional = ctx.service_method_return()?.OPTIONAL() != null
        val withEvents = getServiceMethodEvents(location, ctx.with_events())
        val jd = javadoc(first(ctx.javadoc(), ctx.suffix_javadoc()))

        val method = buildMap()
            .with("name", methodName)
            .with("serviceName", serviceName)
            .with("naturalId", naturalId)
            .with("paramId", methodParamId)
            .with("paramIdIsOptional", methodParamIdIsOptional)
            .with("parameter", methodParameter)
            .with("parameterIsOptional", methodParameterIsOptional)
            .with("returnType", returnType)
            .with("returnTypeIsArray", returnTypeIsArray)
            .with("returnTypeIsOptional", returnTypeIsOptional)
            .with("withEvents", withEvents)
            .with("javadoc", jd)
        currentStack.last().appendTo("methods", methodName, method)
        currentStack.addLast(method)

        model.setLocation(location, getLocations(ctx))
        model.setLocation("$location.name", getLocations(ctx.service_method_name()))
        model.setLocation("$location.parameter", getLocations(ctx.service_method_parameter()))
        model.setLocation("$location.returnType", getLocations(ctx.service_method_return()))
    }

    override fun exitService_method(ctx: ZdlParser.Service_methodContext) { currentStack.removeLast() }

    private fun getServiceMethodEvents(location: String, ctx: ZdlParser.With_eventsContext?): List<Any> {
        model.setLocation("$location.withEvents", getLocations(ctx))
        val events = mutableListOf<Any>()
        ctx?.with_events_events()?.forEachIndexed { i, event ->
            event.with_events_event()?.let { e ->
                val eventName = getText(e)!!
                events.add(eventName)
                model.setLocation("$location.withEvents.$i", getLocations(e))
                model.setLocation("$location.withEvents.$eventName", getLocations(e))
            }
            event.with_events_events_or()?.let { ors ->
                val orEvents = ors.with_events_event().map { it.text }
                events.add(orEvents)
                ors.with_events_event().forEachIndexed { j, eventContext ->
                    model.setLocation("$location.withEvents.$i.$j", getLocations(eventContext))
                    model.setLocation("$location.withEvents.${getText(eventContext)}", getLocations(eventContext))
                }
            }
        }
        return events
    }

    override fun enterEvent(ctx: ZdlParser.EventContext) {
        val name = ctx.event_name().text
        val jd = javadoc(ctx.javadoc())
        val kebab = kebabCase(name)
        currentStack.addLast(
            buildMap()
            .with("name", name)
            .with("className", camelCase(name)!!)
            .with("type", "events")
            .with("kebabCase", kebab)
            .with("javadoc", jd)
            .with("fields", buildMap())
        )
        model.appendTo("events", name, currentStack.last())
        currentCollection = "events"
    }

    override fun exitEvent(ctx: ZdlParser.EventContext) { currentStack.removeLast() }

    override fun enterInput(ctx: ZdlParser.InputContext) {
        val name = ctx.input_name().text
        val jd = javadoc(ctx.javadoc())
        currentStack.addLast(processEntity(name, jd, null).with("type", "inputs"))
        model.appendTo("inputs", name, currentStack.last())
        currentCollection = "inputs"
    }

    override fun exitInput(ctx: ZdlParser.InputContext) { currentStack.removeLast() }

    override fun enterOutput(ctx: ZdlParser.OutputContext) {
        val name = ctx.output_name().text
        val jd = javadoc(ctx.javadoc())
        currentStack.addLast(processEntity(name, jd, null).with("type", "outputs"))
        model.appendTo("outputs", name, currentStack.last())
        currentCollection = "outputs"
    }

    override fun exitOutput(ctx: ZdlParser.OutputContext) { currentStack.removeLast() }

    override fun exitEveryRule(ctx: ParserRuleContext) { super.exitEveryRule(ctx) }
    override fun visitTerminal(node: TerminalNode) { super.visitTerminal(node) }
    override fun visitErrorNode(node: ErrorNode) { super.visitErrorNode(node) }
}

