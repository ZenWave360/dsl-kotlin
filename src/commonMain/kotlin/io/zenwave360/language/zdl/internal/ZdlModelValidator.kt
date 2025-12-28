package io.zenwave360.language.zdl.internal

import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.zdl.ZdlModel
import kotlin.collections.iterator

class ZdlModelValidator {

    private val API_ROLES = listOf("provider", "client")

    private var standardFieldTypes: List<String> = ZdlParser.Companion.STANDARD_FIELD_TYPES
    private var extraFieldTypes: List<String> = emptyList()

    fun withStandardFieldTypes(standardFieldTypes: List<String>): ZdlModelValidator {
        this.standardFieldTypes = standardFieldTypes
        return this
    }

    fun withExtraFieldTypes(extraFieldTypes: List<String>): ZdlModelValidator {
        this.extraFieldTypes = extraFieldTypes
        return this
    }

    fun validate(model: ZdlModel): ZdlModel {
        model.clearProblems()
        validateApis(model)
        validateEntitiesFields(model, "entities")
        validateEntitiesFields(model, "inputs")
        validateEntitiesFields(model, "outputs")
        validateEntitiesFields(model, "events")
        validateAggregates(model)
        validateServices(model)
        validateRelationships(model)
        return model
    }

    private fun validateApis(model: ZdlModel) {
        @Suppress("UNCHECKED_CAST")
        val apis = JSONPath.get(model, "$.apis[*]", listOf<Map<String, Any?>>()) as List<Map<String, Any?>>
        for (api in apis) {
            val role = api["role"] as? String
            val name = api["name"] as? String
            if (role == null || !API_ROLES.contains(role)) {
                model.addProblem(path("apis", name ?: "", "role"), role, "%s is not a valid API role [provider|client]")
            }
        }
    }

    private fun validateRelationships(model: ZdlModel) {
        @Suppress("UNCHECKED_CAST")
        val relationships = JSONPath.get(model, "$.relationships[*][*]", listOf<Map<String, Any?>>()) as List<Map<String, Any?>>
        for (relationship in relationships) {
            val type = JSONPath.get(relationship, "$.type") as? String
            val name = JSONPath.get(relationship, "$.name") as? String
            val from = JSONPath.get(relationship, "$.from") as? String
            val to = JSONPath.get(relationship, "$.to") as? String
            val injectedFieldInFrom = JSONPath.get(relationship, "$.injectedFieldInFrom") as? String
            val injectedFieldInTo = JSONPath.get(relationship, "$.injectedFieldInTo") as? String
            if (!isEntity(model, from)) {
                model.addProblem(path("relationships", name ?: "", "from", "entity"), from ?: "", "%s is not a valid entity")
            }
            if (!isEntity(model, to)) {
                model.addProblem(path("relationships", name ?: "", "to", "entity"), to ?: "", "%s is not a valid entity")
            }
            // TODO: validate injectedFieldInFrom and injectedFieldInTo
        }
    }

    private fun validateEntitiesFields(model: ZdlModel, type: String) {
        @Suppress("UNCHECKED_CAST")
        val entities = JSONPath.get(model, "$.${type}", mapOf<String, Any?>()) as Map<String, Any?>
        for ((_, value) in entities) {
            @Suppress("UNCHECKED_CAST")
            validateFields(model, value as Map<String, Any?>)
        }
    }

    private fun validateFields(model: ZdlModel, entity: Map<String, Any?>) {
        val entityType = JSONPath.get(entity, "$.type") as? String
        val entityName = JSONPath.get(entity, "$.name") as? String
        @Suppress("UNCHECKED_CAST")
        val fields = JSONPath.get(entity, "$.fields", mapOf<String, Map<String, Any?>>()) as Map<String, Map<String, Any?>>
        for (field in fields.values) {
            val fieldName = JSONPath.get(field, "$.name") as? String
            val fieldType = JSONPath.get(field, "$.type") as? String
            validateField(model, entityType, entityName, fieldName, fieldType)
        }
    }

    private fun validateField(model: ZdlModel, entityType: String?, entityName: String?, fieldName: String?, fieldType: String?) {
        if (entityType == "entities") {
            if (!(isStandardFieldType(fieldType) || isEntityOrEnum(model, fieldType))) {
                model.addProblem(path("entities", entityName ?: "", "fields", fieldName ?: "", "type"), fieldType ?: "", "%s is not a valid type")
            }
        } else if (entityType == "inputs") {
            if (!(isStandardFieldType(fieldType) || isEntityOrEnum(model, fieldType) || isInput(model, fieldType))) {
                model.addProblem(path("inputs", entityName ?: "", "fields", fieldName ?: "", "type"), fieldType ?: "", "%s is not a valid type")
            }
        } else if (entityType == "outputs") {
            if (!(isStandardFieldType(fieldType) || isEntityOrEnum(model, fieldType) || isInput(model, fieldType) || isOutput(model, fieldType))) {
                model.addProblem(path("outputs", entityName ?: "", "fields", fieldName ?: "", "type"), fieldType ?: "", "%s is not a valid type")
            }
        } else if (entityType == "events") {
            if (!(isStandardFieldType(fieldType) || isEntityOrEnum(model, fieldType) || isEvent(model, fieldType))) {
                model.addProblem(path("events", entityName ?: "", "fields", fieldName ?: "", "type"), fieldType ?: "", "%s is not a valid type")
            }
        }
    }

    private fun validateAggregates(model: ZdlModel): List<Map<String, Any?>>? {
        @Suppress("UNCHECKED_CAST")
        val services = JSONPath.get(model, "$.aggregates", mapOf<String, Any?>()) as Map<String, Any?>
        for ((key, value) in services) {
            val aggregateRoot = JSONPath.get(value, "$.aggregateRoot") as? String
            if (aggregateRoot == null || !isEntity(model, aggregateRoot)) {
                model.addProblem(path("aggregates", key, "aggregateRoot"), aggregateRoot ?: "", "%s is not an entity")
            }

            @Suppress("UNCHECKED_CAST")
            val methods = JSONPath.get(value, "$.commands[*]", listOf<Map<String, Any?>>()) as List<Map<String, Any?>>
            for (method in methods) {
                val methodName = JSONPath.get(method, "$.name") as? String
                val parameter = JSONPath.get(method, "$.parameter") as? String
                if (parameter != null && !isEntity(model, parameter) && !isInput(model, parameter)) {
                    model.addProblem(path("aggregates", key, "commands", methodName ?: "", "parameter"), parameter, "%s is not an entity or input")
                }
                @Suppress("UNCHECKED_CAST")
                val withEvents = (method["withEvents"] ?: emptyList<Any>()) as List<Any>
                for ((i, event) in withEvents.withIndex()) {
                    if (event is List<*>) {
                        for ((j, inner) in event.withIndex()) {
                            val innerEvent = inner as? String
                            if (!isEvent(model, innerEvent)) {
                                model.addProblem(path("aggregates", key, "commands", methodName ?: "", "withEvents", "$i", "$j"), innerEvent ?: "", "%s is not an event")
                            }
                        }
                    } else {
                        val e = event as? String
                        if (!isEvent(model, e)) {
                            model.addProblem(path("aggregates", key, "commands", methodName ?: "", "withEvents", "$i"), e ?: "", "%s is not an event")
                        }
                    }
                }
            }
        }
        return null
    }

    private fun validateServices(model: ZdlModel): List<Map<String, Any?>>? {
        @Suppress("UNCHECKED_CAST")
        val services = JSONPath.get(model, "$.services", mapOf<String, Any?>()) as Map<String, Any?>
        for ((key, value) in services) {
            @Suppress("UNCHECKED_CAST")
            val aggregates = JSONPath.get(value, "$.aggregates", listOf<String>()) as List<String>
            for (aggregate in aggregates) {
                if (aggregate.isNotEmpty() && !isAggregate(model, aggregate)) {
                    model.addProblem(path("services", key, "aggregates"), aggregate, "%s is not an aggregate")
                }
            }

            @Suppress("UNCHECKED_CAST")
            val methods = JSONPath.get(value, "$.methods[*]", listOf<Map<String, Any?>>()) as List<Map<String, Any?>>
            for (method in methods) {
                val methodName = JSONPath.get(method, "$.name") as? String
                val parameter = JSONPath.get(method, "$.parameter") as? String
                if (parameter != null && !isEntity(model, parameter) && !isInput(model, parameter)) {
                    model.addProblem(path("services", key, "methods", methodName ?: "", "parameter"), parameter, "%s is not an entity or input")
                }
                val returnType = JSONPath.get(method, "$.returnType") as? String
                if (returnType != null && !isEntity(model, returnType) && !isInput(model, returnType) && !isOutput(model, returnType)) {
                    model.addProblem(path("services", key, "methods", methodName ?: "", "returnType"), returnType, "%s is not an entity, input or output")
                }
                @Suppress("UNCHECKED_CAST")
                val withEvents = (method["withEvents"] ?: emptyList<Any>()) as List<Any>
                for ((i, event) in withEvents.withIndex()) {
                    if (event is List<*>) {
                        for ((j, inner) in event.withIndex()) {
                            val innerEvent = inner as? String
                            if (!isEvent(model, innerEvent)) {
                                model.addProblem(path("services", key, "methods", methodName ?: "", "withEvents", "$i", "$j"), innerEvent ?: "", "%s is not an event")
                            }
                        }
                    } else {
                        val e = event as? String
                        if (!isEvent(model, e)) {
                            model.addProblem(path("services", key, "methods", methodName ?: "", "withEvents", "$i"), e ?: "", "%s is not an event")
                        }
                    }
                }
            }
        }
        return null
    }

    private fun path(vararg path: String): String = path.joinToString(".")

    private fun isStandardFieldType(fieldType: String?): Boolean =
        fieldType != null && (standardFieldTypes.contains(fieldType) || extraFieldTypes.contains(fieldType))

    private fun isEntity(model: ZdlModel, entityName: String?): Boolean =
        JSONPath.get<String>(model, "$.entities.$entityName") != null

    private fun isEnum(model: ZdlModel, entityName: String?): Boolean =
        JSONPath.get<String>(model, "$.enums.$entityName") != null

    private fun isInput(model: ZdlModel, entityName: String?): Boolean =
        JSONPath.get<String>(model, "$.inputs.$entityName") != null

    private fun isOutput(model: ZdlModel, entityName: String?): Boolean =
        JSONPath.get<String>(model, "$.outputs.$entityName") != null

    private fun isEvent(model: ZdlModel, entityName: String?): Boolean =
        JSONPath.get<String>(model, "$.events.$entityName") != null

    private fun isEntityOrEnum(model: ZdlModel, entityName: String?): Boolean =
        isEntity(model, entityName) || isEnum(model, entityName)

    private fun isAggregate(model: ZdlModel, entityName: String?): Boolean =
        JSONPath.get<String>(model, "$.aggregates.$entityName") != null ||
                (JSONPath.get(model, "$.entities.$entityName.options.aggregate", false) as Boolean)
}

