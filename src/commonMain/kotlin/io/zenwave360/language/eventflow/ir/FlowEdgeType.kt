package io.zenwave360.language.eventflow.ir

import kotlinx.serialization.Serializable

/**
 * Semantic meaning of a relationship between nodes.
 */
@Serializable
enum class FlowEdgeType {
    CAUSATION,
    TRIGGER,
    CONDITIONAL
}
