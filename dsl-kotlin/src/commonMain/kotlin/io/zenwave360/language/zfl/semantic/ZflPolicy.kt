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
    val events: List<String>,
    val sourceRef: SourceRef,
    /** `@title`: the text drawn for this policy. */
    val title: String? = null,
    /** `@actor`: who acts on the event. */
    val actor: String? = null,
    /** `@timer` / `@time`: when the policy runs. */
    val timer: String? = null,
    /** Rule names from `@rules(...)` on the `when … do`. */
    val rules: List<String> = emptyList(),
    /** True for the policy a `timer` block draws; its [timer] text tells timers of one event and command apart. */
    val fromTimerBlock: Boolean = false,
)
