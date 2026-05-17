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

                groups.putIfAbsent(
                    groupId,
                    ServiceGroupView(
                        id = groupId,
                        label = groupLabel,
                        path = groupPath
                    )
                )

                val eventGroupKey = path?.groupSegments?.joinToString(">") ?: "Unbounded"

                nodes.putIfAbsent(
                    "command:${command.name}",
                    ServiceNodeView(
                        id = "command:${command.name}",
                        type = ServiceNodeType.COMMAND,
                        label = command.name,
                        groupId = groupId,
                        system = command.system,
                        service = command.service,
                        servicePath = path?.raw,
                        sourceRef = command.sourceRef
                    )
                )

                command.outcomesForServicesView().forEach { outcome ->
                    val eventId = "event:${outcome}@${eventGroupKey}"
                    nodes.putIfAbsent(
                        eventId,
                        ServiceNodeView(
                            id = eventId,
                            type = ServiceNodeType.EVENT,
                            label = outcome,
                            groupId = groupId,
                            system = command.system,
                            service = command.service,
                            servicePath = path?.raw,
                            sourceRef = command.sourceRef
                        )
                    )
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
