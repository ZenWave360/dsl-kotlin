package io.zenwave360.language.zfl.semantic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface ZflActionStep

@Serializable
@SerialName("service")
data class ZflServiceStep(
    val system: String,
    val service: String?,
    val servicePath: String
) : ZflActionStep

@Serializable
@SerialName("call")
data class ZflCallStep(
    val action: String,
    val handlers: List<ZflOutcomeHandler> = emptyList()
) : ZflActionStep

@Serializable
@SerialName("signal")
data class ZflSignalStep(
    val outcome: String,
    val emits: Boolean = false,
    val response: Boolean = false
) : ZflActionStep

@Serializable
data class ZflOutcomeHandler(
    val outcome: String,
    val action: String? = null,
    val emits: String? = null
)
