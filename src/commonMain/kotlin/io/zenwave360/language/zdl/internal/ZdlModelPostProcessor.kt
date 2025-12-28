package io.zenwave360.language.zdl.internal

import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.zdl.ZdlModel

class ZdlModelPostProcessor {
    companion object {
        fun postProcess(model: ZdlModel): ZdlModel {
            val aggregates = model.getAggregates()
            val entities = model.getEntities()
            val inputs = model.getInputs()
            val outputs = model.getOutputs()
            val enums = model.getEnums()
            val events = model.getEvents()

            val fields = JSONPath.get(model, "$..fields[*]", listOf<MutableMap<String, Any?>>())
            for (field in fields) {
                val type = field["type"]
                if (type != null) {
                    field["isComplexType"] = false
                    if (entities.containsKey(type)) {
                        field["isEntity"] = true
                        field["isComplexType"] = true
                    }
                    if (enums.containsKey(type)) {
                        field["isEnum"] = true
                        field["isComplexType"] = true
                    }
                    if (inputs.containsKey(type)) {
                        field["isInput"] = true
                        field["isComplexType"] = true
                    }
                    if (outputs.containsKey(type)) {
                        field["isOutput"] = true
                        field["isComplexType"] = true
                    }
                    if (events.containsKey(type)) {
                        field["isEvent"] = true
                        field["isComplexType"] = true
                    }
                }
            }

            val allEntitiesAndEnums = mutableMapOf<String, Any?>()
            allEntitiesAndEnums.putAll(aggregates)
            allEntitiesAndEnums.putAll(entities)
            allEntitiesAndEnums.putAll(enums)
            allEntitiesAndEnums.putAll(inputs)
            allEntitiesAndEnums.putAll(outputs)
            model["allEntitiesAndEnums"] = allEntitiesAndEnums

            return model
        }
    }
}

