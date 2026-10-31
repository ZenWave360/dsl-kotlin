package io.zenwave360.language.zfl.editing

import io.zenwave360.language.antlr.ZflBaseListener
import io.zenwave360.language.antlr.ZflParser as AntlrParser
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.language.zfl.semantic.Severity
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.tree.ParseTreeWalker

/**
 * Prototype edit engine for ZFL visual editing (ES-P1).
 *
 * Implements three operations over original parsed text using the ANTLR parse tree
 * and CommonTokenStream for precise character ranges. Returns [ZflEditResult] objects
 * containing non-overlapping [ZflTextEdit]s without mutating the input.
 *
 * Supported (ES-P1):
 *   1. [addEmissionToOccurrence] – add `emits SomeEvent` to a named command occurrence.
 *   2. [changePolicyTrigger] – replace the trigger expression of a named when-block.
 *   3. [renameCommand] – rename a command throughout the flow, with identity mapping.
 *
 * Unsupported / deferred to ES-P2:
 *   - Adding/removing commands (requires service selection: ZVE-Q2)
 *   - Creating policies atomically (requires full atomic form: ZVE-R5)
 *   - Deleting commands or events (requires cascade/reference analysis: ZVE-R7)
 *   - Adding/removing policy triggers (add/remove one event from a trigger group)
 *   - Cross-flow renames
 *   - Renaming start events or emitted events
 *   - Setting a policy's target command
 *   - Scoped renames with cascade reporting
 *   - Response form edits
 *   - Undo idempotency checks (operation-id dedup: ZVE-R4 / ES-P2)
 *   - Capability advertisement (ZVE-R12 / ES-P2)
 *   - Revision token enforcement (transport layer: ES-P4)
 *
 * spec:flows/zfl-visual-editing.md#zve-r3, #zve-r4, #zve-r5, #zve-r8, #zve-r13
 */
class ZflEditor {

    // -------------------------------------------------------------------------
    // Operation 1: Add an emission to a named command occurrence
    // -------------------------------------------------------------------------

    /**
     * Adds `emits [eventName]` to the body of the command occurrence identified by
     * [flowName] and [occurrenceKey].
     *
     * [occurrenceKey] must match one of the `occurrenceKey` values produced by the
     * semantic analyzer (e.g. "doCommand@definition" or "doCommand@when[TriggerA,TriggerB]").
     * If the occurrence already emits [eventName] the call is a no-op.
     *
     * An occurrence that has no explicit `do` body cannot receive an emission;
     * operations on such occurrences are rejected.
     *
     * spec:flows/zfl-visual-editing.md#zve-r4, #zve-r5, #zve-r8, #zve-r13
     */
    fun addEmissionToOccurrence(
        source: String,
        flowName: String,
        occurrenceKey: String,
        eventName: String
    ): ZflEditResult {
        val parseResult = ZflParser().parse(source)
        if (parseResult.syntaxProblems.isNotEmpty()) {
            return ZflEditResult.rejected("Source has syntax errors; cannot apply edits.")
        }

        val collector = OccurrenceCollector(flowName)
        ParseTreeWalker.DEFAULT.walk(collector, parseResult.tree)

        val target = collector.occurrences.find { it.key == occurrenceKey }
            ?: return ZflEditResult.rejected(
                "Occurrence '$occurrenceKey' not found in flow '$flowName'."
            )

        if (!target.hasBody) {
            return ZflEditResult.rejected(
                "Occurrence '$occurrenceKey' has no explicit do-body; cannot add an emission. " +
                "Occurrences declared only as `when X do Y` without a body block are unsupported for this operation (ES-P2)."
            )
        }

        // Already emitting this event from this occurrence? → no-op (ZVE-R8)
        if (eventName in target.emittedEvents) {
            return ZflEditResult.noOp()
        }

        // Insert before the closing brace of the do-body.
        // Preserve indentation: detect the indent used for the last statement, or default to 8 spaces.
        val indent = detectIndent(source, target.bodyEnd)
        val insertion = "${indent}emits $eventName\n"
        val edit = ZflTextEdit(start = target.bodyEnd, end = target.bodyEnd, replacement = insertion)

        val candidate = applyEdits(source, listOf(edit))
        val validationResult = validateZfl(candidate)
        if (!validationResult.accepted) return validationResult

        return ZflEditResult(accepted = true, edits = listOf(edit))
    }

    // -------------------------------------------------------------------------
    // Operation 2: Change a policy's trigger
    // -------------------------------------------------------------------------

    /**
     * Replaces the trigger expression of the `when` block identified by [flowName]
     * and [policyKey] with [newTriggers].
     *
     * [policyKey] identifies the when-block to change. It is formed as
     * `commandName@when[trigger1,trigger2,...]` (sorted triggers) matching the
     * occurrence key produced by the semantic analyzer.  When two policies have the
     * same command and sorted triggers (equal keys), the operation is AMBIGUOUS and
     * is rejected; the caller must supply [disambiguationIndex] (0-based) to select
     * the nth among them.
     *
     * [newTriggers] is the structured trigger data: a list of groups, each group
     * being a list of event names with a separator ("," for AND-groups, "|" for OR).
     * AND-groups joined by `and`; within a group the separator is used literally.
     * Single flat list with no separator defaults to comma-separated in one group.
     *
     * spec:flows/zfl-visual-editing.md#zve-r6, #zve-r8, #zve-r13
     */
    fun changePolicyTrigger(
        source: String,
        flowName: String,
        policyKey: String,
        newTriggers: List<TriggerGroup>,
        disambiguationIndex: Int? = null
    ): ZflEditResult {
        val parseResult = ZflParser().parse(source)
        if (parseResult.syntaxProblems.isNotEmpty()) {
            return ZflEditResult.rejected("Source has syntax errors; cannot apply edits.")
        }

        val collector = WhenCollector(flowName)
        ParseTreeWalker.DEFAULT.walk(collector, parseResult.tree)

        val matching = collector.whens.filter { it.key == policyKey }
        when {
            matching.isEmpty() -> return ZflEditResult.rejected(
                "Policy '$policyKey' not found in flow '$flowName'."
            )
            matching.size > 1 && disambiguationIndex == null -> return ZflEditResult.rejected(
                "Policy '$policyKey' is ambiguous: ${matching.size} when-blocks share this key in flow '$flowName'. " +
                "Supply disambiguationIndex to select the intended one. " +
                "This is required by ZVE-R13: parallel edges need explicit origin selection."
            )
        }

        val target = if (disambiguationIndex != null) {
            matching.getOrNull(disambiguationIndex)
                ?: return ZflEditResult.rejected(
                    "disambiguationIndex=$disambiguationIndex out of range for policy '$policyKey' (found ${matching.size})."
                )
        } else {
            matching.first()
        }

        // Build the new trigger text
        val newTriggerText = buildTriggerText(newTriggers)

        // If unchanged → no-op (ZVE-R8)
        val oldTriggerText = source.substring(target.triggerStart, target.triggerEnd)
        if (oldTriggerText.trim() == newTriggerText.trim()) {
            return ZflEditResult.noOp()
        }

        val edit = ZflTextEdit(
            start = target.triggerStart,
            end = target.triggerEnd,
            replacement = newTriggerText
        )

        val candidate = applyEdits(source, listOf(edit))
        val validationResult = validateZfl(candidate)
        if (!validationResult.accepted) return validationResult

        return ZflEditResult(accepted = true, edits = listOf(edit))
    }

    // -------------------------------------------------------------------------
    // Operation 3: Rename a command (flow-local)
    // -------------------------------------------------------------------------

    /**
     * Renames the command [oldName] to [newName] within [flowName], updating all
     * occurrences in do-headers, when-headers, call statements, and on-handlers.
     *
     * Returns an [ZflIdentityMapping] so visual selection survives the rename (ZVE-R3).
     * Other flows in the same document are not touched (ZVE-R7).
     * If [oldName] is not found in [flowName] the operation is rejected.
     *
     * spec:flows/zfl-visual-editing.md#zve-r3, #zve-r7, #zve-r8
     */
    fun renameCommand(
        source: String,
        flowName: String,
        oldName: String,
        newName: String
    ): ZflEditResult {
        if (oldName == newName) return ZflEditResult.noOp()

        val parseResult = ZflParser().parse(source)
        if (parseResult.syntaxProblems.isNotEmpty()) {
            return ZflEditResult.rejected("Source has syntax errors; cannot apply edits.")
        }

        val collector = CommandRefCollector(flowName, oldName)
        ParseTreeWalker.DEFAULT.walk(collector, parseResult.tree)

        if (!collector.foundDeclaration) {
            return ZflEditResult.rejected(
                "Command '$oldName' is not declared in flow '$flowName'. " +
                "Only flow-local commands (do-blocks) can be renamed here."
            )
        }

        if (collector.refs.isEmpty()) {
            return ZflEditResult.noOp()
        }

        // Build non-overlapping edits sorted by start offset
        val edits = collector.refs
            .sortedBy { it.first }
            .map { (start, end) -> ZflTextEdit(start, end, newName) }

        val candidate = applyEdits(source, edits)
        val validationResult = validateZfl(candidate)
        if (!validationResult.accepted) return validationResult

        val mapping = ZflIdentityMapping(
            flowName = flowName,
            kind = "command",
            oldName = oldName,
            newName = newName
        )

        return ZflEditResult(accepted = true, edits = edits, identityMappings = listOf(mapping))
    }

    // -------------------------------------------------------------------------
    // Validation helper
    // -------------------------------------------------------------------------

    /** Re-parses the candidate text and checks for syntax errors and ERROR-severity diagnostics. */
    private fun validateZfl(candidate: String): ZflEditResult {
        val pr = ZflParser().parse(candidate)
        if (pr.syntaxProblems.isNotEmpty()) {
            val msgs = pr.syntaxProblems.map { it["message"]?.toString() ?: "syntax error" }
            return ZflEditResult.rejected("Edit produces syntax errors: ${msgs.joinToString("; ")}")
        }
        val semantic = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(candidate))
        val errors = semantic.diagnostics.filter { it.severity == Severity.ERROR }
        if (errors.isNotEmpty()) {
            val msgs = errors.map { it.message }
            return ZflEditResult.rejected("Edit produces semantic errors: ${msgs.joinToString("; ")}")
        }
        return ZflEditResult(accepted = true)
    }

    // -------------------------------------------------------------------------
    // Indent detection
    // -------------------------------------------------------------------------

    /**
     * Scans backwards from [position] to find the indentation used on the preceding
     * non-empty line, then returns that same prefix.  Falls back to 8 spaces.
     */
    private fun detectIndent(source: String, position: Int): String {
        // Walk back to find the start of the previous line
        var i = (position - 1).coerceAtLeast(0)
        // Skip over any whitespace before position (the closing brace line)
        while (i > 0 && source[i] in " \t") i--
        // Skip to end of previous line content
        while (i > 0 && source[i] != '\n') i--
        if (i == 0) return "        " // default
        i-- // move past the '\n'
        while (i > 0 && source[i] != '\n') i--
        val lineStart = if (source[i] == '\n') i + 1 else 0
        val sb = StringBuilder()
        var j = lineStart
        while (j < source.length && source[j] in " \t") {
            sb.append(source[j])
            j++
        }
        return if (sb.isEmpty()) "        " else sb.toString()
    }

    // -------------------------------------------------------------------------
    // Trigger text builder
    // -------------------------------------------------------------------------

    private fun buildTriggerText(groups: List<TriggerGroup>): String {
        return groups.joinToString(" and ") { group ->
            group.events.joinToString(group.separator)
        }
    }
}

// -------------------------------------------------------------------------
// Data classes for structured trigger input
// -------------------------------------------------------------------------

/**
 * A group of trigger events joined by [separator] within the group.
 * Multiple groups are joined by `and` at the top level.
 *
 * [separator] should be "," (comma/AND within group) or "|" (OR within group).
 * spec:flows/zfl-visual-editing.md#zve-r6
 */
data class TriggerGroup(
    val events: List<String>,
    val separator: String = ","
)

// -------------------------------------------------------------------------
// ANTLR tree listeners
// -------------------------------------------------------------------------

/** Collected occurrence data for operation 1. */
private data class OccurrenceInfo(
    val key: String,
    val hasBody: Boolean,
    /** Offset of the last character before the closing `}` of the do-body. */
    val bodyEnd: Int,
    val emittedEvents: Set<String>
)

/** Collected when-block data for operation 2. */
private data class WhenInfo(
    val key: String,
    /** Start/end offsets of the trigger expression (the text between WHEN and DO). */
    val triggerStart: Int,
    val triggerEnd: Int
)

/**
 * Listener that walks one named flow and collects all `do` command occurrences.
 * Used by [ZflEditor.addEmissionToOccurrence].
 *
 * Occurrence keys mirror [io.zenwave360.language.zfl.internal.ZflListenerImpl]:
 *   - definition: `commandName@definition`
 *   - when-body: `commandName@when[trigger1,trigger2,...]` (sorted triggers)
 */
private class OccurrenceCollector(private val targetFlow: String) : ZflBaseListener() {

    val occurrences = mutableListOf<OccurrenceInfo>()

    private var inTargetFlow = false
    private var currentFlowName: String? = null

    // For tracking when-bodies
    private var pendingWhenAction: String? = null
    private var pendingWhenTriggers: List<String> = emptyList()

    override fun enterFlow(ctx: AntlrParser.FlowContext) {
        currentFlowName = ctx.flow_name()?.text
        inTargetFlow = (currentFlowName == targetFlow)
    }

    override fun exitFlow(ctx: AntlrParser.FlowContext) {
        inTargetFlow = false
        currentFlowName = null
    }

    override fun enterFlow_when(ctx: AntlrParser.Flow_whenContext) {
        if (!inTargetFlow) return
        val actionName = ctx.flow_command_name()?.text ?: return
        val triggers = collectTriggers(ctx.flow_when_trigger())
        pendingWhenAction = actionName
        pendingWhenTriggers = triggers

        // A `when X do Y { ... }` with an explicit body
        val body = ctx.flow_do_body() ?: return
        val key = whenKey(actionName, triggers)
        val bodyEnd = bodyEndOffset(body, ctx)
        val emits = collectEmitsFromBody(body)
        // Count duplicate when-keys to match ZflListenerImpl collision resolution
        val existing = occurrences.count { it.key == key || it.key.startsWith("$key#") }
        val finalKey = if (existing == 0) key else "$key#${existing + 1}"
        occurrences += OccurrenceInfo(
            key = finalKey,
            hasBody = true,
            bodyEnd = bodyEnd,
            emittedEvents = emits
        )
    }

    override fun enterFlow_do(ctx: AntlrParser.Flow_doContext) {
        if (!inTargetFlow) return
        val actionName = ctx.flow_command_name()?.text ?: return
        val key = "$actionName@definition"
        val body = ctx.flow_do_body()
        val bodyEnd = if (body != null) bodyEndOffset(body, ctx) else -1
        val emits = if (body != null) collectEmitsFromBody(body) else emptySet()
        val existing = occurrences.count { it.key == key || it.key.startsWith("$key#") }
        val finalKey = if (existing == 0) key else "$key#${existing + 1}"
        occurrences += OccurrenceInfo(
            key = finalKey,
            hasBody = body != null,
            bodyEnd = bodyEnd,
            emittedEvents = emits
        )
    }

    private fun collectTriggers(ctx: AntlrParser.Flow_when_triggerContext?): List<String> {
        if (ctx == null) return emptyList()
        val result = mutableListOf<String>()
        ctx.flow_when_trigger_group().forEach { group ->
            group.flow_when_event_trigger().forEach { trigger ->
                result += trigger.text
            }
        }
        return result.sorted()
    }

    private fun whenKey(actionName: String, triggers: List<String>): String =
        "$actionName@when[${triggers.joinToString(",")}]"

    /**
     * Returns the offset just before the closing `}` of [body],
     * which is where new statements should be inserted.
     */
    private fun bodyEndOffset(body: AntlrParser.Flow_do_bodyContext, parent: ParserRuleContext): Int {
        // The do-body is inside { ... }; the `}` token belongs to the parent flow_do or flow_when.
        // parent.stop is the `}` token; we want to insert before it.
        val rbrace = parent.stop ?: return (body.stop?.stopIndex ?: 0) + 1
        return rbrace.startIndex
    }

    private fun collectEmitsFromBody(body: AntlrParser.Flow_do_bodyContext): Set<String> {
        val result = mutableSetOf<String>()
        body.flow_do_statement().forEach { stmt ->
            stmt.flow_do_signal()?.flow_signal_body()?.flow_event_list()?.flow_event_name()
                ?.forEach { en -> en.text?.let(result::add) }
            stmt.flow_do_on()?.flow_signal_body()?.flow_event_list()?.flow_event_name()
                ?.forEach { en -> en.text?.let(result::add) }
        }
        return result
    }
}

/**
 * Listener that collects when-block trigger ranges for operation 2.
 */
private class WhenCollector(private val targetFlow: String) : ZflBaseListener() {

    val whens = mutableListOf<WhenInfo>()

    private var inTargetFlow = false

    override fun enterFlow(ctx: AntlrParser.FlowContext) {
        inTargetFlow = (ctx.flow_name()?.text == targetFlow)
    }

    override fun exitFlow(ctx: AntlrParser.FlowContext) {
        inTargetFlow = false
    }

    override fun enterFlow_when(ctx: AntlrParser.Flow_whenContext) {
        if (!inTargetFlow) return
        val actionName = ctx.flow_command_name()?.text ?: return
        val triggerCtx = ctx.flow_when_trigger() ?: return
        val triggerStart = triggerCtx.start?.startIndex ?: return
        val triggerEnd = (triggerCtx.stop?.stopIndex ?: return) + 1

        // Build the key (sorted triggers)
        val triggers = mutableListOf<String>()
        triggerCtx.flow_when_trigger_group().forEach { group ->
            group.flow_when_event_trigger().forEach { triggers += it.text }
        }
        val key = "$actionName@when[${triggers.sorted().joinToString(",")}]"
        // Duplicate key resolution (same as OccurrenceCollector)
        val existing = whens.count { it.key == key || it.key.startsWith("$key#") }
        val finalKey = if (existing == 0) key else "$key#${existing + 1}"

        whens += WhenInfo(key = finalKey, triggerStart = triggerStart, triggerEnd = triggerEnd)
    }
}

/**
 * Listener that collects all character ranges for a named command in one flow.
 * Covers: do-headers, when-headers, call targets, on-call targets.
 * Used by [ZflEditor.renameCommand].
 */
private class CommandRefCollector(
    private val targetFlow: String,
    private val commandName: String
) : ZflBaseListener() {

    /** Pairs of (startOffset, endOffset exclusive) for each reference to replace. */
    val refs = mutableListOf<Pair<Int, Int>>()
    var foundDeclaration = false

    private var inTargetFlow = false

    override fun enterFlow(ctx: AntlrParser.FlowContext) {
        inTargetFlow = (ctx.flow_name()?.text == targetFlow)
    }

    override fun exitFlow(ctx: AntlrParser.FlowContext) {
        inTargetFlow = false
    }

    // do commandName { ... }  → standalone declaration
    override fun enterFlow_do(ctx: AntlrParser.Flow_doContext) {
        if (!inTargetFlow) return
        val nameCtx = ctx.flow_command_name() ?: return
        if (nameCtx.text == commandName) {
            foundDeclaration = true
            addRef(nameCtx)
        }
    }

    // when X do commandName { ... } → implicit declaration + occurrence reference
    // A command appearing as a `when` target is a flow-local symbol even without a standalone `do` block.
    override fun enterFlow_when(ctx: AntlrParser.Flow_whenContext) {
        if (!inTargetFlow) return
        val nameCtx = ctx.flow_command_name() ?: return
        if (nameCtx.text == commandName) {
            foundDeclaration = true
            addRef(nameCtx)
        }
    }

    // call commandName → call target
    override fun enterFlow_do_call(ctx: AntlrParser.Flow_do_callContext) {
        if (!inTargetFlow) return
        val nameCtx = ctx.flow_command_name() ?: return
        if (nameCtx.text == commandName) {
            addRef(nameCtx)
        }
    }

    // on X call commandName → on-handler call target
    override fun enterFlow_do_on(ctx: AntlrParser.Flow_do_onContext) {
        if (!inTargetFlow) return
        val nameCtx = ctx.flow_command_name() ?: return
        if (nameCtx.text == commandName) {
            addRef(nameCtx)
        }
    }

    private fun addRef(ctx: ParserRuleContext) {
        val start = ctx.start?.startIndex ?: return
        val end = (ctx.stop?.stopIndex ?: return) + 1
        refs += Pair(start, end)
    }
}
