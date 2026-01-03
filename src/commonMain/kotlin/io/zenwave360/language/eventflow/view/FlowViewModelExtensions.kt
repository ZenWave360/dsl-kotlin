package io.zenwave360.language.eventflow.view

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts FlowViewModel to JSON string.
 *
 * @param pretty If true, formats the JSON with indentation for readability. Default is false.
 * @return JSON string representation of the FlowViewModel
 */
fun FlowViewModel.toJson(pretty: Boolean = false): String {
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
 * Alias for toJson() for backward compatibility with existing tests.
 */
fun FlowViewModel.toJsonString(): String = toJson(pretty = true)

