package io.zenwave360.language.zfl.editing

import io.zenwave360.language.antlr.ZflBaseListener
import io.zenwave360.language.antlr.ZflLexer
import io.zenwave360.language.antlr.ZflParser as AntlrParser
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.Severity
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.ParserRuleContext
import org.antlr.v4.kotlinruntime.Token
import org.antlr.v4.kotlinruntime.tree.ParseTreeWalker
import org.antlr.v4.kotlinruntime.tree.TerminalNode

/**
 * Prototype edit engine for ZFL visual editing (ES-P1).
 *
 * Implements three operations over the original text, using the ANTLR parse tree for the
 * source ranges. Each returns a [ZflEditResult] with non-overlapping [ZflTextEdit]s and never
 * mutates its input. All offsets in the results are UTF-16 code-unit indexes into the source
 * string, the indexes `String` uses in Kotlin and JavaScript.
 *
 * Supported (ES-P1):
 *   1. [addEmissionToOccurrence] adds an unconditional `emits SomeEvent` to a command occurrence.
 *   2. [changePolicyTrigger] replaces the trigger of a `when` block.
 *   3. [renameCommand] renames a command throughout one flow, with identity mapping.
 *
 * Recorded limitations. Unless a line says the edit is accepted, each is an explicit rejection,
 * never an accepted wrong result:
 *   - An occurrence written as `when X do Y` without a body cannot receive an emission.
 *   - A trigger change that alters the group structure of an expression holding comments.
 *   - A rename of a command listed in a `systems` `commands:` list. This is a recorded ES-P2
 *     limitation even in a one-flow document, because the list is not safely scoped here.
 *   - A rename onto a name already used as a command in the flow.
 *   - A rename when a quoted `@compensates("name")` refers to the command.
 *   - A rename of a command that is only called (and has no `do` block).
 *   - Adding an emission to an occurrence whose body is inherited from another occurrence.
 *   - Adding an emission to an occurrence that has its own statements but inherits only its
 *     emissions is accepted; the inherited emissions are not kept, and linting should report
 *     the inconsistent ZFL.
 *   - A document that does not build a model, or whose parser reports a fatal problem, is
 *     rejected for every operation. The message says the document could not be analysed and
 *     includes the construction failure. The edit is not checked against an empty model.
 *
 * Deferred to ES-P2:
 *   - Adding/removing commands (service selection: ZVE-Q2) and creating policies atomically
 *   - Deleting commands or events (cascade/reference analysis: ZVE-R7)
 *   - Adding/removing single policy triggers, setting a policy's target command
 *   - Cross-flow and scoped renames, renaming start or emitted events, response form edits
 *   - Operation-id dedup (ZVE-R4), capability advertisement (ZVE-R12), revision tokens (ES-P4)
 *
 * spec:flows/zfl-visual-editing#zve-r3
 * spec:flows/zfl-visual-editing#zve-r4
 * spec:flows/zfl-visual-editing#zve-r5
 * spec:flows/zfl-visual-editing#zve-r8
 * spec:flows/zfl-visual-editing#zve-r13
 */
class ZflEditor {

    // -------------------------------------------------------------------------
    // Operation 1: Add an emission to a command occurrence
    // -------------------------------------------------------------------------

    /**
     * Adds `emits [eventName]` to the body of the command occurrence selected by [flowName],
     * [occurrenceKey] and, when needed, [disambiguationIndex].
     *
     * [occurrenceKey] uses the analyzer's occurrence keys (`ZflCommandOccurrence.key`): `name@definition`
     * or `name@when[A,B]` (distinct triggers, sorted), with `#2`, `#3`... for repeated blocks.
     * A key with a `#n` suffix selects that block. A key without one selects a block only when exactly
     * one block has that key; with several, the selection is ambiguous and is rejected unless
     * [disambiguationIndex] (0-based, in source order among the blocks sharing the key) says which one.
     *
     * The emission is unconditional. An unannotated `emits` of the same event already in the body
     * makes the call a no-op. A `response`, an `on X emits` or an `@outcome` emission does not.
     *
     * spec:flows/zfl-visual-editing#zve-r5
     * spec:flows/zfl-visual-editing#zve-r8
     * spec:flows/zfl-visual-editing#zve-r13
     */
    fun addEmissionToOccurrence(
        source: String,
        flowName: String,
        occurrenceKey: String,
        eventName: String,
        disambiguationIndex: Int? = null
    ): ZflEditResult {
        if (!isIdentifier(eventName)) return ZflEditResult.rejected(notAnIdentifier("event name", eventName))
        val scan = scanFlow(source, flowName) ?: return syntaxRejection()
        scan.flowRejection(flowName)?.let { return it }

        val target = when (val selection = select(scan.blocks, flowName, occurrenceKey, disambiguationIndex, "Occurrence")) {
            is Selection.Rejected -> return selection.result
            is Selection.Chosen -> selection.block
        }
        val body = target.body ?: return ZflEditResult.rejected(
            "Occurrence '${target.key}' has no body; an emission cannot be added to `when X do Y` written " +
                "without a body (unsupported until ES-P2)."
        )
        if (target.inheritsBody) {
            return ZflEditResult.rejected(
                "Occurrence '${target.key}' has an inherited body; adding an emission would replace its inherited steps " +
                    "(unsupported until ES-P2)."
            )
        }

        // Already emitted unconditionally from this occurrence? No-op (ZVE-R8)
        if (eventName in target.unconditionalEmits) return ZflEditResult.noOp()

        val edit = emissionEdit(source, body, eventName)
        return finish(source, listOf(edit), emptyList())
    }

    // -------------------------------------------------------------------------
    // Operation 2: Change a policy's trigger
    // -------------------------------------------------------------------------

    /**
     * Replaces the trigger expression of the `when` block selected by [flowName], [policyKey] and,
     * when needed, [disambiguationIndex] (selection works as in [addEmissionToOccurrence]) with
     * [newTriggers]: groups joined by `and`, each group's events joined by its separator.
     *
     * When a group keeps its separator tokens, only the event names that differ are edited, so spacing
     * and comments inside it stay. An unchanged expression is a no-op. A null separator keeps the
     * tokens already written, including a trailing or mixed separator, and a group the caller did not
     * change keeps them even when another group is rewritten. The separator of a one-event group does
     * not matter. Rewriting a group, or the expression because the number of groups changed, is
     * rejected when the expression holds comments.
     *
     * spec:flows/zfl-visual-editing#zve-r6
     * spec:flows/zfl-visual-editing#zve-r8
     * spec:flows/zfl-visual-editing#zve-r13
     */
    fun changePolicyTrigger(
        source: String,
        flowName: String,
        policyKey: String,
        newTriggers: List<TriggerGroup>,
        disambiguationIndex: Int? = null
    ): ZflEditResult {
        triggerInputRejection(newTriggers)?.let { return it }
        val scan = scanFlow(source, flowName) ?: return syntaxRejection()
        scan.flowRejection(flowName)?.let { return it }

        val whens = scan.blocks.filter { it.trigger != null }
        val target = when (val selection = select(whens, flowName, policyKey, disambiguationIndex, "Policy")) {
            is Selection.Rejected -> return selection.result
            is Selection.Chosen -> selection.block
        }
        val old = target.trigger!!

        if (newTriggers.any { it.events.size > 1 && it.separator == null } &&
            (old.groups.size != newTriggers.size || old.groups.zip(newTriggers).any { (o, n) -> o.events.size != n.events.size })
        ) {
            return ZflEditResult.rejected(
                "A changed trigger group with more than one event needs an explicit separator; use COMMA or OR."
            )
        }

        // Same number of groups: rewrite only the groups whose separators the caller changed.
        // A group that keeps its tokens, including one passed with a null separator, is left
        // character for character apart from event names that differ.
        val edits = if (old.groups.size == newTriggers.size) {
            val rewritesAGroup = old.groups.zip(newTriggers).any { (group, requested) ->
                !keepsSeparatorTokens(group, requested)
            }
            if (rewritesAGroup && old.hasComments) return commentRewriteRejection(target.key)
            old.groups.zip(newTriggers).flatMap { (group, requested) ->
                if (keepsSeparatorTokens(group, requested)) eventNameEdits(group, requested)
                else listOf(ZflTextEdit(group.start, group.end, renderGroup(requested)))
            }
        } else {
            if (old.hasComments) return commentRewriteRejection(target.key)
            listOf(ZflTextEdit(old.start, old.end, buildTriggerText(newTriggers)))
        }
        if (edits.isEmpty()) return ZflEditResult.noOp()
        return finish(source, edits, emptyList())
    }

    // -------------------------------------------------------------------------
    // Operation 3: Rename a command (flow-local)
    // -------------------------------------------------------------------------

    /**
     * Renames the command [oldName] within [flowName], updating its `do` and `when ... do` headers,
     * `call` and `on X call` targets and unquoted `@compensates(...)` values.
     *
     * Returns an [ZflIdentityMapping] so that selection survives the rename (ZVE-R3). Other flows are
     * not touched (ZVE-R7). The operation is rejected when [oldName] is not a command of the flow,
     * when [newName] is not one ASCII identifier, when [newName] already names or is called as a
     * command in the flow, and in the recorded limitations listed on [ZflEditor].
     *
     * spec:flows/zfl-visual-editing#zve-r3
     * spec:flows/zfl-visual-editing#zve-r7
     * spec:flows/zfl-visual-editing#zve-r8
     */
    fun renameCommand(
        source: String,
        flowName: String,
        oldName: String,
        newName: String
    ): ZflEditResult {
        if (!isIdentifier(newName)) return ZflEditResult.rejected(notAnIdentifier("command name", newName))
        val scan = scanFlow(source, flowName, oldName) ?: return syntaxRejection()
        scan.flowRejection(flowName)?.let { return it }

        if (oldName !in scan.declared) {
            return ZflEditResult.rejected(
                "Command '$oldName' is not declared in flow '$flowName'. " +
                    "Only flow-local commands (do-blocks) can be renamed here."
            )
        }
        if (oldName == newName) return ZflEditResult.noOp()

        if (newName in scan.commandRefs.keys || newName in scan.compensatesNames || newName in scan.listedCommands) {
            return ZflEditResult.rejected(
                "Command '$newName' already exists as a command or reference in flow '$flowName'; " +
                    "renaming '$oldName' onto it would capture that reference or merge two commands."
            )
        }
        if (oldName in scan.listedCommands) {
            return ZflEditResult.rejected(
                "Command '$oldName' is listed in a systems `commands:` list; renaming it here would leave that list " +
                    "stale (scoped rename: unsupported until ES-P2)."
            )
        }
        if (scan.quotedCompensates) {
            return ZflEditResult.rejected(
                "A quoted or non-plain @compensates value refers to '$oldName'; renaming it would leave that reference stale " +
                    "(unsupported until ES-P2)."
            )
        }

        val edits = scan.commandRefs.getValue(oldName).sortedBy { it.first }
            .map { (start, end) -> ZflTextEdit(start, end, newName) }
        val mapping = ZflIdentityMapping(flowName = flowName, kind = "command", oldName = oldName, newName = newName)
        return finish(source, edits, listOf(mapping))
    }

    // -------------------------------------------------------------------------
    // Targeting
    // -------------------------------------------------------------------------

    /** The analyzer-compatible occurrence keys of [flowName], in source order. For tests. */
    internal fun occurrenceKeys(source: String, flowName: String): List<String> =
        scanFlow(source, flowName)?.blocks?.map { it.key } ?: emptyList()

    private sealed class Selection {
        class Chosen(val block: Block) : Selection()
        class Rejected(val result: ZflEditResult) : Selection()
    }

    private fun select(
        blocks: List<Block>,
        flowName: String,
        key: String,
        index: Int?,
        label: String
    ): Selection {
        if (EXPLICIT_KEY.containsMatchIn(key)) {
            if (index != null) {
                return Selection.Rejected(ZflEditResult.rejected(
                    "$label '$key' already selects one block with its #n suffix; disambiguationIndex cannot be combined with it."
                ))
            }
            val block = blocks.find { it.key == key }
                ?: return Selection.Rejected(ZflEditResult.rejected("$label '$key' not found in flow '$flowName'."))
            return Selection.Chosen(block)
        }
        val candidates = blocks.filter { it.baseKey == key }
        if (candidates.isEmpty()) {
            return Selection.Rejected(ZflEditResult.rejected("$label '$key' not found in flow '$flowName'."))
        }
        if (index == null) {
            if (candidates.size > 1) {
                return Selection.Rejected(ZflEditResult.rejected(
                    "$label '$key' is ambiguous: ${candidates.size} blocks in flow '$flowName' share this key. " +
                        "Select one with disambiguationIndex (0..${candidates.size - 1}, in source order) " +
                        "or with the analyzer's '#n' key. The editor does not guess (ZVE-R13)."
                ))
            }
            return Selection.Chosen(candidates.single())
        }
        val block = candidates.getOrNull(index)
            ?: return Selection.Rejected(ZflEditResult.rejected(
                "disambiguationIndex=$index is out of range for '$key' in flow '$flowName' (found ${candidates.size})."
            ))
        return Selection.Chosen(block)
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private fun syntaxRejection() = ZflEditResult.rejected("Source has syntax errors; cannot apply edits.")

    private class Analysis(
        val syntaxErrors: List<String>,
        val errors: List<String>,
        val warnings: List<String>,
        val problems: Map<String, Int>,
        val constructionFailure: String? = null
    )

    private fun analyze(text: String): Analysis {
        val parser = ZflParser()
        val syntax = parser.parse(text).syntaxProblems.map { it["message"]?.toString() ?: "syntax error" }
        if (syntax.isNotEmpty()) return Analysis(syntax, emptyList(), emptyList(), emptyMap())
        // Construction can abort after the grammar has accepted the text, for example an integer
        // outside Long range. The parser records that as a fatal problem and returns whatever it
        // built, which may contain no flows. Semantic results from that model must not decide an edit.
        val model = try {
            parser.parseModel(text)
        } catch (e: Exception) {
            return Analysis(
                syntaxErrors = emptyList(),
                errors = emptyList(),
                warnings = emptyList(),
                problems = emptyMap(),
                constructionFailure = e.message?.takeIf { it.isNotBlank() }
                    ?: e::class.simpleName
                    ?: "model construction failed"
            )
        }
        val fatal = model.getProblems().mapNotNull { problem ->
            if (problem["path"] as? String != "parser") return@mapNotNull null
            problem["message"]?.toString()?.takeIf { it.isNotBlank() }
                ?: "Parser failed while building ZFL model"
        }
        if (fatal.isNotEmpty()) {
            return Analysis(
                syntaxErrors = emptyList(),
                errors = emptyList(),
                warnings = emptyList(),
                problems = emptyMap(),
                constructionFailure = fatal.joinToString("; ")
            )
        }
        val semantic = ZflSemanticAnalyzer().analyze(model)
        return Analysis(
            syntaxErrors = emptyList(),
            errors = semantic.diagnostics.filter { it.severity == Severity.ERROR }.map { it.message },
            warnings = semantic.diagnostics.filter { it.severity == Severity.WARNING }.map { it.message },
            problems = model.getProblems().groupingBy { "${it["message"]}|${it["value"]}" }.eachCount()
        )
    }

    /**
     * Applies [edits] to a copy of [source], re-parses and re-analyzes it, and accepts only text without
     * syntax errors, error diagnostics or parser problems that the original did not have. A document that
     * does not build a model, or whose parser reports a fatal problem, is rejected instead of compared.
     * Warnings the edit introduces are returned in the accepted result (ZVE-R5).
     */
    private fun finish(source: String, edits: List<ZflTextEdit>, mappings: List<ZflIdentityMapping>): ZflEditResult {
        val original = analyze(source)
        if (original.syntaxErrors.isNotEmpty()) {
            return ZflEditResult.rejected("Source has syntax errors; cannot apply edits: ${original.syntaxErrors.joinToString("; ")}")
        }
        original.constructionFailure?.let { return constructionRejection(it, edited = false) }
        if (original.errors.isNotEmpty()) {
            return ZflEditResult.rejected("Source has semantic errors; cannot apply edits: ${original.errors.joinToString("; ")}")
        }
        val candidate = analyze(applyEdits(source, edits))
        if (candidate.syntaxErrors.isNotEmpty()) {
            return ZflEditResult.rejected("Edit produces syntax errors: ${candidate.syntaxErrors.joinToString("; ")}")
        }
        candidate.constructionFailure?.let { return constructionRejection(it, edited = true) }
        if (candidate.errors.isNotEmpty()) {
            return ZflEditResult.rejected("Edit produces semantic errors: ${candidate.errors.joinToString("; ")}")
        }
        val introduced = candidate.problems.filter { (message, count) -> count > (original.problems[message] ?: 0) }.keys
        if (introduced.isNotEmpty()) {
            return ZflEditResult.rejected(
                "Edit produces model problems: ${introduced.joinToString("; ") { it.substringBefore('|') }}"
            )
        }
        val newWarnings = introducedWarnings(original.warnings, candidate.warnings, mappings)
        return ZflEditResult(
            accepted = true,
            edits = edits,
            diagnostics = newWarnings.map { ZflEditDiagnostic(EditSeverity.WARNING, it) },
            identityMappings = mappings
        )
    }

    /**
     * Warnings the edit introduces. An exact original warning is the same warning, including one that
     * another flow still has for the old command name. A warning that only differs by the rename is the
     * renamed form of an original warning, not a new one.
     */
    private fun introducedWarnings(
        original: List<String>,
        candidate: List<String>,
        mappings: List<ZflIdentityMapping>
    ): List<String> {
        fun renamed(warning: String): String = mappings.fold(warning) { current, mapping ->
            current.replace("'${mapping.oldName}'", "'${mapping.newName}'")
        }
        val remaining = original.toMutableList()
        val pending = mutableListOf<String>()
        for (warning in candidate) {
            val exact = remaining.indexOf(warning)
            if (exact >= 0) remaining.removeAt(exact) else pending += warning
        }
        val introduced = mutableListOf<String>()
        for (warning in pending) {
            val source = remaining.indexOfFirst { renamed(it) == warning }
            if (source >= 0) remaining.removeAt(source) else introduced += warning
        }
        return introduced
    }

    /** True when [name] is exactly one ZFL `ID` token: ASCII, not a keyword, nothing else (ZVE-R5). */
    private fun isIdentifier(name: String): Boolean {
        val lexer = ZflLexer(CharStreams.fromString(name))
        lexer.removeErrorListeners()
        val first = lexer.nextToken()
        return first.type == ZflLexer.Tokens.ID && first.text == name && lexer.nextToken().type == Token.EOF
    }

    private fun notAnIdentifier(what: String, name: String) =
        "'$name' is not a valid $what: ZFL identifiers are one ASCII token of letters, digits and '_', " +
            "not starting with a digit and not a keyword."

    private fun constructionRejection(failure: String, edited: Boolean) = ZflEditResult.rejected(
        if (edited) "The edited document could not be analysed: $failure"
        else "The document could not be analysed: $failure"
    )

    private fun commentRewriteRejection(key: String) = ZflEditResult.rejected(
        "The trigger of '$key' holds comments, and the change alters its structure; " +
            "rewriting it would remove them (unsupported until ES-P2)."
    )

    /**
     * True when [requested] can be applied by editing event names and leaving every separator token
     * where it is. A null separator means keep what is there. A one-event group's separator is
     * immaterial, including a trailing one. A different event count has to be rewritten.
     */
    private fun keepsSeparatorTokens(group: TriggerGroupInfo, requested: TriggerGroup): Boolean {
        if (group.events.size != requested.events.size) return false
        return when {
            requested.separator == null -> true
            group.events.size == 1 -> true
            else -> group.wellFormed && group.separator == requested.separator
        }
    }

    private fun eventNameEdits(group: TriggerGroupInfo, requested: TriggerGroup): List<ZflTextEdit> =
        group.events.zip(requested.events)
            .filter { (token, name) -> token.text != name }
            .map { (token, name) -> ZflTextEdit(token.start, token.end, name) }

    private fun triggerInputRejection(groups: List<TriggerGroup>): ZflEditResult? {
        if (groups.isEmpty() || groups.any { it.events.isEmpty() }) {
            return ZflEditResult.rejected("A trigger needs at least one group, and every group at least one event.")
        }
        val events = groups.flatMap { it.events }
        events.firstOrNull { !isIdentifier(it) }?.let { return ZflEditResult.rejected(notAnIdentifier("event name", it)) }
        events.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.firstOrNull()?.let {
            return ZflEditResult.rejected("Duplicate trigger event '$it'.")
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Text builders
    // -------------------------------------------------------------------------

    private fun renderGroup(group: TriggerGroup): String =
        group.events.joinToString(if (group.separator == TriggerSeparator.OR) " | " else ", ")

    private fun buildTriggerText(groups: List<TriggerGroup>): String =
        groups.joinToString(" and ", transform = ::renderGroup)

    /**
     * Builds the insertion of `emits [eventName]` before the closing brace of [body].
     *
     * When the closing brace is the first thing on its line the new statement gets its own line, before
     * the brace line, indented like the last statement (or one level inside the brace) and ended with the
     * file's own line break. Otherwise the body is on one line and the statement follows the last token.
     */
    private fun emissionEdit(source: String, body: BodyInfo, eventName: String): ZflTextEdit {
        val rbrace = body.rbraceStart
        var lineStart = rbrace
        while (lineStart > 0 && source[lineStart - 1] in " \t") lineStart--
        val braceOwnsLine = lineStart == 0 || source[lineStart - 1] == '\n'
        if (braceOwnsLine) {
            val braceIndent = source.substring(lineStart, rbrace)
            val indent = body.lastStatementStart?.let { leadingIndent(source, it) }
                ?: (braceIndent + if ('\t' in braceIndent) "\t" else "    ")
            val eol = if (lineStart >= 2 && source[lineStart - 2] == '\r') "\r\n" else "\n"
            return ZflTextEdit(lineStart, lineStart, "${indent}emits $eventName$eol")
        }
        val at = body.lastStatementEnd ?: body.lbraceEnd
        val trailing = if (source.getOrNull(at) == '}') " " else ""
        return ZflTextEdit(at, at, " emits $eventName$trailing")
    }

    /** The whitespace before [offset] on its line, or null when other text precedes it on that line. */
    private fun leadingIndent(source: String, offset: Int): String? {
        var i = offset
        while (i > 0 && source[i - 1] in " \t") i--
        return if (i == 0 || source[i - 1] == '\n') source.substring(i, offset) else null
    }

    private companion object {
        val EXPLICIT_KEY = Regex("#\\d+$")
    }
}

// -------------------------------------------------------------------------
// Structured trigger input
// -------------------------------------------------------------------------

/** The separator between the events of one trigger group: `A, B` or `A | B`. */
enum class TriggerSeparator { COMMA, OR }

/**
 * A group of trigger events joined by [separator]. Several groups are joined by `and`.
 * The separator of a one-event group does not matter, including a trailing separator, so an
 * unchanged one-event group is a no-op whatever separator is requested. Null keeps the separator
 * tokens already written when the group has the same number of events.
 *
 * spec:flows/zfl-visual-editing#zve-r6
 */
data class TriggerGroup(
    val events: List<String>,
    val separator: TriggerSeparator? = null
)

// -------------------------------------------------------------------------
// Source scan
// -------------------------------------------------------------------------

/** Maps ANTLR's code-point offsets to UTF-16 string indexes. Identity when the text has no surrogates. */
private class Utf16Offsets(text: String) {
    private val starts: IntArray? = if (text.none { it.isSurrogate() }) null else {
        val list = ArrayList<Int>(text.length + 1)
        var i = 0
        while (i < text.length) {
            list.add(i)
            i += if (text[i].isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
        }
        list.add(text.length)
        list.toIntArray()
    }

    fun at(codePointIndex: Int): Int = starts?.get(codePointIndex) ?: codePointIndex
}

private class Range(val start: Int, val end: Int, val text: String)

private class BodyInfo(
    val lbraceEnd: Int,
    val rbraceStart: Int,
    val lastStatementStart: Int?,
    val lastStatementEnd: Int?
)

private class TriggerGroupInfo(
    val events: List<Range>,
    val separator: TriggerSeparator?,
    val wellFormed: Boolean,
    val start: Int,
    val end: Int
)

private class TriggerInfo(val start: Int, val end: Int, val groups: List<TriggerGroupInfo>, val hasComments: Boolean)

/** One `do` or `when ... do` block of the flow, keyed as the analyzer keys its occurrences. */
private class Block(
    val key: String,
    val baseKey: String,
    val body: BodyInfo?,
    val unconditionalEmits: Set<String>,
    val trigger: TriggerInfo?,
    var inheritsBody: Boolean = false
)

/**
 * Everything the three operations need from one walk over the tree: the blocks of the target flow, the
 * ranges of every command name written in it, and the references that rename must treat specially.
 *
 * Keys mirror `ZflListenerImpl.createOccurrence`: `name@definition` or `name@when[distinct sorted triggers]`,
 * and `#n` from the second block with the same base key. Blocks without a body count too.
 */
private class FlowScan(
    private val source: String,
    private val targetFlow: String,
    private val renaming: String?,
    private val offsets: Utf16Offsets
) : ZflBaseListener() {

    var flowCount = 0
    val blocks = mutableListOf<Block>()
    val declared = mutableSetOf<String>()
    val commandRefs = linkedMapOf<String, MutableList<Pair<Int, Int>>>()
    val listedCommands = mutableSetOf<String>()
    val compensatesNames = mutableSetOf<String>()
    var quotedCompensates = false

    private var inFlow = false
    private val keyCounts = mutableMapOf<String, Int>()

    fun flowRejection(flowName: String): ZflEditResult? = when {
        flowCount == 0 -> ZflEditResult.rejected("Flow '$flowName' not found.")
        flowCount > 1 -> ZflEditResult.rejected("Flow name '$flowName' is used by $flowCount flows; the target is ambiguous.")
        else -> null
    }

    private fun startOf(token: Token) = offsets.at(token.startIndex)
    private fun endOf(token: Token) = offsets.at(token.stopIndex + 1)

    private fun range(ctx: ParserRuleContext): Range? {
        val start = startOf(ctx.start ?: return null)
        val end = endOf(ctx.stop ?: return null)
        return Range(start, end, source.substring(start, end))
    }

    override fun enterFlow(ctx: AntlrParser.FlowContext) {
        inFlow = ctx.flow_name().text == targetFlow
        if (inFlow) flowCount++
    }

    override fun exitFlow(ctx: AntlrParser.FlowContext) {
        inFlow = false
    }

    override fun enterSystem_service_command_list(ctx: AntlrParser.System_service_command_listContext) {
        ctx.ID().forEach { listedCommands += it.text }
    }

    override fun enterFlow_when(ctx: AntlrParser.Flow_whenContext) {
        if (!inFlow) return
        val name = ctx.flow_command_name()
        declare(name)

        val groups = ctx.flow_when_trigger().flow_when_trigger_group().map { group ->
            val events = group.flow_when_event_trigger().mapNotNull { range(it) }
            val commas = group.COMMA().size
            val ors = group.OR().size
            val span = range(group)!!
            TriggerGroupInfo(
                events = events,
                separator = when {
                    commas > 0 -> TriggerSeparator.COMMA
                    ors > 0 -> TriggerSeparator.OR
                    else -> null
                },
                wellFormed = commas + ors == events.size - 1 && (commas == 0 || ors == 0),
                start = span.start,
                end = span.end
            )
        }
        val triggerRange = range(ctx.flow_when_trigger())!!
        val trigger = TriggerInfo(
            start = triggerRange.start,
            end = triggerRange.end,
            groups = groups,
            hasComments = "//" in triggerRange.text || "/*" in triggerRange.text
        )
        val triggerNames = groups.flatMap { g -> g.events.map { it.text } }.distinct().sorted()
        val doBody = ctx.flow_do_body()
        addBlock(
            baseKey = "${name.text}@when[${triggerNames.joinToString(",")}]",
            body = doBody?.let { bodyInfo(ctx.LBRACE(), ctx.RBRACE(), it) },
            doBody = doBody,
            trigger = trigger
        )
    }

    override fun enterFlow_do(ctx: AntlrParser.Flow_doContext) {
        if (!inFlow) return
        val name = ctx.flow_command_name()
        declare(name)
        val doBody = ctx.flow_do_body()
        addBlock(
            baseKey = "${name.text}@definition",
            body = bodyInfo(ctx.LBRACE(), ctx.RBRACE(), doBody),
            doBody = doBody,
            trigger = null
        )
    }

    override fun enterFlow_do_call(ctx: AntlrParser.Flow_do_callContext) {
        if (inFlow) reference(ctx.flow_command_name())
    }

    override fun enterFlow_do_on(ctx: AntlrParser.Flow_do_onContext) {
        if (!inFlow) return
        ctx.flow_command_name()?.let { reference(it) }
    }

    override fun enterOption(ctx: AntlrParser.OptionContext) {
        if (!inFlow || renaming == null) return
        val optionName = ctx.option_name().text.replace("@", "")
        if (optionName != "compensates" && optionName != "compensate") return
        val value = ctx.option_value() ?: return
        val simpleValues = mutableListOf<AntlrParser.SimpleContext>()
        ParseTreeWalker.DEFAULT.walk(object : ZflBaseListener() {
            override fun enterSimple(simple: AntlrParser.SimpleContext) {
                simpleValues += simple
            }
        }, value)
        simpleValues.forEach { simple ->
            val name = simpleName(simple)
            if (name != null) compensatesNames += name
            if (name != renaming) return@forEach
            val isPlain = simple.ID() != null && isPlainCompensatesValue(value, simple)
            if (isPlain) {
                range(simple)?.let { commandRefs.getOrPut(renaming) { mutableListOf() }.add(it.start to it.end) }
            } else {
                quotedCompensates = true
            }
        }
    }

    private fun isPlainCompensatesValue(
        value: AntlrParser.Option_valueContext,
        simple: AntlrParser.SimpleContext
    ): Boolean {
        val complex = value.complex_value()
        return complex.value()?.simple() === simple || simple in (complex.array_plain()?.simple() ?: emptyList())
    }

    private fun simpleName(simple: AntlrParser.SimpleContext): String? = when {
        simple.ID() != null -> simple.text
        simple.SINGLE_QUOTED_STRING() != null || simple.DOUBLE_QUOTED_STRING() != null ->
            simple.text.drop(1).dropLast(1)
        else -> null
    }

    private fun declare(ctx: AntlrParser.Flow_command_nameContext) {
        declared += ctx.text
        reference(ctx)
    }

    private fun reference(ctx: AntlrParser.Flow_command_nameContext) {
        val r = range(ctx) ?: return
        commandRefs.getOrPut(ctx.text) { mutableListOf() }.add(r.start to r.end)
    }

    private fun addBlock(baseKey: String, body: BodyInfo?, doBody: AntlrParser.Flow_do_bodyContext?, trigger: TriggerInfo?) {
        val seen = keyCounts.getOrElse(baseKey) { 0 }
        keyCounts[baseKey] = seen + 1
        blocks += Block(
            key = if (seen == 0) baseKey else "$baseKey#${seen + 1}",
            baseKey = baseKey,
            body = body,
            unconditionalEmits = doBody?.let(::unconditionalEmits) ?: emptySet(),
            trigger = trigger
        )
    }

    fun resolveInheritedBodies() {
        blocks.forEach { block ->
            block.inheritsBody = block.body?.lastStatementEnd == null && blocks.any { other ->
                other !== block && other.baseKey.substringBefore('@') == block.baseKey.substringBefore('@') &&
                    other.body?.lastStatementEnd != null
            }
        }
    }

    private fun bodyInfo(lbrace: TerminalNode?, rbrace: TerminalNode?, body: AntlrParser.Flow_do_bodyContext): BodyInfo? {
        val open = lbrace?.symbol ?: return null
        val close = rbrace?.symbol ?: return null
        val last = body.flow_do_statement().lastOrNull()
        return BodyInfo(
            lbraceEnd = endOf(open),
            rbraceStart = startOf(close),
            lastStatementStart = last?.start?.let(::startOf),
            lastStatementEnd = last?.stop?.let(::endOf)
        )
    }

    /** Events of direct, unannotated `emits` statements. */
    private fun unconditionalEmits(body: AntlrParser.Flow_do_bodyContext): Set<String> =
        body.flow_do_statement().mapNotNull { it.flow_do_signal() }
            .filter { signal ->
                signal.flow_signal_body().EMITS() != null &&
                    signal.annotations().option().isEmpty()
            }
            .flatMap { it.flow_signal_body().flow_event_list().flow_event_name().map { name -> name.text } }
            .toSet()
}

/** Parses [source] and scans [flowName]; null when the source has syntax errors. */
private fun scanFlow(source: String, flowName: String, renaming: String? = null): FlowScan? {
    val parseResult = ZflParser().parse(source)
    if (parseResult.syntaxProblems.isNotEmpty()) return null
    val scan = FlowScan(source, flowName, renaming, Utf16Offsets(source))
    ParseTreeWalker.DEFAULT.walk(scan, parseResult.tree)
    scan.resolveInheritedBodies()
    return scan
}
