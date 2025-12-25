package io.zenwave360.zdl.antlr

class ZdlModel(private val delegate: FluentMap = FluentMap.build()) : MutableMap<String, Any?> by delegate {

    init {
        // Initialize top-level structure
        delegate.putEntry("imports", mutableListOf<Any?>())
        delegate.putEntry("config", FluentMap.build())
        delegate.putEntry("apis", FluentMap.build())
        delegate.putEntry("aggregates", FluentMap.build())
        delegate.putEntry("entities", FluentMap.build())
        delegate.putEntry("enums", FluentMap.build())
        delegate.putEntry("relationships", FluentMap.build())
        delegate.putEntry("services", FluentMap.build())
        delegate.putEntry("inputs", FluentMap.build())
        delegate.putEntry("outputs", FluentMap.build())
        delegate.putEntry("events", FluentMap.build())
        delegate.putEntry("locations", FluentMap.build())
        delegate.putEntry("problems", mutableListOf<Any?>())
    }

    // Forward FluentMap fluent API expected by call sites
    fun with(key: String, value: Any?): ZdlModel { delegate.with(key, value); return this }
    fun appendTo(collection: String, key: String, value: Any?): ZdlModel { delegate.appendTo(collection, key, value); return this }
    fun appendToWithMap(collection: String, map: Map<String, Any?>): ZdlModel { delegate.appendToWithMap(collection, map); return this }
    fun appendToList(collection: String, value: Any?): ZdlModel { delegate.appendToList(collection, value); return this }

    fun getAggregates(): FluentMap = delegate["aggregates"] as FluentMap
    fun getEntities(): FluentMap = delegate["entities"] as FluentMap
    fun getInputs(): FluentMap = delegate["inputs"] as FluentMap
    fun getOutputs(): FluentMap = delegate["outputs"] as FluentMap
    fun getEvents(): FluentMap = delegate["events"] as FluentMap
    fun getEnums(): FluentMap = delegate["enums"] as FluentMap
    fun getRelationships(): FluentMap = delegate["relationships"] as FluentMap
    fun getLocations(): FluentMap = delegate["locations"] as FluentMap

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

