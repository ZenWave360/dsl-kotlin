package io.zenwave360.language.eventflow.view

import kotlinx.serialization.Serializable

/**
 * Semantic types of nodes in an event flow.
 */
@Serializable
enum class FlowNodeType {
    START,
    COMMAND,
    EVENT,
    POLICY
}
