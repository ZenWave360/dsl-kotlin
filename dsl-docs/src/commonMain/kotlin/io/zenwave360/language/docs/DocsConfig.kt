package io.zenwave360.language.docs

import io.zenwave360.language.docs.internal.Yaml
import io.zenwave360.language.docs.internal.asMap

/**
 * The content of a `dsl-docs.yml` (or `.json`) file. File paths are relative to the config file.
 *
 * ```yaml
 * title: Orders Checkout        # optional, defaults to the ZDL config title
 * version: 0.1.0                # optional, defaults to the ZDL config version
 * date: 2026-10-13              # optional, printed as written
 * zdl: [domain-model.zdl]
 * zfl: []
 * openapi: [openapi.yml]
 * asyncapi: [asyncapi.yml, asyncapi-client.yml]
 * pages:                        # optional pages written by hand, linked from the index
 *   - { title: Architecture decisions, path: docs/adr/README.md }
 * ```
 */
data class DocsConfig(
    val title: String? = null,
    val version: String? = null,
    val date: String? = null,
    val sources: List<Pair<SourceKind, String>> = emptyList(),
    val pages: List<DocsLink> = emptyList(),
) {
    companion object {
        private val SOURCE_KEYS = mapOf(
            "zdl" to SourceKind.ZDL,
            "zfl" to SourceKind.ZFL,
            "openapi" to SourceKind.OPENAPI,
            "asyncapi" to SourceKind.ASYNCAPI,
        )
        private val KEYS = setOf("title", "version", "date", "pages") + SOURCE_KEYS.keys

        /** Reads a config file. Throws [IllegalArgumentException] with a message for the user when it is not valid. */
        fun parse(text: String): DocsConfig {
            val map = try {
                Yaml.read(text)
            } catch (e: Exception) {
                throw IllegalArgumentException("Config is not valid YAML or JSON: ${e.message?.lineSequence()?.firstOrNull()}")
            }
            val unknown = map.keys - KEYS
            require(unknown.isEmpty()) { "Unknown config keys: ${unknown.joinToString(", ")}. Known keys: ${KEYS.joinToString(", ")}" }

            val sources = SOURCE_KEYS.flatMap { (key, kind) -> paths(map, key).map { kind to it } }
            val pages = list(map, "pages").mapIndexed { i, page ->
                val p = page.asMap()
                val path = p["path"] as? String
                require(!path.isNullOrBlank()) { "pages[$i] needs a path" }
                DocsLink(title = (p["title"] as? String)?.takeIf { it.isNotBlank() } ?: path, href = path)
            }
            return DocsConfig(
                title = scalar(map, "title"),
                version = scalar(map, "version"),
                date = scalar(map, "date"),
                sources = sources,
                pages = pages,
            )
        }

        private fun scalar(map: Map<String, Any?>, key: String): String? {
            val value = map[key] ?: return null
            require(value is String) { "$key must be a single value" }
            return value.takeIf { it.isNotBlank() }
        }

        private fun list(map: Map<String, Any?>, key: String): List<Any?> = when (val value = map[key]) {
            null -> emptyList()
            is List<*> -> value
            else -> throw IllegalArgumentException("$key must be a list")
        }

        private fun paths(map: Map<String, Any?>, key: String): List<String> = list(map, key).mapIndexed { i, path ->
            require(path is String && path.isNotBlank()) { "$key[$i] must be a file path" }
            path
        }
    }
}
