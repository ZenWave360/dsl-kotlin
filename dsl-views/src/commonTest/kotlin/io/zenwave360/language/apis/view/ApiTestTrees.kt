package io.zenwave360.language.apis.view

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** JSON as the Map/List trees dsl-docs produces: every scalar a string, document order kept. */
internal fun jsonTree(text: String): Any? = convert(Json.parseToJsonElement(text))

private fun convert(e: JsonElement): Any? = when (e) {
    is JsonObject -> linkedMapOf<String, Any?>().also { m -> e.forEach { (k, v) -> m[k] = convert(v) } }
    is JsonArray -> e.map { convert(it) }
    is JsonNull -> null
    is JsonPrimitive -> e.content
}
