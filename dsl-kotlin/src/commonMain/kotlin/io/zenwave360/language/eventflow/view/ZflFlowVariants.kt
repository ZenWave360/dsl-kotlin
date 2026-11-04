package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.semantic.ZflFlow

/** A selectable execution path through a ZFL flow. */
data class FlowVariant(
    val id: String,
    val title: String
)

data class ZflFlowVariant(
    val variant: FlowVariant,
    val path: SequencePath
)

/**
 * Enumerates variants in precisely the order used by ZFL sequence diagrams.
 * Keeping the identifier and title construction here lets every consumer name
 * the same execution path without depending on dsl-views.
 */
fun zflFlowVariants(
    flow: ZflFlow,
    maxCommandVisitsPerPath: Int = 2
): List<ZflFlowVariant> {
    val paths = ZflSequencePaths(flow, maxCommandVisitsPerPath).paths()
    return flow.end.endOutcomes.keys
        .flatMap { outcome -> paths.filter { it.endOutcome == outcome } }
        .mapIndexed { index, path ->
            ZflFlowVariant(
                variant = FlowVariant(
                    id = "sequence:${path.endOutcome}:$index",
                    title = sequenceVariantTitle(path)
                ),
                path = path
            )
        }
}

fun sequenceVariantTitle(path: SequencePath): String = buildString {
    append(path.endOutcome)
    append(": from ")
    append(path.start.name)
    if (path.choiceLabels.isNotEmpty()) {
        append(" via ")
        append(path.choiceLabels.joinToString(" → "))
    }
}
