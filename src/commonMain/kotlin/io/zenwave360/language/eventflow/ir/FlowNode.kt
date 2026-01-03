package io.zenwave360.language.eventflow.ir

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

/**
 * A node in an event-driven flow.
 */
@Serializable
data class FlowNode(
    val id: String,
    val type: FlowNodeType,
    val label: String,
    val system: String?,
    val sourceRef: SourceRef
)
