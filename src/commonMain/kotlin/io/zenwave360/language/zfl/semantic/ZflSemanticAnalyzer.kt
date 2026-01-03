package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.zfl.ZflModel
import io.zenwave360.language.source.SourceRef

class ZflSemanticAnalyzer {

    fun analyze(model: ZflModel): ZflSemanticModel {
        val actors = mutableMapOf<String, ZflActor>()
        val systems = mutableMapOf<String, ZflSystem>()
        val flows = mutableListOf<ZflFlow>()

        model.getFlows().values.forEach { flowData ->
            val flowModel = flowData.asMapOrReturn { return@forEach }
            val flowName = flowModel.getString("name")

            // 1. Systems + commands
            val commandByName = mutableMapOf<String, ZflCommand>()

            flowModel.getMap("systems").values.forEach { systemData ->
                val systemModel = systemData.asMapOrReturn { return@forEach }
                val systemName = systemModel.getString("name")

                systems[systemName] = ZflSystem(systemName)

                systemModel.getMap("services").values.forEach { serviceData ->
                    val service = serviceData.asMapOrReturn { return@forEach }

                    service.getList("commands").forEach { commandName ->
                        commandByName[commandName] = ZflCommand(
                            name = commandName,
                            system = systemName,
                            actor = null,
                            sourceRef = sourceRefOf(flowName, commandName)
                        )
                    }
                }
            }

            // 2. Starts → actors
            flowModel.getMap("starts").values.forEach { startData ->
                val start = startData.asMapOrReturn { return@forEach }
                val startName = start.getString("name")
                val options = start.getMap("options")

                options["actor"]?.toString()?.let { actorName ->
                    actors.getOrPut(actorName) {
                        ZflActor(actorName, sourceRefOf(flowName, startName))
                    }
                }
            }

            // 3. Events + policies + whens from whens
            val events = mutableMapOf<String, ZflEvent>()
            val policies = mutableListOf<ZflPolicy>()
            val whens = mutableListOf<ZflWhen>()

            flowModel.getMapList("whens").forEach { whenModel ->
                val commandName = whenModel.getString("command")
                val command = commandByName[commandName]
                    ?: error("Command '$commandName' not found in systems")

                val triggers = whenModel.getList("triggers")
                val eventNames = whenModel.getList("events")

                // Store when clause
                whens += ZflWhen(
                    triggers = triggers,
                    command = commandName,
                    events = eventNames,
                    sourceRef = sourceRefOf(flowName, commandName)
                )

                // emitted events
                eventNames.forEach { eventName ->
                    events.getOrPut(eventName) {
                        ZflEvent(
                            name = eventName,
                            system = command.system,
                            isError = eventName.contains("Failed", ignoreCase = true),
                            sourceRef = sourceRefOf(flowName, eventName)
                        )
                    }
                }

                // conditional policies
                whenModel.getMap("options")["if"]?.toString()?.let { condition ->
                    policies += ZflPolicy(
                        name = condition,
                        fromEvent = triggers.joinToString(","),
                        toCommand = commandName,
                        system = command.system,
                        sourceRef = sourceRefOf(flowName, commandName)
                    )
                }
            }

            flows += ZflFlow(
                name = flowName,
                commands = commandByName.values.toList(),
                events = events.values.toList(),
                policies = policies,
                whens = whens
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
