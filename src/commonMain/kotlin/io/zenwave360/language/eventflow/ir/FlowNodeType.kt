package io.zenwave360.language.eventflow.ir

import kotlinx.serialization.Serializable

/**
 * Semantic types of nodes in an event flow.
 */
@Serializable
enum class FlowNodeType {
    COMMAND,
    EVENT,
    POLICY
}
