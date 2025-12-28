package io.zenwave360.language.zfl

import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.utils.appendTo
import io.zenwave360.language.utils.appendToList
import io.zenwave360.language.utils.appendToWithMap
import io.zenwave360.language.utils.asJavaMap
import io.zenwave360.language.utils.buildMap
import io.zenwave360.language.utils.putEntry
import io.zenwave360.language.utils.with

class ZflModel(private val delegate: MutableMap<String, Any?> = buildMap()) : MutableMap<String, Any?> by delegate {

    init {
        // Initialize top-level structure
        delegate.putEntry("imports", mutableListOf<Any?>())
        delegate.putEntry("config", buildMap())
        delegate.putEntry("flows", buildMap())
        delegate.putEntry("locations", buildMap())
        delegate.putEntry("problems", mutableListOf<Any?>())
    }

    fun asJavaMap(): MutableMap<String, Any?> = delegate.asJavaMap()

    // Forward fluent API expected by call sites
    fun with(key: String, value: Any?): ZflModel { delegate.with(key, value); return this }
    fun appendTo(collection: String, key: String, value: Any?): ZflModel { delegate.appendTo(collection, key, value); return this }
    fun appendToWithMap(collection: String, map: Map<String, Any?>): ZflModel { delegate.appendToWithMap(collection, map); return this }
    fun appendToList(collection: String, value: Any?): ZflModel { delegate.appendToList(collection, value); return this }

    @Suppress("UNCHECKED_CAST")
    fun getFlows(): MutableMap<String, Any?> = delegate["flows"] as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun getLocations(): MutableMap<String, Any?> = delegate["locations"] as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun getProblems(): MutableList<MutableMap<String, Any?>> =
        delegate["problems"] as MutableList<MutableMap<String, Any?>>

    fun setLocation(location: String, positions: IntArray?): ZflModel {
        if (positions == null || positions.size != 6) return this
        return appendTo("locations", location, positions)
    }

    fun clearProblems() = getProblems().clear()

    fun addProblem(path: String, value: String?, error: String) {
        try {
            val p = problem(path, value, error)
            @Suppress("UNCHECKED_CAST")
            (delegate["problems"] as MutableList<Any?>).add(p)
        } catch (e: Exception) {
            println("Error adding problem '$path': ${e.message}")
        }
    }

    private fun problem(path: String, value: String?, error: String): Map<String, Any?> {
        val location = getLocation(path)
        val message = error.replace("%s", value ?: "")
        return mapOf(
            "path" to path,
            "location" to location,
            "value" to value,
            "message" to message
        )
    }

    private fun getLocation(path: String): IntArray? {
        @Suppress("UNCHECKED_CAST")
        return JSONPath.get(this, "$.locations.['$path']") as? IntArray
    }

    fun getLocation(line: Int, character: Int): String? {
        val entries = getLocations().entries.filter { (_, value) ->
            val position = value as IntArray
            val lineStart = position[2]
            val characterStart = position[3]
            val lineEnd = position[4]
            val characterEnd = position[5]
            lineStart <= line && line <= lineEnd && (line != lineStart || characterStart <= character) && (line != lineEnd || character <= characterEnd)
        }
        // Return the one with the closer location which is the one with the smaller range
        val location = entries.minByOrNull { (_, v) ->
            val pos = v as IntArray
            pos[1] - pos[0]
        }?.key
        return location
    }
}
