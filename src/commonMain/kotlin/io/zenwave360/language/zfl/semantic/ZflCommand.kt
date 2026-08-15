package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflEmission(
    val eventName: String,
    val outcome: String? = null,
    val failure: Boolean = false,
    val sourceRef: SourceRef? = null,
)

@Serializable
data class ZflResponse(
    val name: String,
    val outcome: String? = null,
    val options: Map<String, String?> = emptyMap(),
    val sourceRef: SourceRef? = null,
)

/**
 * One execution occurrence of a logical operation declared by ZFL `do` syntax.
 *
 * Occurrence-local documentation, triggers, options, steps, emissions, responses and source
 * location are intentionally retained here instead of being merged into the logical operation.
 */
@Serializable
data class ZflCommandOccurrence(
    val key: String,
    val index: Int,
    val description: String? = null,
    val triggers: List<String> = emptyList(),
    val compensates: String? = null,
    val actor: String? = null,
    val timer: String? = null,
    val options: Map<String, String?> = emptyMap(),
    val system: String? = null,
    val service: String? = null,
    val servicePath: String? = null,
    val steps: List<ZflActionStep> = emptyList(),
    val emissions: List<ZflEmission> = emptyList(),
    val responses: List<ZflResponse> = emptyList(),
    val sourceRef: SourceRef,
)

/**
 * A logical operation declared with ZFL `do` syntax.
 *
 * It is not necessarily an externally transported EventCatalog command. The legacy merged fields
 * remain the compatibility view; [occurrences] is the lossless execution model.
 */
@Serializable
data class ZflCommand(
    val name: String,
    val description: String? = null,
    val system: String?,
    val service: String?,
    val servicePath: String?,
    val actor: String?,
    val emits: List<ZflEmission> = emptyList(),
    val responses: List<String> = emptyList(),
    val steps: List<ZflActionStep> = emptyList(),
    val occurrences: List<ZflCommandOccurrence> = emptyList(),
    val sourceRef: SourceRef,
)
