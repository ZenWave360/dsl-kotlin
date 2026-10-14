package io.zenwave360.language.utils

/**
 * Utility functions for building and manipulating MutableMap instances with a fluent API.
 * These replace the previous FluentMap class to avoid ClassCastException issues with Java interop.
 */

/**
 * Build a new MutableMap with optional initialization block.
 */
fun buildMap(block: MutableMap<String, Any?>.() -> Unit = {}): MutableMap<String, Any?> =
    linkedMapOf<String, Any?>().apply(block)

/**
 * Put a key-value pair and return the map for chaining.
 */
fun MutableMap<String, Any?>.with(key: String, value: Any?): MutableMap<String, Any?> =
    apply { this[key] = value }

/**
 * Put a key-value pair and return the map for chaining (alias for with).
 */
fun MutableMap<String, Any?>.putEntry(key: String, value: Any?): MutableMap<String, Any?> =
    apply { this[key] = value }

/**
 * Put all entries from another map and return the map for chaining.
 */
fun MutableMap<String, Any?>.putAllEntries(map: Map<String, Any?>): MutableMap<String, Any?> =
    apply { this.putAll(map) }

/**
 * Append a key-value pair to a nested map within this map.
 * If the collection doesn't exist, it will be created as a LinkedHashMap.
 */
fun MutableMap<String, Any?>.appendTo(collection: String, key: String, value: Any?): MutableMap<String, Any?> = apply {
    val nestedMap = this.getOrPut(collection) { linkedMapOf<String, Any?>() } as MutableMap<String, Any?>
    nestedMap[key] = value
}

/**
 * Append all entries from a map to a nested map within this map.
 * If the collection doesn't exist, it will be created as a LinkedHashMap.
 */
fun MutableMap<String, Any?>.appendToWithMap(collection: String, map: Map<String, Any?>): MutableMap<String, Any?> = apply {
    val nestedMap = this.getOrPut(collection) { linkedMapOf<String, Any?>() } as MutableMap<String, Any?>
    nestedMap.putAll(map)
}

/**
 * Append a value to a nested list within this map.
 * If the collection doesn't exist, it will be created as a MutableList.
 */
fun MutableMap<String, Any?>.appendToList(collection: String, value: Any?): MutableMap<String, Any?> = apply {
    val nestedList = this.getOrPut(collection) { mutableListOf<Any?>() } as MutableList<Any?>
    nestedList.add(value)
}

/**
 * Returns this map (useful for Java interop compatibility).
 * Previously used to unwrap FluentMap to underlying map.
 */
fun MutableMap<String, Any?>.asJavaMap(): MutableMap<String, Any?> = this

