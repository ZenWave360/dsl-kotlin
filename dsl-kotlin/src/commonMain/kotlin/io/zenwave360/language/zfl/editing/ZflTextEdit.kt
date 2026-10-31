package io.zenwave360.language.zfl.editing

import kotlinx.serialization.Serializable

/**
 * A single non-overlapping replacement in the source text.
 *
 * [start] and [end] are zero-based character offsets into the original source string
 * (end is exclusive, i.e. source[start, end) is replaced by [replacement]).
 * An insertion has start == end. A deletion has replacement == "".
 *
 * spec:flows/zfl-visual-editing.md#zve-r4
 */
@Serializable
data class ZflTextEdit(
    val start: Int,
    val end: Int,
    val replacement: String
)

/**
 * An identity mapping that maps an old symbol name (in a given flow) to its new name
 * after a rename, so that prior selections/placements can follow the symbol.
 *
 * spec:flows/zfl-visual-editing.md#zve-r3
 */
@Serializable
data class ZflIdentityMapping(
    val flowName: String,
    val kind: String, // "command" | "event"
    val oldName: String,
    val newName: String
)

/**
 * A diagnostic produced by an edit operation – may describe rejection reasons,
 * warnings, or informational messages.
 *
 * spec:flows/zfl-visual-editing.md#zve-r4
 */
@Serializable
data class ZflEditDiagnostic(
    val severity: EditSeverity,
    val message: String
)

@Serializable
enum class EditSeverity { INFO, WARNING, ERROR }

/**
 * The result of a prepare-edit call.  When [accepted] is true the [edits] list is
 * non-null and can be applied to produce valid ZFL.  When [accepted] is false the
 * [diagnostics] list explains why the operation was rejected; no edit should be applied.
 *
 * spec:flows/zfl-visual-editing.md#zve-r4, #zve-r5
 */
@Serializable
data class ZflEditResult(
    val accepted: Boolean,
    val edits: List<ZflTextEdit> = emptyList(),
    val diagnostics: List<ZflEditDiagnostic> = emptyList(),
    val identityMappings: List<ZflIdentityMapping> = emptyList()
) {
    companion object {
        fun rejected(vararg reasons: String): ZflEditResult = ZflEditResult(
            accepted = false,
            diagnostics = reasons.map { ZflEditDiagnostic(EditSeverity.ERROR, it) }
        )

        fun noOp(): ZflEditResult = ZflEditResult(
            accepted = true,
            edits = emptyList()
        )
    }
}

/**
 * Applies a list of non-overlapping [ZflTextEdit]s to [source] and returns the result.
 * Edits must be sorted by start offset; behaviour for overlapping edits is undefined.
 * Useful in tests and host adapters.
 *
 * spec:flows/zfl-visual-editing.md#zve-r8
 */
fun applyEdits(source: String, edits: List<ZflTextEdit>): String {
    val sorted = edits.sortedBy { it.start }
    val sb = StringBuilder()
    var cursor = 0
    for (edit in sorted) {
        require(edit.start >= cursor) { "Overlapping edits: cursor=$cursor edit.start=${edit.start}" }
        sb.append(source, cursor, edit.start)
        sb.append(edit.replacement)
        cursor = edit.end
    }
    sb.append(source, cursor, source.length)
    return sb.toString()
}
