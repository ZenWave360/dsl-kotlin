package io.zenwave360.language.eventflow.view

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MermaidDiagramsView(
    val schema: String = "zfl.mermaid.view@1",
    val flowName: String,
    val flowchart: String,
    val sequences: List<MermaidSequenceDiagram>
) {
    fun toJson(pretty: Boolean = false): String {
        val json = if (pretty) {
            Json { prettyPrint = true; encodeDefaults = true }
        } else {
            Json { encodeDefaults = true }
        }
        return json.encodeToString(this)
    }

    fun toJsonString(): String = toJson(pretty = true)

    companion object {
        fun fromJson(json: String): MermaidDiagramsView =
            Json { isLenient = true }.decodeFromString(json)
    }
}

@Serializable
data class MermaidSequenceDiagram(
    val outcome: String,
    val variant: Int,
    val mermaid: String
)
