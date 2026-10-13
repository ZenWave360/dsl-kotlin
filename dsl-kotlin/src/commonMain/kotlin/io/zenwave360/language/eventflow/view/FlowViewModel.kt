package io.zenwave360.language.eventflow.view

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Unified view model for an event-driven flow.
 *
 * Contains both semantic properties (always populated) and optional layout
 * properties (populated after the layout engine runs).
 */
@Serializable
data class FlowViewModel(
    val schema: String = "zfl.eventflow.view@1",
    val nodes: List<FlowNode>,
    val edges: List<FlowEdge>,
    /** Layout algorithm metadata. Null until layout is applied. */
    val layout: LayoutMetadata? = null,
    /** Overall bounding box of the entire flow diagram. Null until layout is applied. */
    val bounds: FlowBounds? = null,
    /** System groupings (swim lanes). Null until layout is applied. */
    val systemGroups: List<FlowSystemGroupView>? = null
) {
    /** Returns true if all nodes have positions assigned (layout has been applied). */
    fun hasLayout(): Boolean = nodes.isNotEmpty() && nodes.all { it.position != null }

    /** Returns a copy of this view model with all layout data removed. */
    fun withoutLayout(): FlowViewModel = copy(
        nodes = nodes.map { it.copy(position = null, dimensions = null) },
        layout = null,
        bounds = null,
        systemGroups = null
    )

    /**
     * Converts this FlowViewModel to a JSON string.
     *
     * @param pretty If true, formats the JSON with indentation for readability. Default is false.
     * @return JSON string representation of this FlowViewModel
     */
    fun toJson(pretty: Boolean = false): String {
        val json = if (pretty) {
            Json { prettyPrint = true; encodeDefaults = true }
        } else {
            Json { encodeDefaults = true }
        }
        return json.encodeToString(this)
    }

    /** Alias for toJson(pretty = true) for backward compatibility. */
    fun toJsonString(): String = toJson(pretty = true)

    companion object {
        /** Deserializes a FlowViewModel from a JSON string. */
        fun fromJson(json: String): FlowViewModel =
            Json { isLenient = true }.decodeFromString(json)
    }
}
