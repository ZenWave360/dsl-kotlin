package io.zenwave360.language.zdl

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Where a resolved plugin option was declared. */
enum class PluginArgumentOrigin { CONFIG, PLUGIN }

/** A resolved plugin property, in SDK order. */
data class ResolvedPluginProperty(
    val name: String,
    val value: String,
    val origin: PluginArgumentOrigin,
    val overrides: Boolean,
)

/** A resolved plugin command-line option, in SDK order. A null [value] is a flag. */
data class ResolvedPluginCliOption(
    val name: String,
    val value: String?,
    val origin: PluginArgumentOrigin,
    val overrides: Boolean,
)

/** The effective configuration and unquoted SDK arguments for one plugin declaration. */
data class PluginArgumentResolution(
    val properties: List<ResolvedPluginProperty>,
    val cliOptions: List<ResolvedPluginCliOption>,
    val arguments: List<String>,
)

/**
 * Resolves the arguments owned by a `.zw` plugin declaration.
 *
 * The [model] and [plugin] parameters are the maps produced by [ZdlParser.parseModel].
 * This deliberately excludes a configured SDK executable and user-supplied extra arguments:
 * callers own command selection and shell quoting.
 */
object PluginArguments {
    private val mavenCoordinate = Regex("^[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+:[a-zA-Z0-9._-]+$")
    private const val filePlaceholder = "\${this.file.path}"
    private const val maxSafeInteger = 9007199254740991L

    fun resolve(model: Map<String, Any?>, plugin: Map<String, Any?>, filePath: String): PluginArgumentResolution {
        val inherit = readsAsInheritance(plugin["options"].asMap()["inherit"])
        val properties = linkedMapOf<String, ResolvedPluginProperty>()
        if (inherit) {
            for ((name, value) in model["config"].asMap()) {
                properties[name] = ResolvedPluginProperty(name, formatValue(value), PluginArgumentOrigin.CONFIG, false)
            }
        }
        for ((name, value) in plugin["config"].asMap()) {
            properties[name] = ResolvedPluginProperty(name, formatValue(value), PluginArgumentOrigin.PLUGIN, properties.containsKey(name))
        }

        val cliOptions = linkedMapOf<String, ResolvedPluginCliOption>()
        // Source form, kept until the argument is built. Display values are dequoted without substituting the path.
        val cliSources = linkedMapOf<String, Any?>()
        if (inherit) {
            for ((name, value) in model["cliOptions"].asMap()) {
                cliSources[name] = value
                cliOptions[name] = ResolvedPluginCliOption(name, cliValue(value), PluginArgumentOrigin.CONFIG, false)
            }
        }
        for ((name, value) in plugin["cliOptions"].asMap()) {
            val overrides = cliOptions.remove(name) != null
            cliSources.remove(name)
            cliSources[name] = value
            cliOptions[name] = ResolvedPluginCliOption(name, cliValue(value), PluginArgumentOrigin.PLUGIN, overrides)
        }

        val options = LinkedHashMap(properties)
        if (!options.containsKey("zdlFile") && !options.containsKey("zdlFiles")) {
            options["zdlFile"] = ResolvedPluginProperty("zdlFile", normalizedPath(filePath), PluginArgumentOrigin.PLUGIN, false)
        }
        val arguments = mutableListOf("-p", plugin["name"] as? String ?: "")
        val dependencies = model["imports"].asList().mapNotNull { (it as? Map<*, *>)?.get("value") as? String }.filter { mavenCoordinate.matches(it) }
        if (dependencies.isNotEmpty()) {
            arguments += "--deps"
            arguments += dependencies.joinToString(",")
        }
        for ((name, property) in options) arguments += "$name=${substitutePath(property.value, filePath)}"
        for (option in cliOptions.values) arguments += cliOptionArgument(option.name, cliSources[option.name], filePath)
        return PluginArgumentResolution(properties.values.toList(), cliOptions.values.toList(), arguments.filter { it.isNotEmpty() })
    }

    /** Builds only the SDK arguments, in the order the SDK receives them. */
    fun build(model: Map<String, Any?>, plugin: Map<String, Any?>, filePath: String): List<String> = resolve(model, plugin, filePath).arguments

    fun formatValue(value: Any?): String = when (value) {
        is List<*> -> value.joinToString(",") { formatValue(it) }
        is Map<*, *> -> Json.encodeToString(JsonElement.serializer(), toJson(value))
        else -> value.toString()
    }

    fun cliValue(value: Any?): String? {
        value ?: return null
        val source = value.toString()
        val words = splitCommandLine(source)
        return if (words.size == 1) words[0] else source
    }

    /** SDK argument: substitute `${this.file.path}` in the source form, then dequote. [cliValue] stays the display value. */
    private fun cliOptionArgument(name: String, raw: Any?, filePath: String): String {
        raw ?: return "--$name"
        val substituted = substitutePath(raw.toString(), filePath)
        val words = splitCommandLine(substituted)
        return "--$name=${if (words.size == 1) words[0] else substituted}"
    }

    /** Splits a source-form CLI value using the same POSIX rules as the SDK client. */
    fun splitCommandLine(commandLine: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var started = false
        for (index in commandLine.trim().indices) {
            val character = commandLine.trim()[index]
            val next = commandLine.trim().getOrNull(index + 1)
            when {
                escaped -> { word.append(character); escaped = false; started = true }
                character == '\\' && quote != '\'' -> {
                    val escapable = if (quote == '"') next != null && next in "\"\\$`" else next != null && (next.isWhitespace() || next in "\"'\\")
                    if (escapable) escaped = true else word.append(character)
                    started = true
                }
                quote != null -> { if (character == quote) quote = null else word.append(character); started = true }
                character == '\'' || character == '"' -> { quote = character; started = true }
                character.isWhitespace() -> if (started) { words += word.toString(); word.clear(); started = false }
                else -> { word.append(character); started = true }
            }
        }
        if (escaped) word.append('\\')
        if (started) words += word.toString()
        return words
    }

    private fun readsAsInheritance(value: Any?): Boolean = if (value is String) value.replace(Regex("\\s"), "").lowercase() != "inheritfalse" else value != false
    private fun normalizedPath(filePath: String) = filePath.replace('\\', '/')
    private fun substitutePath(value: String, filePath: String) = value.replace(filePlaceholder, normalizedPath(filePath))
    private fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()
    private fun Any?.asList(): List<Any?> = this as? List<Any?> ?: emptyList()
    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is List<*> -> JsonArray(value.map(::toJson))
        is Boolean -> JsonPrimitive(value)
        is Long -> if (value in -maxSafeInteger..maxSafeInteger) JsonPrimitive(value) else JsonPrimitive(value.toString())
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}
