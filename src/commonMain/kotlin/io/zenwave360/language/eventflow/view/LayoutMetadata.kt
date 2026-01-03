package io.zenwave360.language.eventflow.view

import kotlinx.serialization.Serializable

@Serializable
data class LayoutMetadata(
    val engine: String,
    val direction: Direction,
    val rankSpacing: Double,
    val nodeSpacing: Double
)

@Serializable
enum class Direction {
    LR, TB
}
