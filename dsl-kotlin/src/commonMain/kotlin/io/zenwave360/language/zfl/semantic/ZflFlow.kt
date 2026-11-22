package io.zenwave360.language.zfl.semantic

import kotlinx.serialization.Serializable

@Serializable
data class ZflFlow(
    val name: String,
    val description: String,
    val options: Map<String, String?> = emptyMap(),
    val starts: List<ZflStart>,
    val policies: List<ZflPolicy>,
    /** Business rules from `rules` blocks. [policies] stays the `when … do` links. */
    val rules: List<ZflRule> = emptyList(),
    val commands: List<ZflCommand>,
    val events: List<ZflEvent>,
    val end: ZflEnd,
    /** The `view` block: the aggregates of one flow instance. */
    val view: ZflView? = null,
    val timers: List<ZflTimer> = emptyList(),
    val sourceRef: io.zenwave360.language.source.SourceRef? = null,
)
