package io.zenwave360.language.eventflow.view

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

/**
 * A node in an event-driven flow.
 *
 * Semantic properties (always populated) and optional layout properties
 * (populated after the layout engine runs).
 */
@Serializable
data class FlowNode(
    val id: String,
    val type: FlowNodeType,
    val label: String,
    val system: String?,
    val service: String?,
    val sourceRef: SourceRef,
    /** Marks this event as a terminal outcome when present. */
    val endOutcomeLabels: List<String>? = null,
    /** Absolute position (x, y) in the canvas. Null until layout is applied. */
    val position: Point? = null,
    /** Width and height of the node. Null until layout is applied. */
    val dimensions: Dimensions? = null
)

@Serializable
data class Point(val x: Double, val y: Double)

@Serializable
data class Dimensions(val width: Double, val height: Double)
