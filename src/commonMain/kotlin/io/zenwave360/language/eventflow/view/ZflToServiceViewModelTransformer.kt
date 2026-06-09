package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.semantic.ZflCommand
import io.zenwave360.language.zfl.semantic.ZflSemanticModel

class ZflToServiceViewModelTransformer {

    fun transform(semanticModel: ZflSemanticModel): ServiceViewModel {
        val groups = linkedMapOf<String, ServiceGroupView>()
        val nodes = linkedMapOf<String, ServiceNodeView>()

        semanticModel.flows.forEach { flow ->
            flow.commands.forEach { command ->
                val path = parseServicePath(command.servicePath)
                val groupId = path?.groupId ?: "group:Unbounded"
                val groupLabel = path?.groupLabel ?: "Unbounded"
                val groupPath = path?.groupSegments?.joinToString("/") ?: "Unbounded"

                if (groupId !in groups) {
                    groups[groupId] = ServiceGroupView(
                        id = groupId,
                        label = groupLabel,
                        path = groupPath
                    )
                }

                val eventGroupKey = path?.groupSegments?.joinToString(">") ?: "Unbounded"

                val commandId = "command:${command.name}"
                if (commandId !in nodes) {
                    nodes[commandId] = ServiceNodeView(
                        id = "command:${command.name}",
                        type = ServiceNodeType.COMMAND,
                        label = command.name,
                        groupId = groupId,
                        system = command.system,
                        service = command.service,
                        servicePath = path?.raw,
                        sourceRef = command.sourceRef
                    )
                }

                command.outcomesForServicesView().forEach { endOutcome ->
                    val eventId = "event:${endOutcome}@${eventGroupKey}"
                    if (eventId !in nodes) {
                        nodes[eventId] = ServiceNodeView(
                            id = eventId,
                            type = ServiceNodeType.EVENT,
                            label = endOutcome,
                            groupId = groupId,
                            system = command.system,
                            service = command.service,
                            servicePath = path?.raw,
                            sourceRef = command.sourceRef
                        )
                    }
                }
            }
        }

        return ServiceViewModel(
            groups = groups.values.toList(),
            nodes = nodes.values.toList()
        )
    }
}

private fun ZflCommand.outcomesForServicesView(): List<String> =
    (emits + responses).distinct()
