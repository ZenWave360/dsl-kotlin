package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.semantic.ZflActionStep
import io.zenwave360.language.zfl.semantic.ZflCallStep
import io.zenwave360.language.zfl.semantic.ZflCommand
import io.zenwave360.language.zfl.semantic.ZflFlow
import io.zenwave360.language.zfl.semantic.ZflPolicy
import io.zenwave360.language.zfl.semantic.ZflSemanticModel
import io.zenwave360.language.zfl.semantic.ZflServiceStep
import io.zenwave360.language.zfl.semantic.ZflSignalStep
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

        val commandByName = flow.commands.associateBy { it.name }
        val policiesByTrigger = flow.policies
            .flatMap { policy -> policy.triggers.map { trigger -> trigger to policy } }
            .groupBy({ it.first }, { it.second })

        val sequences = flow.end.outcomes.keys.flatMap { outcome ->
            renderSequences(
                flow = flow,
                outcome = outcome,
                commandByName = commandByName,
                policiesByTrigger = policiesByTrigger,
                sequenceRenderMode = sequenceRenderMode
            )
        }

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

        val startByName = flow.starts.associateBy { it.name }

        flow.starts.forEach { start ->
            val startId = flowchartNodeId("start", start.name)
            val startLabel = start.actor ?: start.timer ?: start.name
            val startShape = if (start.actor != null) "[\"$startLabel\"]" else "(($startLabel))"
            nodeLines += "    $startId$startShape"
            classAssignments += "    class $startId actor"
        }

        graph.nodes.forEach { node ->
            val nodeId = flowchartNodeId(node.type.name.lowercase(), node.label)
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

        flow.end.outcomes.keys.forEach { outcome ->
            val terminalId = flowchartNodeId("terminal", outcome)
            nodeLines += "    $terminalId((\"${escape(outcome)}\"))"
            classAssignments += "    class $terminalId terminal"
        }

        lines += nodeLines.sorted()

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
            val sourceId = flowchartNodeId(sourceNode.type.name.lowercase(), sourceNode.label)
            val targetId = flowchartNodeId(targetNode.type.name.lowercase(), targetNode.label)
            val label = edge.label?.let { "|${escape(it)}|" } ?: ""
            val connector = if (edge.type == FlowGraphEdgeType.CONDITIONAL) "-.->" else "-->"
            edgeLines += "    $sourceId $connector$label $targetId"
        }

        flow.end.outcomes.forEach { (outcome, eventNames) ->
            val terminalId = flowchartNodeId("terminal", outcome)
            eventNames.forEach { eventName ->
                val eventId = flowchartNodeId("outcome", eventName)
                edgeLines += "    $eventId --> $terminalId"
            }
        }

        lines += edgeLines.distinct()
        lines += ""
        lines += "    classDef actor fill:#dbeafe,stroke:#1d4ed8,color:#0f172a"
        lines += "    classDef action fill:#dcfce7,stroke:#166534,color:#0f172a"
        lines += "    classDef event fill:#f8fafc,stroke:#475569,color:#0f172a"
        lines += "    classDef policy fill:#fef3c7,stroke:#92400e,color:#0f172a"
        lines += "    classDef terminal fill:#fee2e2,stroke:#b91c1c,color:#0f172a"
        lines += classAssignments.distinct()
        return lines.joinToString("\n")
    }

    private fun renderSequences(
        flow: ZflFlow,
        outcome: String,
        commandByName: Map<String, ZflCommand>,
        policiesByTrigger: Map<String, List<ZflPolicy>>,
        sequenceRenderMode: MermaidSequenceRenderMode
    ): List<MermaidSequenceDiagram> {
        val terminalEvents = flow.end.outcomes[outcome].orEmpty().toSet()
        val variants = mutableListOf<SequenceVariant>()

        flow.starts.forEach { start ->
            policiesByTrigger[start.name].orEmpty().forEach { policy ->
                val startParticipant = participantForStart(start)
                val initial = mutableListOf<SequenceMessage>()
                initial += SequenceMessage(
                    from = startParticipant,
                    to = participantForCommand(commandByName[policy.command]),
                    label = policy.command,
                    dashed = false
                )

                val execution = executeCommand(
                    commandName = policy.command,
                    callerParticipant = startParticipant,
                    commandByName = commandByName,
                    path = TraversalPath(),
                    prefixMessages = initial
                )

                execution.forEach { result ->
                    variants += continueFromEvent(
                        eventName = result.eventName,
                        emitterParticipant = result.emitterParticipant,
                        terminalEvents = terminalEvents,
                        outcome = outcome,
                        entryTrigger = start.name,
                        commandByName = commandByName,
                        policiesByTrigger = policiesByTrigger,
                        path = result.path,
                        messages = result.messages,
                        warnings = result.warnings
                    )
                }
            }
        }

        val distinctVariants = variants.distinctBy { it.messages to it.warnings to it.terminalParticipant }
        val families = groupScenarioFamilies(distinctVariants)

        return if (sequenceRenderMode == MermaidSequenceRenderMode.SEPARATE_VARIANTS) {
            families.flatMap { family ->
                val prefixLength = commonPrefixLength(family.map { it.messages })
                val suffixLength = commonSuffixLength(family.map { it.messages }, prefixLength)
                family.map { variant ->
                    variant to deriveForkLabel(variant.messages, prefixLength, suffixLength)
                }
            }.map { entry ->
                val startLabel = entry.first.entryTrigger
                val branchLabel = entry.second
                MermaidSequenceDiagram(
                    outcome = outcome,
                    title = buildSequenceTitle(outcome, startLabel, branchLabel),
                    startLabel = startLabel,
                    branchLabels = listOf(branchLabel),
                    mermaid = renderSequenceDiagram(outcome, entry.first)
                )
            }
        } else {
            families.mapIndexedNotNull { _, family ->
                val useAltBlocks = shouldRenderAltBlocks(sequenceRenderMode, family)
                val startLabel = family.firstOrNull()?.entryTrigger ?: return@mapIndexedNotNull null
                val branchLabels = deriveFamilyBranchLabels(family)
                if (useAltBlocks) {
                    MermaidSequenceDiagram(
                        outcome = outcome,
                        title = buildSequenceTitle(outcome, startLabel),
                        startLabel = startLabel,
                        branchLabels = branchLabels,
                        mermaid = renderAltSequenceDiagram(outcome, family)
                    )
                } else {
                    val variant = family.firstOrNull() ?: return@mapIndexedNotNull null
                    MermaidSequenceDiagram(
                        outcome = outcome,
                        title = buildSequenceTitle(outcome, startLabel),
                        startLabel = startLabel,
                        branchLabels = branchLabels,
                        mermaid = renderSequenceDiagram(outcome, variant)
                    )
                }
            }
        }
    }

    private fun shouldRenderAltBlocks(
        sequenceRenderMode: MermaidSequenceRenderMode,
        variants: List<SequenceVariant>
    ): Boolean =
        when (sequenceRenderMode) {
            MermaidSequenceRenderMode.SEPARATE_VARIANTS -> false
            MermaidSequenceRenderMode.ALT_BLOCKS -> variants.size > 1
            MermaidSequenceRenderMode.AUTO -> variants.size > 1
        }

    private fun groupScenarioFamilies(variants: List<SequenceVariant>): List<List<SequenceVariant>> =
        variants.groupBy { variant ->
            variant.messages.firstOrNull()
        }.values.toList()

    private fun executeCommand(
        commandName: String,
        callerParticipant: SequenceParticipant,
        commandByName: Map<String, ZflCommand>,
        path: TraversalPath,
        prefixMessages: List<SequenceMessage>
    ): List<CommandExecutionResult> {
        val command = commandByName[commandName] ?: return emptyList()
        val owner = participantForCommand(command)
        val nextPath = path.visit(commandName, maxCommandVisitsPerPath)
        if (nextPath.truncated) {
            return listOf(
                CommandExecutionResult(
                    eventName = "",
                    emitterParticipant = owner,
                    messages = prefixMessages,
                    warnings = listOf("Cycle detected while visiting command '$commandName'; traversal truncated."),
                    path = nextPath
                )
            )
        }

        var results = emptyList<CommandExecutionResult>()

        command.steps.forEach { step ->
            when (step) {
                is ZflServiceStep -> Unit
                is ZflSignalStep -> {
                    if (step.emits || step.response) {
                        results += CommandExecutionResult(
                            eventName = step.outcome,
                            emitterParticipant = owner,
                            messages = prefixMessages,
                            warnings = emptyList(),
                            path = nextPath
                        )
                    }
                }
                is ZflCallStep -> {
                    val callPrefix = prefixMessages + SequenceMessage(
                        from = owner,
                        to = participantForCommand(commandByName[step.action]),
                        label = step.action,
                        dashed = false
                    )
                    val callResults = executeCommand(
                        commandName = step.action,
                        callerParticipant = owner,
                        commandByName = commandByName,
                        path = nextPath,
                        prefixMessages = callPrefix
                    )
                    results += applyOutcomeHandlers(
                        callResults = callResults,
                        handlers = step.handlers,
                        callerParticipant = owner,
                        commandByName = commandByName
                    )
                }
                is ZflActionStep -> Unit
            }
        }

        return results
    }

    private fun applyOutcomeHandlers(
        callResults: List<CommandExecutionResult>,
        handlers: List<io.zenwave360.language.zfl.semantic.ZflOutcomeHandler>,
        callerParticipant: SequenceParticipant,
        commandByName: Map<String, ZflCommand>
    ): List<CommandExecutionResult> {
        val results = mutableListOf<CommandExecutionResult>()
        callResults.forEach { result ->
            if (result.eventName.isBlank()) {
                results += result
                return@forEach
            }
            val returned = result.messages + SequenceMessage(
                from = result.emitterParticipant,
                to = callerParticipant,
                label = result.eventName,
                dashed = true
            )
            val matchingHandlers = handlers.filter { it.outcome == result.eventName }
            if (matchingHandlers.isEmpty()) {
                results += result.copy(messages = returned, emitterParticipant = callerParticipant)
            }
            matchingHandlers.forEach { handler ->
                when {
                    handler.emits != null -> {
                        results += CommandExecutionResult(
                            eventName = handler.emits,
                            emitterParticipant = callerParticipant,
                            messages = returned,
                            warnings = result.warnings,
                            path = result.path
                        )
                    }
                    handler.action != null -> {
                        val nestedPrefix = returned + SequenceMessage(
                            from = callerParticipant,
                            to = participantForCommand(commandByName[handler.action]),
                            label = handler.action,
                            dashed = false
                        )
                        results += executeCommand(
                            commandName = handler.action,
                            callerParticipant = callerParticipant,
                            commandByName = commandByName,
                            path = result.path,
                            prefixMessages = nestedPrefix
                        ).map { nested -> nested.copy(warnings = result.warnings + nested.warnings) }
                    }
                }
            }
        }
        return results
    }

    private fun continueFromEvent(
        eventName: String,
        emitterParticipant: SequenceParticipant,
        terminalEvents: Set<String>,
        outcome: String,
        entryTrigger: String,
        commandByName: Map<String, ZflCommand>,
        policiesByTrigger: Map<String, List<ZflPolicy>>,
        path: TraversalPath,
        messages: List<SequenceMessage>,
        warnings: List<String>
    ): List<SequenceVariant> {
        if (eventName.isBlank()) {
            return emptyList()
        }

        if (eventName in terminalEvents) {
            val terminalMessages = messages + SequenceMessage(
                from = emitterParticipant,
                to = emitterParticipant,
                label = eventName,
                dashed = true
            )
            return listOf(
                SequenceVariant(
                    messages = terminalMessages,
                    warnings = warnings,
                    terminalParticipant = emitterParticipant,
                    outcome = outcome,
                    entryTrigger = entryTrigger
                )
            )
        }

        val variants = mutableListOf<SequenceVariant>()
        policiesByTrigger[eventName].orEmpty().forEach { policy ->
            val consumer = participantForCommand(commandByName[policy.command])
            val prefix = messages +
                SequenceMessage(
                    from = emitterParticipant,
                    to = consumer,
                    label = eventName,
                    dashed = true
                ) +
                SequenceMessage(
                    from = consumer,
                    to = consumer,
                    label = policy.command,
                    dashed = false
                )

            val executions = executeCommand(
                commandName = policy.command,
                callerParticipant = consumer,
                commandByName = commandByName,
                path = path,
                prefixMessages = prefix
            )
            executions.forEach { execution ->
                variants += continueFromEvent(
                    eventName = execution.eventName,
                    emitterParticipant = execution.emitterParticipant,
                    terminalEvents = terminalEvents,
                    outcome = outcome,
                    entryTrigger = entryTrigger,
                    commandByName = commandByName,
                    policiesByTrigger = policiesByTrigger,
                    path = execution.path,
                    messages = execution.messages,
                    warnings = warnings + execution.warnings
                )
            }
        }
        return variants
    }

    private fun renderSequenceDiagram(outcome: String, variant: SequenceVariant): String {
        val participants = collectParticipants(listOf(variant))
        val lines = mutableListOf<String>()
        appendSequenceHeader(lines, participants)
        variant.warnings.distinct().forEach { warning ->
            lines += "    %% $warning"
        }
        appendMessages(lines, variant.messages)
        lines += "    Note over ${variant.terminalParticipant.alias}: end $outcome"
        return lines.joinToString("\n")
    }

    private fun renderAltSequenceDiagram(
        outcome: String,
        variants: List<SequenceVariant>
    ): String {
        val participants = collectParticipants(variants)
        val prefixLength = commonPrefixLength(variants.map { it.messages })
        val suffixLength = commonSuffixLength(variants.map { it.messages }, prefixLength)
        val prefix = variants.firstOrNull()?.messages?.take(prefixLength).orEmpty()
        val suffix = variants.firstOrNull()?.messages?.takeLast(suffixLength).orEmpty()
        val lines = mutableListOf<String>()

        appendSequenceHeader(lines, participants)
        variants.flatMap { it.warnings }.distinct().forEach { warning ->
            lines += "    %% $warning"
        }
        appendMessages(lines, prefix)

        variants.forEachIndexed { index, variant ->
            val middle = variant.messages.subList(prefixLength, variant.messages.size - suffixLength)
            val label = deriveForkLabel(variant.messages, prefixLength, suffixLength)
            lines += if (index == 0) {
                "    alt $label"
            } else {
                "    else $label"
            }
            if (middle.isEmpty()) {
                lines += "        Note over ${variant.terminalParticipant.alias}: no additional steps"
            } else {
                appendMessages(lines, middle, "        ")
            }
        }
        lines += "    end"

        appendMessages(lines, suffix)
        lines += "    Note over ${variants.first().terminalParticipant.alias}: end $outcome"
        return lines.joinToString("\n")
    }

    private fun deriveFamilyBranchLabels(variants: List<SequenceVariant>): List<String> {
        if (variants.isEmpty()) {
            return emptyList()
        }
        val prefixLength = commonPrefixLength(variants.map { it.messages })
        val suffixLength = commonSuffixLength(variants.map { it.messages }, prefixLength)
        return variants.map { deriveForkLabel(it.messages, prefixLength, suffixLength) }.distinct()
    }

    private fun appendSequenceHeader(
        lines: MutableList<String>,
        participants: List<SequenceParticipant>
    ) {
        lines += "sequenceDiagram"
        participants.forEach { participant ->
            if (participant.kind == ParticipantKind.ACTOR) {
                lines += "    actor ${participant.alias}"
            } else {
                lines += "    participant ${participant.alias} as \"${escapeSequence(participant.label)}\""
            }
        }
    }

    private fun collectParticipants(variants: List<SequenceVariant>): List<SequenceParticipant> {
        val participants = linkedSetOf<SequenceParticipant>()
        variants.forEach { variant ->
            variant.messages.forEach { message ->
                participants += message.from
                participants += message.to
            }
        }
        return participants.toList()
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
        if (messageLists.isEmpty()) {
            return 0
        }
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
        if (messageLists.isEmpty()) {
            return 0
        }
        val shortestLength = messageLists.minOf { it.size }
        var suffixLength = 0
        while (suffixLength < shortestLength - prefixLength) {
            val candidateIndex = messageLists.first().size - 1 - suffixLength
            val candidate = messageLists.first()[candidateIndex]
            val matches = messageLists.all { messages ->
                val currentIndex = messages.size - 1 - suffixLength
                currentIndex >= prefixLength && messages[currentIndex] == candidate
            }
            if (!matches) {
                break
            }
            suffixLength++
        }
        return suffixLength
    }

    private fun deriveForkLabel(
        messages: List<SequenceMessage>,
        prefixLength: Int,
        suffixLength: Int
    ): String {
        val middle = messages.subList(prefixLength, messages.size - suffixLength)
        val forkMessage = middle.firstOrNull { it.dashed } ?: middle.firstOrNull() ?: messages.lastOrNull()
        return forkMessage?.label?.let { "via $it" } ?: "variant"
    }

    private fun buildSequenceTitle(
        outcome: String,
        startLabel: String,
        branchLabel: String? = null
    ): String =
        buildString {
            append(outcome)
            append(": from ")
            append(startLabel)
            if (branchLabel != null) {
                append(" ")
                append(branchLabel)
            }
        }

    private fun participantForStart(start: ZflStart): SequenceParticipant {
        val label = start.actor ?: start.timer ?: start.system ?: start.name
        val kind = if (start.actor != null) ParticipantKind.ACTOR else ParticipantKind.PARTICIPANT
        return SequenceParticipant(alias = participantAlias(label), label = label, kind = kind)
    }

    private fun participantForCommand(command: ZflCommand?): SequenceParticipant {
        val label = command?.service ?: command?.system ?: command?.name ?: "Flow"
        return SequenceParticipant(alias = participantAlias(label), label = label, kind = ParticipantKind.PARTICIPANT)
    }

    private fun flowchartNodeId(prefix: String, value: String): String =
        "${prefix}_${value.replace(Regex("[^A-Za-z0-9_]"), "_")}"

    private fun participantAlias(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "Participant" }

    private fun escape(value: String): String =
        value.replace("\"", "\\\"")

    private fun escapeSequence(value: String): String =
        value.replace("\n", " ").replace(":", " -")

    private data class CommandExecutionResult(
        val eventName: String,
        val emitterParticipant: SequenceParticipant,
        val messages: List<SequenceMessage>,
        val warnings: List<String>,
        val path: TraversalPath
    )

    private data class SequenceVariant(
        val messages: List<SequenceMessage>,
        val warnings: List<String>,
        val terminalParticipant: SequenceParticipant,
        val outcome: String,
        val entryTrigger: String
    )

    private data class SequenceMessage(
        val from: SequenceParticipant,
        val to: SequenceParticipant,
        val label: String,
        val dashed: Boolean
    )

    private data class SequenceParticipant(
        val alias: String,
        val label: String,
        val kind: ParticipantKind
    )

    private enum class ParticipantKind {
        ACTOR,
        PARTICIPANT
    }

    private data class TraversalPath(
        val commandVisits: Map<String, Int> = emptyMap(),
        val truncated: Boolean = false
    ) {
        fun visit(commandName: String, maxVisits: Int): TraversalPath {
            val count = commandVisits[commandName] ?: 0
            return if (count >= maxVisits) {
                copy(truncated = true)
            } else {
                copy(commandVisits = commandVisits + (commandName to count + 1), truncated = false)
            }
        }
    }
}
