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
        validateEntityLifecycles(model)
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

            // Resolve enum type for lifecycle state validation (may be null if lifecycle absent or invalid)
            val lifecycleEnumValues = validateLifecycle(model, key, value, aggregateRoot)

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
                // Validate state transitions only when present (transitions are optional per command)
                if (lifecycleEnumValues != null) {
                    validateCommandTransition(model, key, methodName, method, lifecycleEnumValues)
                }
            }
        }
        return null
    }

    /**
     * Validates the aggregate lifecycle declaration:
     * - statusField must exist as a field on the aggregate root entity
     * - the field's type must be a known enum
     * - initialState must be a valid value of that enum
     *
     * Returns the set of valid enum value names when lifecycle is present and fully valid, null otherwise.
     */
    private fun validateLifecycle(
        model: ZdlModel,
        aggregateKey: String,
        aggregate: Any?,
        aggregateRoot: String?
    ): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        val lifecycle = JSONPath.get(aggregate, "$.lifecycle") as? Map<String, Any?> ?: return null

        val statusField = lifecycle["statusField"] as? String
        val initialState = lifecycle["initialState"] as? String

        // Validate statusField exists on the aggregate root entity
        val fieldType = if (aggregateRoot != null && statusField != null)
            JSONPath.get(model, "$.entities.$aggregateRoot.fields.$statusField.type") as? String
        else null

        if (statusField == null || fieldType == null) {
            model.addProblem(
                path("aggregates", aggregateKey, "lifecycle", "statusField"),
                statusField ?: "",
                "%s is not a field of the aggregate root entity"
            )
            return null
        }

        // Validate the field type is a known enum
        if (!isEnum(model, fieldType)) {
            model.addProblem(
                path("aggregates", aggregateKey, "lifecycle", "statusField"),
                fieldType,
                "field type %s is not an enum"
            )
            return null
        }

        @Suppress("UNCHECKED_CAST")
        val enumValues = (JSONPath.get(model, "$.enums.$fieldType.values", mapOf<String, Any?>()) as? Map<String, Any?>)?.keys
            ?: emptySet()

        // Validate initialState is a valid enum value
        if (initialState == null || !enumValues.contains(initialState)) {
            model.addProblem(
                path("aggregates", aggregateKey, "lifecycle", "initialState"),
                initialState ?: "",
                "%s is not a valid value of enum $fieldType"
            )
        }

        return enumValues
    }

    /**
     * Validates from/to state transitions on a command when they are present.
     * Transitions are optional — commands without from/to are not reported as errors.
     */
    private fun validateCommandTransition(
        model: ZdlModel,
        aggregateKey: String,
        methodName: String?,
        method: Map<String, Any?>,
        enumValues: Set<String>
    ) {
        @Suppress("UNCHECKED_CAST")
        val fromStates = method["from"] as? List<String>
        val toState = method["to"] as? String

        fromStates?.forEachIndexed { i, state ->
            if (!enumValues.contains(state)) {
                model.addProblem(
                    path("aggregates", aggregateKey, "commands", methodName ?: "", "from", "$i"),
                    state,
                    "%s is not a valid state value"
                )
            }
        }

        if (toState != null && !enumValues.contains(toState)) {
            model.addProblem(
                path("aggregates", aggregateKey, "commands", methodName ?: "", "to"),
                toState,
                "%s is not a valid state value"
            )
        }
    }

    /**
     * Validates @lifecycle annotations on entities:
     * - statusField must name a real field on the entity
     * - that field's type must be a known enum
     * - initialState must be a valid value of that enum
     */
    private fun validateEntityLifecycles(model: ZdlModel) {
        @Suppress("UNCHECKED_CAST")
        val entities = JSONPath.get(model, "$.entities", mapOf<String, Any?>()) as Map<String, Any?>
        for ((name, value) in entities) {
            @Suppress("UNCHECKED_CAST")
            val lifecycle = ((value as? Map<String, Any?>)?.get("options") as? Map<String, Any?>)
                ?.get("lifecycle") as? Map<String, Any?> ?: continue

            val statusField = lifecycle["statusField"] as? String
            val initialState = lifecycle["initialState"] as? String

            val fieldType = if (statusField != null)
                JSONPath.get(model, "$.entities.$name.fields.$statusField.type") as? String
            else null

            if (statusField == null || fieldType == null) {
                model.addProblem(path("entities", name, "options", "lifecycle", "statusField"),
                    statusField ?: "", "%s is not a field of this entity")
                continue
            }

            if (!isEnum(model, fieldType)) {
                model.addProblem(path("entities", name, "options", "lifecycle", "statusField"),
                    fieldType, "field type %s is not an enum")
                continue
            }

            @Suppress("UNCHECKED_CAST")
            val enumValues = (JSONPath.get(model, "$.enums.$fieldType.values",
                mapOf<String, Any?>()) as? Map<String, Any?>)?.keys ?: emptySet()

            if (initialState == null || !enumValues.contains(initialState)) {
                model.addProblem(path("entities", name, "options", "lifecycle", "initialState"),
                    initialState ?: "", "%s is not a valid value of enum $fieldType")
            }
        }
    }

    /**
     * Returns the set of valid enum values for an entity's @lifecycle annotation,
     * or null if the entity has no lifecycle or the lifecycle is invalid.
     */
    private fun getEntityLifecycleEnumValues(model: ZdlModel, entityName: String?): Set<String>? {
        if (entityName == null) return null
        @Suppress("UNCHECKED_CAST")
        val lifecycle = ((JSONPath.get(model, "$.entities.$entityName") as? Map<String, Any?>)
            ?.get("options") as? Map<String, Any?>)?.get("lifecycle") as? Map<String, Any?> ?: return null
        val statusField = lifecycle["statusField"] as? String ?: return null
        val fieldType = JSONPath.get(model, "$.entities.$entityName.fields.$statusField.type") as? String ?: return null
        if (!isEnum(model, fieldType)) return null
        @Suppress("UNCHECKED_CAST")
        return (JSONPath.get(model, "$.enums.$fieldType.values",
            mapOf<String, Any?>()) as? Map<String, Any?>)?.keys
    }

    private fun validateServices(model: ZdlModel): List<Map<String, Any?>>? {
        @Suppress("UNCHECKED_CAST")
        val services = JSONPath.get(model, "$.services", mapOf<String, Any?>()) as Map<String, Any?>
        for ((key, value) in services) {
            @Suppress("UNCHECKED_CAST")
            val serviceAggregates = JSONPath.get(value, "$.aggregates", listOf<String>()) as List<String>
            for (aggregate in serviceAggregates) {
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
                // Validate state transitions when present (optional per method)
                val hasTransition = method["from"] != null || method["to"] != null
                if (hasTransition) {
                    validateServiceMethodTransition(model, key, methodName, method, serviceAggregates)
                }
            }
        }
        return null
    }

    /**
     * Validates from/to state transitions on a service method.
     * Rules:
     *  1. Method must have an id parameter.
     *  2. Target entity is resolved: if service has 1 aggregate → use it;
     *     if multiple → returnType or parameter must be in the aggregate list.
     *  3. Resolved entity must carry a @lifecycle annotation.
     *  4. from/to state values must be valid enum values of the lifecycle status field.
     */
    private fun validateServiceMethodTransition(
        model: ZdlModel,
        serviceKey: String,
        methodName: String?,
        method: Map<String, Any?>,
        serviceAggregates: List<String>
    ) {
        val mPath = path("services", serviceKey, "methods", methodName ?: "")

        // Rule 1: id parameter is required for transitions
        val paramId = method["paramId"] as? String
        if (paramId != "id") {
            model.addProblem("$mPath.from", null, "state transitions require an id parameter")
            return
        }

        // Rule 2: resolve target entity
        val targetEntity: String? = when {
            serviceAggregates.size == 1 -> serviceAggregates[0]
            else -> {
                val returnType = method["returnType"] as? String
                val parameter = method["parameter"] as? String
                // prefer returnType, then parameter — whichever appears in the service's aggregate list
                when {
                    returnType != null && serviceAggregates.contains(returnType) -> returnType
                    parameter != null && serviceAggregates.contains(parameter) -> parameter
                    else -> null
                }
            }
        }

        if (targetEntity == null) {
            model.addProblem("$mPath.from", null,
                "state transitions require returnType or parameter to match one of the service aggregates")
            return
        }

        // Rule 3: entity must have @lifecycle
        val enumValues = getEntityLifecycleEnumValues(model, targetEntity)
        if (enumValues == null) {
            model.addProblem("$mPath.from", targetEntity,
                "entity %s does not have a @lifecycle annotation")
            return
        }

        // Rule 4: validate individual from/to states
        @Suppress("UNCHECKED_CAST")
        val fromStates = method["from"] as? List<String>
        val toState = method["to"] as? String

        fromStates?.forEachIndexed { i, state ->
            if (!enumValues.contains(state)) {
                model.addProblem(path("services", serviceKey, "commands", methodName ?: "", "from", "$i"),
                    state, "%s is not a valid state value")
            }
        }
        if (toState != null && !enumValues.contains(toState)) {
            model.addProblem(path("services", serviceKey, "commands", methodName ?: "", "to"),
                toState, "%s is not a valid state value")
        }
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

