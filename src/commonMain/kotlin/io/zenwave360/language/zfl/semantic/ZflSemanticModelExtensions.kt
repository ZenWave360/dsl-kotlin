package io.zenwave360.language.zfl.semantic

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Converts ZflSemanticModel to JSON string.
 *
 * @param pretty If true, formats the JSON with indentation for readability. Default is false.
 * @return JSON string representation of the ZflSemanticModel
 */
fun ZflSemanticModel.toJson(pretty: Boolean = false): String {
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
fun ZflSemanticModel.toJsonString(): String = toJson(pretty = true)

