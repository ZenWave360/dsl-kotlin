package io.zenwave360.language.eventflow.view

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ServiceViewModel(
    val schema: String = "zfl.services.view@1",
    val groups: List<ServiceGroupView>,
    val nodes: List<ServiceNodeView>,
    val layout: LayoutMetadata? = null,
    val bounds: FlowBounds? = null
) {
    fun toJson(pretty: Boolean = false): String {
        val json = if (pretty) {
            Json { prettyPrint = true; encodeDefaults = true }
        } else {
            Json { encodeDefaults = true }
        }
        return json.encodeToString(this)
    }

    companion object {
        fun fromJson(json: String): ServiceViewModel =
            Json { isLenient = true }.decodeFromString(json)
    }
}

@Serializable
data class ServiceGroupView(
    val id: String,
    val label: String,
    val path: String,
    val position: Point? = null,
    val dimensions: Dimensions? = null
)

@Serializable
data class ServiceNodeView(
    val id: String,
    val type: ServiceNodeType,
    val label: String,
    val groupId: String,
    val system: String?,
    val service: String?,
    val servicePath: String?,
    val sourceRef: SourceRef,
    val position: Point? = null,
    val dimensions: Dimensions? = null
)

@Serializable
enum class ServiceNodeType {
    COMMAND,
    EVENT
}
