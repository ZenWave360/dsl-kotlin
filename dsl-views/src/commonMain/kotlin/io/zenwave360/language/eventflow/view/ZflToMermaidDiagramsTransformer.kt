package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.semantic.ZflFlow
import io.zenwave360.language.zfl.semantic.ZflPolicy
import io.zenwave360.language.zfl.semantic.ZflSemanticModel
import io.zenwave360.language.zfl.semantic.ZflStart

class ZflToMermaidDiagramsTransformer(
    private val graphTransformer: ZflToFlowGraphTransformer = ZflToFlowGraphTransformer(),
    private val maxCommandVisitsPerPath: Int = 2
) {

    fun transform(semanticModel: ZflSemanticModel): MermaidDiagramsView {
        return transform(semanticModel, MermaidSequenceRenderMode.ALT_BLOCKS)
    }

    fun transform(
        semanticModel: ZflSemanticModel,
        sequenceRenderMode: MermaidSequenceRenderMode
    ): MermaidDiagramsView {
        val flow = semanticModel.flows.firstOrNull()
            ?: return MermaidDiagramsView(
                flowName = "UnknownFlow",
                flowchart = "flowchart TD",
                sequenceRenderMode = sequenceRenderMode,
                sequences = emptyList()
            )

        val policiesByTrigger = flow.policies
            .flatMap { policy -> policy.triggers.map { trigger -> trigger to policy } }
            .groupBy({ it.first }, { it.second })

        val engine = ZflSequencePaths(flow, maxCommandVisitsPerPath)
        val paths = engine.paths()
        val sequences = renderSequences(flow, paths, engine.happy(paths), sequenceRenderMode)

        return MermaidDiagramsView(
            flowName = flow.name,
            flowchart = renderFlowchart(semanticModel, flow, policiesByTrigger),
            sequenceRenderMode = sequenceRenderMode,
            sequences = sequences
        )
    }

    private fun renderFlowchart(
        semanticModel: ZflSemanticModel,
        flow: ZflFlow,
        policiesByTrigger: Map<String, List<ZflPolicy>>
    ): String {
        val graph = graphTransformer.transform(semanticModel)
        val lines = mutableListOf<String>()
        lines += "flowchart TD"

        val nodeLines = linkedSetOf<String>()
        val classAssignments = mutableListOf<String>()
        val outcomeNodes = linkedSetOf<String>()

        val startByName = flow.starts.associateBy { it.name }

        flow.starts.forEach { start ->
            val startId = flowchartNodeId("start", start.name)
            val startLabel = start.actor ?: start.timer ?: start.name
            val startShape = if (start.actor != null) "[\"$startLabel\"]" else "(($startLabel))"
            nodeLines += "    $startId$startShape"
            classAssignments += "    class $startId actor"
        }

        graph.nodes.forEach { node ->
            val nodeId = flowchartNodeId(flowchartPrefix(node.type), node.label)
            val shape = when (node.type) {
                FlowGraphNodeType.START -> "[\"${escape(node.label)}\"]"
                FlowGraphNodeType.ACTION -> "[\"${escape(node.label)}\"]"
                FlowGraphNodeType.OUTCOME -> "[/${escape(node.label)}/]"
                FlowGraphNodeType.POLICY -> "{{\"${escape(node.label)}\"}}"
            }
            nodeLines += "    $nodeId$shape"
            val klass = when (node.type) {
                FlowGraphNodeType.START -> "actor"
                FlowGraphNodeType.ACTION -> "action"
                FlowGraphNodeType.OUTCOME -> "event"
                FlowGraphNodeType.POLICY -> "policy"
            }
            classAssignments += "    class $nodeId $klass"
        }

        flow.end.endOutcomes.keys.forEach { endOutcome ->
            val terminalId = flowchartNodeId("terminal", endOutcome)
            nodeLines += "    $terminalId((\"${escape(endOutcome)}\"))"
            classAssignments += "    class $terminalId terminal"
        }

        val edgeLines = mutableListOf<String>()

        flow.starts.forEach { start ->
            val startPolicies = policiesByTrigger[start.name].orEmpty()
            startPolicies.forEach { policy ->
                val sourceId = flowchartNodeId("start", start.name)
                val targetId = flowchartNodeId("action", policy.command)
                edgeLines += "    $sourceId --> $targetId"
            }
        }

        graph.edges.forEach { edge ->
            val sourceNode = graph.nodes.find { it.id == edge.source }
            val targetNode = graph.nodes.find { it.id == edge.target }
            if (sourceNode == null || targetNode == null) {
                return@forEach
            }
            if (sourceNode.type == FlowGraphNodeType.START && startByName.containsKey(sourceNode.label)) {
                return@forEach
            }
            val sourceId = flowchartNodeId(flowchartPrefix(sourceNode.type), sourceNode.label)
            val targetId = flowchartNodeId(flowchartPrefix(targetNode.type), targetNode.label)
            // A local copy: dsl-kotlin's properties cannot be smart cast from this module.
            val outcome = edge.outcome
            if (
                sourceNode.type == FlowGraphNodeType.ACTION &&
                targetNode.type == FlowGraphNodeType.OUTCOME &&
                outcome != null
            ) {
                val outcomeNodeId = flowchartOutcomeNodeId(sourceNode.label, outcome)
                if (outcomeNodes.add(outcomeNodeId)) {
                    nodeLines += "    $outcomeNodeId([\"${escape(outcome)}\"])"
                    classAssignments += "    class $outcomeNodeId outcomeNode"
                    val label = edge.label?.let { "|${escape(it)}|" } ?: ""
                    edgeLines += "    $sourceId -->$label $outcomeNodeId"
                }
                edgeLines += "    $outcomeNodeId --> $targetId"
                return@forEach
            }
            val label = edge.label?.let { "|${escape(it)}|" } ?: ""
            val connector = if (edge.type == FlowGraphEdgeType.CONDITIONAL) "-.->" else "-->"
            edgeLines += "    $sourceId $connector$label $targetId"
        }

        flow.end.endOutcomes.forEach { (endOutcome, eventNames) ->
            val terminalId = flowchartNodeId("terminal", endOutcome)
            eventNames.forEach { eventName ->
                val eventId = flowchartNodeId("event", eventName)
                edgeLines += "    $eventId --> $terminalId"
            }
        }

        lines += nodeLines.sorted()
        lines += edgeLines.distinct()
        lines += ""
        lines += "    classDef actor fill:#dbeafe,stroke:#1d4ed8,color:#0f172a"
        lines += "    classDef action fill:#dcfce7,stroke:#166534,color:#0f172a"
        lines += "    classDef event fill:#f8fafc,stroke:#475569,color:#0f172a"
        lines += "    classDef outcomeNode fill:#ede9fe,stroke:#6d28d9,color:#0f172a"
        lines += "    classDef policy fill:#fef3c7,stroke:#92400e,color:#0f172a"
        lines += "    classDef terminal fill:#fee2e2,stroke:#b91c1c,color:#0f172a"
        lines += classAssignments.distinct()
        return lines.joinToString("\n")
    }


    private fun renderSequences(
        flow: ZflFlow,
        paths: List<SequencePath>,
        happy: SequencePath?,
        sequenceRenderMode: MermaidSequenceRenderMode
    ): List<MermaidSequenceDiagram> =
        flow.end.endOutcomes.keys.flatMap { endOutcome ->
            val ofOutcome = paths.filter { it.endOutcome == endOutcome }
            if (sequenceRenderMode == MermaidSequenceRenderMode.SEPARATE_VARIANTS) {
                ofOutcome.map { path ->
                    MermaidSequenceDiagram(
                        endOutcome = endOutcome,
                        title = buildSequenceTitle(endOutcome, path.start.name, path.choiceLabels),
                        startLabel = path.start.name,
                        branchLabels = path.choiceLabels,
                        mermaid = renderSequenceDiagram(endOutcome, path),
                        happy = path === happy
                    )
                }
            } else {
                ofOutcome.groupBy { it.startIndex }.values.map { family ->
                    val startLabel = family.first().start.name
                    val branchLabels = familyLabels(family)
                    MermaidSequenceDiagram(
                        endOutcome = endOutcome,
                        title = buildSequenceTitle(endOutcome, startLabel, emptyList()),
                        startLabel = startLabel,
                        branchLabels = if (family.size > 1) branchLabels.distinct() else family.first().choiceLabels,
                        mermaid = if (family.size > 1) {
                            renderAltSequenceDiagram(endOutcome, family, branchLabels)
                        } else {
                            renderSequenceDiagram(endOutcome, family.first())
                        },
                        happy = family.any { it === happy }
                    )
                }
            }
        }

    private fun renderSequenceDiagram(endOutcome: String, path: SequencePath): String {
        val lines = mutableListOf<String>()
        appendSequenceHeader(lines, collectParticipants(listOf(path)))
        appendMessages(lines, path.messages)
        lines += "    Note over ${path.terminal.alias}: end $endOutcome"
        return lines.joinToString("\n")
    }

    private fun renderAltSequenceDiagram(
        endOutcome: String,
        paths: List<SequencePath>,
        labels: List<String>
    ): String {
        val prefixLength = commonPrefixLength(paths.map { it.messages })
        val suffixLength = commonSuffixLength(paths.map { it.messages }, prefixLength)
        val lines = mutableListOf<String>()

        appendSequenceHeader(lines, collectParticipants(paths))
        appendMessages(lines, paths.first().messages.take(prefixLength))

        val forkAlias = paths.first().messages.getOrNull(prefixLength - 1)?.to?.alias ?: paths.first().terminal.alias
        paths.forEachIndexed { index, path ->
            val middle = path.messages.subList(prefixLength, path.messages.size - suffixLength)
            lines += if (index == 0) "    alt ${escapeSequence(labels[index])}" else "    else ${escapeSequence(labels[index])}"
            if (middle.isEmpty()) {
                lines += "        Note over $forkAlias: no additional steps"
            } else {
                appendMessages(lines, middle, "        ")
            }
        }
        lines += "    end"

        appendMessages(lines, paths.first().messages.takeLast(suffixLength))
        lines += "    Note over ${paths.first().terminal.alias}: end $endOutcome"
        return lines.joinToString("\n")
    }

    /** Alt label of each path: its choices without the ones every path of the family shares. */
    private fun familyLabels(paths: List<SequencePath>): List<String> {
        val all = paths.map { it.choiceLabels }
        val shortest = all.minOf { it.size }
        var prefix = 0
        while (prefix < shortest && all.all { it[prefix] == all.first()[prefix] }) prefix++
        var suffix = 0
        while (suffix < shortest - prefix - 1 && all.all { it[it.size - 1 - suffix] == all.first()[all.first().size - 1 - suffix] }) suffix++
        return all.map { labels ->
            labels.subList(prefix, labels.size - suffix).joinToString(" → ").ifBlank { "variant" }
        }
    }

    private fun appendSequenceHeader(
        lines: MutableList<String>,
        participants: List<SequenceParticipant>
    ) {
        lines += "sequenceDiagram"
        participants.filter { it.kind == ParticipantKind.ACTOR }.forEach { lines += "    actor ${it.alias}" }
        val others = participants.filter { it.kind != ParticipantKind.ACTOR }
        others.filter { it.system == null }.forEach { lines += participantLine(it, "    ") }
        others.filter { it.system != null }.groupBy { it.system }.forEach { (system, members) ->
            lines += "    box transparent ${escapeSequence(system.orEmpty())}"
            members.forEach { lines += participantLine(it, "        ") }
            lines += "    end"
        }
    }

    private fun participantLine(participant: SequenceParticipant, indent: String): String =
        "${indent}participant ${participant.alias} as ${escapeSequence(participant.label)}"

    private fun collectParticipants(paths: List<SequencePath>): List<SequenceParticipant> {
        val participants = linkedMapOf<String, SequenceParticipant>()
        paths.forEach { path ->
            path.messages.forEach { message ->
                if (message.from.alias !in participants) participants[message.from.alias] = message.from
                if (message.to.alias !in participants) participants[message.to.alias] = message.to
            }
        }
        return participants.values.toList()
    }

    private fun appendMessages(
        lines: MutableList<String>,
        messages: List<SequenceMessage>,
        indent: String = "    "
    ) {
        messages.forEach { message ->
            val arrow = if (message.dashed) "-->>" else "->>"
            lines += "$indent${message.from.alias}$arrow${message.to.alias}: ${escapeSequence(message.label)}"
        }
    }

    private fun commonPrefixLength(messageLists: List<List<SequenceMessage>>): Int {
        val shortestLength = messageLists.minOf { it.size }
        var index = 0
        while (index < shortestLength && messageLists.all { it[index] == messageLists.first()[index] }) {
            index++
        }
        return index
    }

    private fun commonSuffixLength(
        messageLists: List<List<SequenceMessage>>,
        prefixLength: Int
    ): Int {
        val shortestLength = messageLists.minOf { it.size }
        var suffixLength = 0
        while (suffixLength < shortestLength - prefixLength) {
            val candidate = messageLists.first()[messageLists.first().size - 1 - suffixLength]
            if (!messageLists.all { it[it.size - 1 - suffixLength] == candidate }) {
                break
            }
            suffixLength++
        }
        return suffixLength
    }

    private fun buildSequenceTitle(
        endOutcome: String,
        startLabel: String,
        choices: List<String>
    ): String =
        buildString {
            append(endOutcome)
            append(": from ")
            append(startLabel)
            if (choices.isNotEmpty()) {
                append(" via ")
                append(choices.joinToString(" → "))
            }
        }

    private fun flowchartNodeId(prefix: String, value: String): String =
        "${prefix}_${value.replace(Regex("[^A-Za-z0-9_]"), "_")}"

    private fun flowchartPrefix(nodeType: FlowGraphNodeType): String =
        when (nodeType) {
            FlowGraphNodeType.START -> "start"
            FlowGraphNodeType.ACTION -> "action"
            FlowGraphNodeType.OUTCOME -> "event"
            FlowGraphNodeType.POLICY -> "policy"
        }

    private fun flowchartOutcomeNodeId(commandName: String, outcome: String): String =
        flowchartNodeId("outcome", "${commandName}_$outcome")

    private fun escape(value: String): String =
        value.replace("\"", "\\\"")

    private fun escapeSequence(value: String): String =
        value.replace("\n", " ").replace(":", " -")
}
