package io.zenwave360.language.zdl

import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.utils.appendTo
import io.zenwave360.language.utils.appendToList
import io.zenwave360.language.utils.appendToWithMap
import io.zenwave360.language.utils.asJavaMap
import io.zenwave360.language.utils.buildMap
import io.zenwave360.language.utils.putEntry
import io.zenwave360.language.utils.with

class ZdlModel(private val delegate: MutableMap<String, Any?> = buildMap()) : MutableMap<String, Any?> by delegate {

    init {
        // Initialize top-level structure
        delegate.putEntry("imports", mutableListOf<Any?>())
        delegate.putEntry("config", buildMap())
        delegate.putEntry("apis", buildMap())
        delegate.putEntry("aggregates", buildMap())
        delegate.putEntry("entities", buildMap())
        delegate.putEntry("enums", buildMap())
        delegate.putEntry("relationships", buildMap())
        delegate.putEntry("services", buildMap())
        delegate.putEntry("inputs", buildMap())
        delegate.putEntry("outputs", buildMap())
        delegate.putEntry("events", buildMap())
        delegate.putEntry("locations", buildMap())
        delegate.putEntry("problems", mutableListOf<Any?>())
    }

    fun asJavaMap(): MutableMap<String, Any?> = delegate.asJavaMap()

    // Forward fluent API expected by call sites
    fun with(key: String, value: Any?): ZdlModel { delegate.with(key, value); return this }
    fun appendTo(collection: String, key: String, value: Any?): ZdlModel { delegate.appendTo(collection, key, value); return this }
    fun appendToWithMap(collection: String, map: Map<String, Any?>): ZdlModel { delegate.appendToWithMap(collection, map); return this }
    fun appendToList(collection: String, value: Any?): ZdlModel { delegate.appendToList(collection, value); return this }

    @Suppress("UNCHECKED_CAST")
    fun getAggregates(): MutableMap<String, Any?> = delegate["aggregates"] as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun getEntities(): MutableMap<String, Any?> = delegate["entities"] as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun getInputs(): MutableMap<String, Any?> = delegate["inputs"] as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun getOutputs(): MutableMap<String, Any?> = delegate["outputs"] as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun getEvents(): MutableMap<String, Any?> = delegate["events"] as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun getEnums(): MutableMap<String, Any?> = delegate["enums"] as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun getRelationships(): MutableMap<String, Any?> = delegate["relationships"] as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun getLocations(): MutableMap<String, Any?> = delegate["locations"] as MutableMap<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun getProblems(): MutableList<MutableMap<String, Any?>> =
        delegate["problems"] as MutableList<MutableMap<String, Any?>>

    fun setLocation(location: String, positions: IntArray?): ZdlModel {
        if (positions == null || positions.size != 6) return this
        return appendTo("locations", location, positions)
    }

    fun clearProblems() = getProblems().clear()

    fun addProblem(path: String, value: String?, error: String) {
        val p = problem(path, value, error)
        @Suppress("UNCHECKED_CAST")
        (delegate["problems"] as MutableList<Any?>).add(p)
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
        val location = entries.minByOrNull { (_, v) ->
            val pos = v as IntArray
            pos[1] - pos[0]
        }?.key
        return location
    }
}
