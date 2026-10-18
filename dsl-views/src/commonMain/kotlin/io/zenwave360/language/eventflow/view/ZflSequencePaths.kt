package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.semantic.ZflActionStep
import io.zenwave360.language.zfl.semantic.ZflCallStep
import io.zenwave360.language.zfl.semantic.ZflCommand
import io.zenwave360.language.zfl.semantic.ZflFlow
import io.zenwave360.language.zfl.semantic.ZflSignalStep
import io.zenwave360.language.zfl.semantic.ZflStart

internal enum class ParticipantKind { ACTOR, PARTICIPANT }

internal data class SequenceParticipant(
    val alias: String,
    val label: String,
    val kind: ParticipantKind,
    val system: String? = null
)

internal data class SequenceMessage(
    val from: SequenceParticipant,
    val to: SequenceParticipant,
    val label: String,
    val dashed: Boolean
)

/** The alternative taken at an execution that had more than one. [rank] is its declaration order. */
internal data class SequenceChoice(val label: String, val rank: Int, val preferred: Boolean)

/** One way through the flow, from a start to a terminal event of [endOutcome]. */
internal data class SequencePath(
    val startIndex: Int,
    val start: ZflStart,
    val endOutcome: String,
    val messages: List<SequenceMessage>,
    val terminal: SequenceParticipant,
    val choices: List<SequenceChoice>
) {
    val choiceLabels: List<String> get() = choices.map { it.label }
}

internal fun sequenceParticipantAlias(value: String): String =
    value.replace(Regex("[^A-Za-z0-9_]"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
        .ifBlank { "Participant" }

/**
 * Enumerates the paths of a flow. Executions are the per-`when` occurrences of each command:
 * an event triggers every occurrence that lists it. See PLAN.md D20.
 */
internal class ZflSequencePaths(
    private val flow: ZflFlow,
    private val maxVisitsPerPath: Int = 2
) {
    private class Exec(
        val name: String,
        val triggers: List<String>,
        val steps: List<ZflActionStep>,
        val participant: SequenceParticipant
    )

    private data class State(
        val messages: List<SequenceMessage> = emptyList(),
        val choices: List<SequenceChoice> = emptyList(),
        val visits: Map<String, Int> = emptyMap()
    ) {
        fun add(message: SequenceMessage) = copy(messages = messages + message)
        fun choose(choice: SequenceChoice) = copy(choices = choices + choice)
    }

    private data class Outcome(
        val state: State,
        val events: List<String>,
        val responses: List<String>,
        val emitter: SequenceParticipant
    )

    private data class AltSpec(
        val label: String,
        val events: List<String>,
        val responses: List<String>,
        val failure: Boolean
    )

    private val terminalOutcome: Map<String, String> = buildMap {
        flow.end.endOutcomes.forEach { (outcome, events) -> events.forEach { if (it !in this) put(it, outcome) } }
    }

    private val execs: List<Exec> = flow.commands.flatMap { command -> execsOf(command) }

    private val execsByTrigger: Map<String, List<Exec>> =
        execs.flatMap { exec -> exec.triggers.distinct().map { it to exec } }.groupBy({ it.first }, { it.second })

    private fun execsOf(command: ZflCommand): List<Exec> {
        if (command.occurrences.isEmpty()) {
            val triggers = flow.policies.filter { it.command == command.name }.flatMap { it.triggers }
            return listOf(Exec(command.name, triggers, command.steps, participantOf(command)))
        }
        return command.occurrences.map { occurrence ->
            val label = occurrence.service ?: command.service ?: occurrence.system ?: command.system ?: command.name
            val system = occurrence.system ?: command.system
            Exec(
                name = command.name,
                triggers = occurrence.triggers,
                steps = occurrence.steps.ifEmpty { command.steps },
                participant = SequenceParticipant(sequenceParticipantAlias(label), label, ParticipantKind.PARTICIPANT, system)
            )
        }
    }

    private fun participantOf(command: ZflCommand?): SequenceParticipant {
        val label = command?.service ?: command?.system ?: command?.name ?: "Flow"
        return SequenceParticipant(sequenceParticipantAlias(label), label, ParticipantKind.PARTICIPANT, command?.system)
    }

    private fun participantOf(commandName: String): SequenceParticipant =
        calleeFor(commandName)?.participant ?: participantOf(flow.commands.firstOrNull { it.name == commandName })

    private fun calleeFor(name: String): Exec? {
        val candidates = execs.filter { it.name == name }
        return candidates.firstOrNull { it.triggers.isEmpty() } ?: candidates.firstOrNull()
    }

    private fun participantForStart(start: ZflStart): SequenceParticipant {
        val label = start.actor ?: start.timer ?: start.system ?: start.name
        val kind = if (start.actor != null) ParticipantKind.ACTOR else ParticipantKind.PARTICIPANT
        return SequenceParticipant(sequenceParticipantAlias(label), label, kind, if (start.actor != null) null else start.system)
    }

    fun paths(): List<SequencePath> {
        val found = mutableListOf<SequencePath>()
        flow.starts.forEachIndexed { index, start ->
            val startParticipant = participantForStart(start)
            val consumers = execsByTrigger[start.name].orEmpty()
            consumers.forEachIndexed { consumerIndex, exec ->
                var state = State()
                if (consumers.size > 1) state = state.choose(SequenceChoice(exec.name, consumerIndex, consumerIndex == 0))
                state = state.add(SequenceMessage(startParticipant, exec.participant, exec.name, false))
                run(exec, state).forEach { outcome ->
                    deliver(listOf(outcome.events), outcome.emitter, outcome.state, index, start, found)
                }
            }
        }
        return found
            .distinctBy { Triple(it.endOutcome, it.messages, it.choices) }
            .sortedWith(
                compareBy<SequencePath> { it.startIndex }
                    .thenBy { it.choices.size }
                    .thenComparator { a, b -> compareRanks(a.choices, b.choices) }
            )
    }

    /** The happy path (D22), or null when the flow has no path. */
    fun happy(paths: List<SequencePath>): SequencePath? {
        val firstStart = paths.minOfOrNull { it.startIndex } ?: return null
        val candidates = paths.filter { it.startIndex == firstStart }
        candidates.firstOrNull { path -> path.choices.all { it.preferred } }?.let { return it }
        flow.end.endOutcomes.keys.forEach { outcome ->
            candidates.filter { it.endOutcome == outcome }.minByOrNull { it.choices.size }?.let { return it }
        }
        return candidates.firstOrNull()
    }

    private fun compareRanks(a: List<SequenceChoice>, b: List<SequenceChoice>): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val c = a[i].rank.compareTo(b[i].rank)
            if (c != 0) return c
        }
        return 0
    }

    private fun deliver(
        batches: List<List<String>>,
        emitter: SequenceParticipant,
        state: State,
        startIndex: Int,
        start: ZflStart,
        out: MutableList<SequencePath>
    ) {
        val live = batches.filter { it.isNotEmpty() }
        val head = live.firstOrNull() ?: return
        val terminals = head.filter { it in terminalOutcome }
        if (terminals.isNotEmpty()) {
            val messages = state.messages + terminals.map { SequenceMessage(emitter, emitter, it, true) }
            out += SequencePath(startIndex, start, terminalOutcome.getValue(terminals.first()), messages, emitter, state.choices)
            return
        }
        val event = head.first()
        val remaining = listOf(head.drop(1)) + live.drop(1)
        val consumers = execsByTrigger[event].orEmpty()
        if (consumers.isEmpty()) {
            deliver(remaining, emitter, state, startIndex, start, out)
            return
        }
        consumers.forEachIndexed { index, exec ->
            var next = state
            if (consumers.size > 1) next = next.choose(SequenceChoice(exec.name, index, index == 0))
            next = next
                .add(SequenceMessage(emitter, exec.participant, event, true))
                .add(SequenceMessage(exec.participant, exec.participant, exec.name, false))
            run(exec, next).forEach { outcome ->
                deliver(listOf(outcome.events) + remaining, outcome.emitter, outcome.state, startIndex, start, out)
            }
        }
    }

    private fun run(exec: Exec, state: State): List<Outcome> {
        val visits = state.visits[exec.name] ?: 0
        if (visits >= maxVisitsPerPath) return emptyList()
        return walk(exec.steps, exec.participant, state.copy(visits = state.visits + (exec.name to visits + 1)))
    }

    private fun walk(steps: List<ZflActionStep>, owner: SequenceParticipant, state: State): List<Outcome> {
        val callIndex = steps.indexOfFirst { it is ZflCallStep && !it.async }
        val before = if (callIndex < 0) steps else steps.subList(0, callIndex)
        var base = state
        before.filterIsInstance<ZflCallStep>().forEach { call ->
            base = base.add(SequenceMessage(owner, participantOf(call.action), "async call ${call.action}", false))
        }
        val specs = altSpecs(before)
        val preferred = specs.indexOfFirst { !it.failure }.coerceAtLeast(0)
        val results = mutableListOf<Outcome>()
        specs.forEachIndexed { index, spec ->
            val chosen = if (specs.size > 1) base.choose(SequenceChoice(spec.label, index, index == preferred)) else base
            results += Outcome(chosen, spec.events, spec.responses, owner)
        }
        if (callIndex >= 0) {
            results += call(steps[callIndex] as ZflCallStep, steps.subList(callIndex + 1, steps.size), owner, base)
        }
        return results
    }

    private fun call(
        call: ZflCallStep,
        rest: List<ZflActionStep>,
        owner: SequenceParticipant,
        state: State
    ): List<Outcome> {
        val callee = calleeFor(call.action)
        val sent = state.add(SequenceMessage(owner, participantOf(call.action), call.action, false))
        if (callee == null) return walk(rest, owner, sent)
        val replies = run(callee, sent)
        val several = replies.size > 1
        val restEmits = rest.any { it is ZflSignalStep && (it.emits || it.response) }
        val results = mutableListOf<Outcome>()
        replies.forEach { reply ->
            val single = when {
                reply.events.size == 1 -> reply.events.single()
                reply.events.isEmpty() && reply.responses.size == 1 -> reply.responses.single()
                else -> null
            }
            val text = single ?: reply.events.joinToString(", ")
            if (text.isBlank()) return@forEach
            val replied = reply.state.add(SequenceMessage(reply.emitter, owner, text, true))
            val handlers = if (single == null) emptyList() else call.handlers.filter { it.endOutcome == single }
            if (handlers.isEmpty()) {
                val continued = walk(rest, owner, replied)
                if (continued.isEmpty() && !restEmits && reply.events.isNotEmpty()) {
                    results += Outcome(replied, reply.events, emptyList(), owner)
                } else {
                    results += continued
                }
                return@forEach
            }
            handlers.forEach { handler ->
                val signal = handler.signal
                val action = handler.action
                if (signal != null) {
                    var next = replied
                    val outcome = signal.outcome
                    if (several && outcome != null && next.choices.isNotEmpty()) {
                        next = next.copy(choices = next.choices.dropLast(1) + next.choices.last().copy(label = outcome))
                    }
                    results += if (signal.response && !signal.emits) {
                        Outcome(next, emptyList(), signal.events, owner)
                    } else {
                        Outcome(next, signal.events, emptyList(), owner)
                    }
                } else if (action != null) {
                    val target = calleeFor(action)
                    val next = replied.add(SequenceMessage(owner, participantOf(action), action, false))
                    if (target != null) results += run(target, next)
                }
            }
        }
        return results
    }

    /** Emissions sharing an `@outcome` are one alternative; any other emission or response is its own. */
    private fun altSpecs(steps: List<ZflActionStep>): List<AltSpec> {
        val specs = mutableListOf<AltSpec>()
        val byOutcome = mutableMapOf<String, Int>()
        steps.filterIsInstance<ZflSignalStep>().filter { it.emits || it.response }.forEach { step ->
            val outcome = step.options["outcome"]
            val failure = step.options.containsKey("failure")
            if (step.emits && outcome != null) {
                val existing = byOutcome[outcome]
                if (existing != null) {
                    val spec = specs[existing]
                    specs[existing] = spec.copy(events = spec.events + step.endOutcome, failure = spec.failure || failure)
                } else {
                    byOutcome[outcome] = specs.size
                    specs += AltSpec(outcome, listOf(step.endOutcome), emptyList(), failure)
                }
            } else if (step.emits) {
                specs += AltSpec(step.endOutcome, listOf(step.endOutcome), emptyList(), failure)
            } else {
                specs += AltSpec(outcome ?: step.endOutcome, emptyList(), listOf(step.endOutcome), failure)
            }
        }
        return specs
    }
}
