package io.zenwave360.language.eventflow.view

data class ServicePath(
    val raw: String,
    val segments: List<String>
) {
    val system: String?
        get() = segments.firstOrNull()

    val service: String?
        get() = segments.getOrNull(1)

    val groupSegments: List<String>
        get() = when {
            segments.isEmpty() -> listOf("Unbounded")
            segments.size == 1 -> listOf(segments[0])
            else -> segments.take(2)
        }

    val groupLabel: String
        get() = groupSegments.joinToString("\n")

    val groupId: String
        get() = "group:${groupSegments.joinToString(">")}"
}

fun parseServicePath(raw: String?): ServicePath? {
    val normalized = raw
        ?.split('/', '.')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.joinToString("/")
        ?: return null

    if (normalized.isBlank()) {
        return null
    }

    return ServicePath(
        raw = normalized,
        segments = normalized.split('/')
    )
}
