package io.zenwave360.language.eventflow.ir

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

/**
 * Directed relationship between two flow nodes.
 */
@Serializable
data class FlowEdge(
    val id: String,
    val source: String,
    val target: String,
    val type: FlowEdgeType,
    val label: String? = null,
    val sourceRef: SourceRef? = null
)
