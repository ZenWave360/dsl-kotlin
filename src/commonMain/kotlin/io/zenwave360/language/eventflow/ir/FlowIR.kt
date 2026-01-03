package io.zenwave360.language.eventflow.ir

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Canonical, language-agnostic representation of an event-driven flow.
 *
 * This model is semantic, deterministic, and layout-agnostic.
 */
@Serializable
data class FlowIR(
    val nodes: List<FlowNode>,
    val edges: List<FlowEdge>
) {
    /**
     * Converts this FlowIR to JSON string.
     *
     * @param pretty If true, formats the JSON with indentation for readability. Default is false.
     * @return JSON string representation of this FlowIR
     */
    fun toJson(pretty: Boolean = false): String {
        val json = if (pretty) {
            Json {
                prettyPrint = true
                encodeDefaults = true
            }
        } else {
            Json {
                encodeDefaults = true
            }
        }
        return json.encodeToString(this)
    }

    /**
     * Alias for toJson(pretty = true) for backward compatibility.
     */
    fun toJsonString(): String = toJson(pretty = true)
}
