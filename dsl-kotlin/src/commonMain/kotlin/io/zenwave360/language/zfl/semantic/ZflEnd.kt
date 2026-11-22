package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflEnd(
    val description: String? = null,
    /** Every event of each outcome, the lines of a repeated outcome merged. */
    val endOutcomes: Map<String, List<String>> = emptyMap(),
    val sourceRef: SourceRef,
    /** The outcomes in order, each with the final-state variants of its lines. */
    val outcomes: List<ZflEndOutcome> = emptyList(),
)

/** One outcome of `end`. An outcome written on several lines is one outcome with several [variants]. */
@Serializable
data class ZflEndOutcome(
    val name: String,
    val variants: List<ZflEndVariant> = emptyList(),
) {
    val events: List<String> get() = variants.flatMap { it.events }.distinct()
}

/**
 * One line of an outcome: any of [events] ends the flow there, and once the instance is at rest each view
 * aggregate of [expectedStates] is in one of its states. [NONE] means the aggregate does not exist. An aggregate
 * absent from the map is not checked.
 */
@Serializable
data class ZflEndVariant(
    val events: List<String>,
    val expectedStates: Map<String, List<String>> = emptyMap(),
    val sourceRef: SourceRef,
) {
    companion object {
        const val NONE = "none"
    }
}
