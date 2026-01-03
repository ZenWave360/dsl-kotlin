package io.zenwave360.language.eventflow.view

import io.zenwave360.language.eventflow.ir.FlowEdgeType
import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class FlowEdgeView(
    val id: String,
    val source: String,
    val target: String,
    val type: FlowEdgeType,
    val label: String?,
    val sourceRef: SourceRef?
)
