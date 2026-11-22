package io.zenwave360.language.eventflow.view

import io.zenwave360.language.zfl.semantic.ZflFlow

/** A selectable execution path through a ZFL flow. */
data class FlowVariant(
    val id: String,
    val title: String,
    /** The end outcome of the path. Empty when the variant does not say. */
    val outcome: String = "",
    /** The start followed by the choices taken, in order: what [title] is written from (spec:flows/eventflow#ef-r25). */
    val steps: List<String> = emptyList()
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
                    title = sequenceVariantTitle(path),
                    outcome = path.endOutcome,
                    steps = sequenceVariantSteps(path)
                ),
                path = path
            )
        }
}

fun sequenceVariantSteps(path: SequencePath): List<String> = listOf(path.start.name) + path.choiceLabels

fun sequenceVariantTitle(path: SequencePath): String = variantTitle(sequenceVariantSteps(path), path.endOutcome)

/** `from X → a → b to Z`: the start, the choices taken and the end outcome (spec:flows/eventflow#ef-r20). */
fun variantTitle(steps: List<String>, outcome: String): String = buildString {
    append("from ")
    append(steps.firstOrNull().orEmpty())
    steps.drop(1).forEach { append(" → ").append(it) }
    append(" to ")
    append(outcome)
}
