package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

/**
 * A `timer` step: armed when [after] is emitted (and fires once [duration] has passed) or at the instant of the
 * view datum [at]; disarmed when any [unless] event is emitted for the same instance. Firing runs [command].
 */
@Serializable
data class ZflTimer(
    val command: String,
    val after: String? = null,
    /** Free text, as written: `"10 minutes"`. */
    val duration: String? = null,
    /** `Aggregate.field` of a view member, for `timer at`. */
    val at: String? = null,
    val unless: List<String> = emptyList(),
    val description: String? = null,
    val options: Map<String, String?> = emptyMap(),
    val sourceRef: SourceRef,
)
