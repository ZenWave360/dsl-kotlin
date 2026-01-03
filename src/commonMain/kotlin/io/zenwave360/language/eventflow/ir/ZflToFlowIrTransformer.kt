package io.zenwave360.language.eventflow.ir

import io.zenwave360.language.zfl.semantic.*

/**
 * Transforms a ZFL semantic model into the canonical EventFlow IR.
 *
 * Mental model:
 * Each when in ZFL expresses:
 *   (trigger event[s]) → [optional policy] → command → emitted event[s]
 *
 * The transformer's job is to make this chain explicit as nodes and edges.
 */
class ZflToFlowIrTransformer {

    fun transform(semanticModel: ZflSemanticModel): FlowIR {
        val nodeMap = mutableMapOf<String, FlowNode>()
        val edgeList = mutableListOf<FlowEdge>()

        semanticModel.flows.forEach { flow ->
            // 1. Register all commands as nodes
            flow.commands.forEach { command ->
                nodeMap[command.name] = FlowNode(
                    id = command.name,
                    type = FlowNodeType.COMMAND,
                    label = command.name,
                    system = command.system,
                    sourceRef = command.sourceRef
                )
            }

            // 2. Register all events as nodes
            flow.events.forEach { event ->
                nodeMap[event.name] = FlowNode(
                    id = event.name,
                    type = FlowNodeType.EVENT,
                    label = event.name,
                    system = event.system,
                    sourceRef = event.sourceRef
                )
            }

            // 3. Register all policies as nodes
            flow.policies.forEach { policy ->
                val policyId = policyNodeId(policy)
                nodeMap[policyId] = FlowNode(
                    id = policyId,
                    type = FlowNodeType.POLICY,
                    label = policy.name,
                    system = policy.system,
                    sourceRef = policy.sourceRef
                )
            }

            // 4. Build edges from when-clauses
            buildEdgesFromWhens(flow, edgeList)
        }

        return FlowIR(
            nodes = nodeMap.values.toList(),
            edges = edgeList
        )
    }

    /**
     * Builds edges from when-clauses.
     *
     * Each when expresses: triggers → [policy?] → command → events
     */
    private fun buildEdgesFromWhens(flow: ZflFlow, edgeList: MutableList<FlowEdge>) {
        flow.whens.forEach { when_ ->
            val commandName = when_.command

            // Find if this when has a conditional policy
            val policy = flow.policies.find { it.toCommand == commandName }

            // Create edges from triggers to command (possibly through policy)
            when_.triggers.forEach { triggerName ->
                if (policy != null) {
                    // Trigger → Policy → Command
                    val policyId = policyNodeId(policy)

                    edgeList.add(FlowEdge(
                        id = "$triggerName→$policyId",
                        source = triggerName,
                        target = policyId,
                        type = FlowEdgeType.TRIGGER,
                        sourceRef = when_.sourceRef
                    ))

                    edgeList.add(FlowEdge(
                        id = "$policyId→$commandName",
                        source = policyId,
                        target = commandName,
                        type = FlowEdgeType.CONDITIONAL,
                        label = policy.name,
                        sourceRef = policy.sourceRef
                    ))
                } else {
                    // Direct: Trigger → Command
                    edgeList.add(FlowEdge(
                        id = "$triggerName→$commandName",
                        source = triggerName,
                        target = commandName,
                        type = FlowEdgeType.TRIGGER,
                        sourceRef = when_.sourceRef
                    ))
                }
            }

            // Create edges from command to emitted events
            when_.events.forEach { eventName ->
                edgeList.add(FlowEdge(
                    id = "$commandName→$eventName",
                    source = commandName,
                    target = eventName,
                    type = FlowEdgeType.CAUSATION,
                    sourceRef = when_.sourceRef
                ))
            }
        }
    }

    /**
     * Generates a unique ID for a policy node.
     */
    private fun policyNodeId(policy: ZflPolicy): String =
        "policy:${policy.name}:${policy.toCommand}"
}
