package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.zfl.ZflModel
import io.zenwave360.language.source.SourceRef
import io.zenwave360.language.utils.JSONPath

class ZflSemanticAnalyzer {

    fun analyze(model: ZflModel): ZflSemanticModel {
        val actors = mutableMapOf<String, ZflActor>()
        val systems = mutableMapOf<String, ZflSystem>()
        val flows = mutableListOf<ZflFlow>()
        val commandByName = mutableMapOf<String, ZflCommand>()

        model.getSystems().values.forEach { systemData ->
            val systemModel = systemData.asMapOrReturn { return@forEach }
            val systemName = systemModel.getString("name")

            systems[systemName] = ZflSystem(systemName)

            systemModel.getMap("services").values.forEach { serviceData ->
                val service = serviceData.asMapOrReturn { return@forEach }
                val serviceName = service.getString("name")

                service.getList("commands").forEach { commandName ->
                    commandByName["$systemName.$serviceName.$commandName"] = ZflCommand(
                        name = commandName,
                        system = systemName,
                        service = serviceName,
                        actor = null,
                        sourceRef = sourceRefOf("todo", commandName)
                    )
                }
            }
        }

        model.getFlows().values.forEach { flowData ->
            val flowModel = flowData.asMapOrReturn { return@forEach }
            val flowName = flowModel.getString("name")

            val starts = mutableListOf<ZflStart>()

            // 1. Starts
            flowModel.getMap("starts").values.forEach { startData ->
                val start = startData.asMapOrReturn { return@forEach }
                val startName = start.getString("name")
                val actor = JSONPath.get<String>(start, "options.actor")
                val timer = JSONPath.get<String>(start, "options.timer")
                val system = JSONPath.get<String>(start, "options.system")
                starts += ZflStart(
                    description = start.getString("javadoc"),
                    name = startName,
                    actor = actor,
                    timer = timer,
                    system = system,
                    sourceRef = sourceRefOf(flowName, startName)
                )

                // Starts → actors
                actor?.let { actorName ->
                    actors.getOrPut(actorName) {
                        ZflActor(actorName, sourceRefOf(flowName, startName))
                    }
                }
            }

            // 2. Events + policies + whens from whens
            val events = mutableMapOf<String, ZflEvent>()
            val policies = mutableListOf<ZflPolicy>()

            flowModel.getMapList("whens").forEach { whenModel ->
                val commandName = whenModel.getString("command")
                val systemName = whenModel.getString("system")
                val serviceName = whenModel.getString("service")
                val actor = JSONPath.get<String>(whenModel, "options.actor")
                val command = commandByName.getOrPut("$systemName.$serviceName.$commandName") {
                    ZflCommand(
                        name = commandName,
                        system = systemName,
                        service = serviceName,
                        actor = actor,
                        sourceRef = sourceRefOf(flowName, commandName)
                    )
                }

                val triggers = whenModel.getList("triggers")
                val eventNames = whenModel.getList("events")

                // Store when clause
                policies += ZflPolicy(
                    description = whenModel.getString("javadoc"),
                    triggers = triggers,
                    condition = JSONPath.get<String>(whenModel, "options.if"),
                    command = commandName,
                    events = eventNames,
                    sourceRef = sourceRefOf(flowName, commandName)
                )

                // emitted events
                eventNames.forEach { eventName ->
                    events.getOrPut(eventName) {
                        ZflEvent(
                            name = eventName,
                            description = null, // TODO: get from model
                            system = command.system,
                            service = command.service,
                            isError = false, // TODO: annotate in ZFL
                            sourceRef = sourceRefOf(flowName, eventName)
                        )
                    }
                }
            }
            
            val end = ZflEnd(
                completed = JSONPath.get(flowModel, "end.completed", emptyList<String>()),
                suspended = JSONPath.get(flowModel, "end.suspended", emptyList<String>()),
                cancelled = JSONPath.get(flowModel, "end.cancelled", emptyList<String>()),
                sourceRef = sourceRefOf(flowName, "end")
            )

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
            actors = actors
        )
    }

    /**
     * Best-effort source ref resolution.
     * Can be refined later using model.locations.
     */
    private fun sourceRefOf(flowName: String, name: String): SourceRef =
        SourceRef(
            file = "<zfl>",
            line = 1,
            column = 1
        )

    // Extension functions for safe map access
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

    private fun Map<String, Any?>.getString(key: String): String =
        this[key] as? String ?: ""
}
