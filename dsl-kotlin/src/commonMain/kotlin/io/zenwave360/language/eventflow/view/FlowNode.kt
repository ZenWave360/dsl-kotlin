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
    /** What starts this flow: timer, actor, or system. Present for START nodes. */
    val startKind: String? = null,
    /** Name of the actor that starts this flow, when one is declared. */
    val actor: String? = null,
    /** `@title` of a policy; replaces its text. Present for POLICY nodes that declare it. */
    val title: String? = null,
    /** What a policy waits on: actor or timer. Absent when it declares neither. */
    val policyKind: String? = null,
    /** Timer text of a policy with `@timer`/`@time`. */
    val timer: String? = null,
    /** Id of the lane band this node is drawn in. Null when the flow is not drawn in lanes. */
    val lane: String? = null,
    /** Absolute position (x, y) in the canvas. Null until layout is applied. */
    val position: Point? = null,
    /** Width and height of the node. Null until layout is applied. */
    val dimensions: Dimensions? = null
)

@Serializable
data class Point(val x: Double, val y: Double)

@Serializable
data class Dimensions(val width: Double, val height: Double)
