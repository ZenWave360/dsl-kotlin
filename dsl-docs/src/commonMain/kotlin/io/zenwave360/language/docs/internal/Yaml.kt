package io.zenwave360.language.docs.internal

import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import it.krzeminski.snakeyaml.engine.kmp.api.lowlevel.Compose
import it.krzeminski.snakeyaml.engine.kmp.nodes.MappingNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.Node
import it.krzeminski.snakeyaml.engine.kmp.nodes.ScalarNode
import it.krzeminski.snakeyaml.engine.kmp.nodes.SequenceNode

/**
 * Reads YAML or JSON into maps, lists and strings. Every scalar stays a string, as written, so
 * `version: 1.10` is "1.10" and response codes are "201", not numbers. Maps keep document order.
 * `$ref` values are kept as they are: nothing is loaded from a file or the network.
 */
internal object Yaml {

    fun read(text: String): Map<String, Any?> {
        @Suppress("DEPRECATION")
        val node = Compose(LoadSettings()).composeString(text) ?: return emptyMap()
        @Suppress("UNCHECKED_CAST")
        return value(node) as? Map<String, Any?> ?: emptyMap()
    }

    /** Like [read] but the root may be a list or a string (an Avro union, for example). Null when the text is empty. */
    fun readAny(text: String): Any? {
        @Suppress("DEPRECATION")
        val node = Compose(LoadSettings()).composeString(text) ?: return null
        return value(node)
    }

    private fun value(node: Node): Any? = when (node) {
        is MappingNode -> linkedMapOf<String, Any?>().also { map ->
            node.value.forEach { tuple ->
                val key = (tuple.keyNode as? ScalarNode)?.value ?: return@forEach
                map[key] = value(tuple.valueNode)
            }
        }
        is SequenceNode -> node.value.map { value(it) }
        is ScalarNode -> node.value
        else -> null
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

internal fun Any?.asList(): List<Any?> = this as? List<Any?> ?: emptyList()

internal fun Map<String, Any?>.string(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }
