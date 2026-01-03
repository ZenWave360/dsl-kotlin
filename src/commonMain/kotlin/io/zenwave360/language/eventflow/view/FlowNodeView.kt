package io.zenwave360.language.eventflow.view

import io.zenwave360.language.eventflow.ir.FlowNodeType
import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class FlowNodeView(
    val id: String,
    val type: FlowNodeType,
    val label: String,
    val position: Point,
    val dimensions: Dimensions,
    val system: String?,
    val sourceRef: SourceRef
)

@Serializable
data class Point(val x: Double, val y: Double)

@Serializable
data class Dimensions(val width: Double, val height: Double)
