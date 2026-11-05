package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

@Serializable
data class ZflPolicy(
    val description: String?,
    val triggers: List<String>,
    val condition: String?,
    val compensates: String? = null,
    val command: String,
    /** `@title`: the text drawn for this policy. */
    val title: String? = null,
    /** `@actor`: who acts on the event. */
    val actor: String? = null,
    /** `@timer` / `@time`: when the policy runs. */
    val timer: String? = null,
    val events: List<String>,
    val sourceRef: SourceRef
)
