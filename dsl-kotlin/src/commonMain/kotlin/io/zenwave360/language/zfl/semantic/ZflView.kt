package io.zenwave360.language.zfl.semantic

import io.zenwave360.language.source.SourceRef
import kotlinx.serialization.Serializable

/**
 * The aggregates of one flow instance (`view Name by Root.key { ... }`): one instance per value of the key.
 * Events need no correlation declaration: they correlate by the key or by a member's key.
 */
@Serializable
data class ZflView(
    val name: String,
    /** The aggregate of `by Root.key`: one flow instance per Root. */
    val keyAggregate: String,
    /** Field path of the key inside [keyAggregate], `[id]` for `by Order.id`. */
    val keyPath: List<String>,
    val members: List<ZflViewMember> = emptyList(),
    val description: String? = null,
    val options: Map<String, String?> = emptyMap(),
    val sourceRef: SourceRef,
) {
    val keyField: String get() = keyPath.lastOrNull().orEmpty()

    fun member(aggregate: String): ZflViewMember? = members.firstOrNull { it.aggregate == aggregate }
}

/** One aggregate of a view. The root has no [path]; any other member points to the key with [path]. */
@Serializable
data class ZflViewMember(
    val aggregate: String,
    /** Field path to the key, `[order, id]` for `Fulfillment.order.id`; empty for the root. */
    val path: List<String> = emptyList(),
    /** `Notification[]`: several instances per key. */
    val many: Boolean = false,
    val sourceRef: SourceRef,
)
