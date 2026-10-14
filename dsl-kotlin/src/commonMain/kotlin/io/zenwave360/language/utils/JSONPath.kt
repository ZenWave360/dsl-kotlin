package io.zenwave360.language.utils

import kotlin.collections.get

object JSONPath {
    fun <T> get(source: Any?, path: String): T? = get(source, path, null)

    @Suppress("UNCHECKED_CAST")
    fun <T> get(source: Any?, path: String, defaultValue: T): T {
        if (source == null) return defaultValue

        // Normalize path: remove leading "$." if present, or just "$" if that's all
        val normalizedPath = when {
            path.startsWith("$..") -> path.substring(1)
            path.startsWith("$.") -> path.substring(2)
            path.startsWith("$") -> path.substring(1)
            else -> path
        }

        // Split path into segments
        val segments = parsePath(normalizedPath)

        try {
            val result = evaluatePath(source, segments)
//            if(segments.last() == "[*]" && result is Map<*, *>) {
//                return result.values.toList() as? T ?: defaultValue
//            }
            return result as? T ?: defaultValue
        } catch (e: Exception) {
            return defaultValue
        }
    }

    private fun parsePath(path: String): List<String> {
        if (path.isEmpty()) return emptyList()
        
        val segments = mutableListOf<String>()
        var current = ""
        var i = 0
        
        while (i < path.length) {
            when {
                // Handle recursive descent operator ".."
                i < path.length - 1 && path[i] == '.' && path[i + 1] == '.' -> {
                    if (current.isNotEmpty()) {
                        segments.add(current)
                        current = ""
                    }
                    segments.add("")
                    i += 2
                    continue
                }
                path[i] == '.' -> {
                    if (current.isNotEmpty()) {
                        segments.add(current)
                        current = ""
                    }
                }
                path[i] == '[' -> {
                    if (current.isNotEmpty()) {
                        segments.add(current)
                        current = ""
                    }
                    // Find the closing bracket
                    val closingBracket = path.indexOf(']', i)
                    if (closingBracket != -1) {
                        val bracketContent = path.substring(i + 1, closingBracket)
                        // Handle quoted strings in brackets
                        val segment = if (bracketContent.startsWith("'") && bracketContent.endsWith("'")) {
                            bracketContent.substring(1, bracketContent.length - 1)
                        } else {
                            "[$bracketContent]"
                        }
                        segments.add(segment)
                        i = closingBracket
                    } else {
                        current += path[i]
                    }
                }
                else -> current += path[i]
            }
            i++
        }
        
        if (current.isNotEmpty()) {
            segments.add(current)
        }
        
        return segments
    }

    private fun evaluatePath(current: Any?, segments: List<String>): Any? {
        if (current == null || segments.isEmpty()) return current

        val segment = segments.first()
        val remaining = segments.drop(1)

        val result = when {
            segment == "" -> {
                // Recursive descent - collect all matching paths
                val results = mutableListOf<Any?>()
                
                // If there are remaining segments, apply them recursively
                if (remaining.isNotEmpty()) {
                    collectRecursive(current, remaining, results)
                } else {
                    // If no remaining segments, return current
                    results.add(current)
                }
                
                results.flatMap { result ->
                    when (result) {
                        is List<*> -> result
                        else -> listOf(result)
                    }
                }
            }
            segment == "[*]" -> {
                when (current) {
                    is List<*> -> {
                        if (remaining.isEmpty()) current
                        else {
                            val results = current.mapNotNull { evaluatePath(it, remaining) }
                            results.flatMap { result ->
                                when (result) {
                                    is List<*> -> result
                                    else -> listOf(result)
                                }
                            }
                        }
                    }
                    is Map<*, *> -> {
                        if (remaining.isEmpty()) current.values.toList()
                        else {
                            val results = current.values.mapNotNull { evaluatePath(it, remaining) }
                            results.flatMap { result ->
                                when (result) {
                                    is List<*> -> result
                                    else -> listOf(result)
                                }
                            }
                        }
                    }
                    else -> null
                }
            }
            segment.startsWith("[") && segment.endsWith("]") -> {
                val index = segment.removeSurrounding("[", "]").toIntOrNull()
                when {
                    index != null && current is List<*> -> {
                        evaluatePath(current.getOrNull(index), remaining)
                    }
                    else -> null
                }
            }
            else -> {
                when (current) {
                    is Map<*, *> -> evaluatePath(current[segment], remaining)
                    else -> null
                }
            }
        }
        return result
    }

    private fun collectRecursive(current: Any?, segments: List<String>, results: MutableList<Any?>) {
        if (current == null) return
        
        // Try to evaluate the path from current position
        val directResult = evaluatePath(current, segments)
        if (directResult != null) {
            when (directResult) {
                is List<*> -> results.addAll(directResult)
                else -> results.add(directResult)
            }
        }
        
        // Recursively search in children
        when (current) {
            is Map<*, *> -> {
                current.values.forEach { value ->
                    collectRecursive(value, segments, results)
                }
            }
            is List<*> -> {
                current.forEach { item ->
                    collectRecursive(item, segments, results)
                }
            }
        }
    }
}
