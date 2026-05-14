package io.zenwave360.language.eventflow.view

import kotlinx.serialization.Serializable

/**
 * Semantic meaning of a relationship between nodes.
 */
@Serializable
enum class FlowEdgeType {
    CAUSATION,
    CALL,
    OUTCOME_HANDLER,
    TRIGGER,
    CONDITIONAL,
    ERROR
}
