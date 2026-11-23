package io.zenwave360.language.eventflow.view

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How the flow is drawn: no bands, one band per system, or one band per service (spec:flows/eventflow#ef-r24). */
enum class FlowLanes(val wire: String) {
    NONE("none"),
    SYSTEMS("systems"),
    SERVICES("services");

    companion object {
        /** The lanes value named [value] on the wire; null when it names none. */
        fun fromWire(value: String?): FlowLanes? = entries.firstOrNull { it.wire == value }
    }
}

@Serializable
enum class FlowLaneKind {
    @SerialName("system") SYSTEM,
    @SerialName("service") SERVICE
}

/**
 * A full-width horizontal band of the flow drawn in lanes. Presentation only: the nodes it holds are
 * listed by [FlowNode.lane]. The last band, with the nodes no system or service owns, has no [system].
 */
@Serializable
data class FlowLane(
    val id: String,
    val kind: FlowLaneKind,
    val label: String,
    /** The system of a service band; null for system bands and for the band of nodes with no service. */
    val system: String? = null,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    /** 0-based position from the top: order of first appearance in the flow, the band with no system or service last. */
    val order: Int
)
