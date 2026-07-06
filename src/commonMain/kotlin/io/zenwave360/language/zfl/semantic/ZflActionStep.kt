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
    val async: Boolean = false,
    val handlers: List<ZflEndOutcomeHandler> = emptyList()
) : ZflActionStep

@Serializable
@SerialName("signal")
data class ZflSignalStep(
    val endOutcome: String,
    val emits: Boolean = false,
    val response: Boolean = false,
    val options: Map<String, String?> = emptyMap()
) : ZflActionStep

@Serializable
data class ZflEndOutcomeHandler(
    val endOutcome: String,
    val action: String? = null,
    val signal: ZflHandlerSignal? = null
)

@Serializable
data class ZflHandlerSignal(
    val events: List<String> = emptyList(),
    val emits: Boolean = false,
    val response: Boolean = false,
    val options: Map<String, String?> = emptyMap(),
    val outcome: String? = null
)
