package io.zenwave360.language.zdl

import java.nio.file.Files
import java.nio.file.Path

/** Performs source-preserving edits to ZDL files. */
class ZdlEditor(
    private val parser: ZdlParser = ZdlParser(),
) {

    /**
     * Replaces an existing string-valued option in the top-level [config][ZdlModel] block.
     *
     * The source is parsed once to locate the option. The edited file is not reparsed or
     * validated; callers that need validation should invoke [ZdlParser] separately.
     */
    fun setConfigString(file: Path, name: String, value: String) {
        require(name.isNotBlank()) { "config option name must not be blank" }

        val source = Files.readString(file)
        val model = parser.parseModel(source)
        val config = model["config"] as? Map<*, *> ?: error("ZDL config block is missing")
        require(config[name] is String) { "ZDL config option '$name' is missing or is not a string" }

        val location = model.getLocations()["config.$name.value"] as? IntArray
            ?: error("ZDL config option '$name' has no source location")
        require(location.size >= 2) { "ZDL config option '$name' has an invalid source location" }

        val start = location[0]
        val end = location[1]
        require(start >= 0 && end >= start && end <= source.length) {
            "ZDL config option '$name' has an invalid source range"
        }

        Files.writeString(file, source.replaceRange(start, end, quote(value)))
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in '\u0000'..'\u001F' -> error("ZDL string values cannot contain control characters")
                else -> append(character)
            }
        }
        append('"')
    }
}
