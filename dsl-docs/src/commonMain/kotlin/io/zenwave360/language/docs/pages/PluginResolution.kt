package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.internal.asList
import io.zenwave360.language.docs.internal.asMap
import io.zenwave360.language.zdl.ZdlModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A command-line option of a `.zw` file as the parser reports it: [value] is in source form, quotes included, or null. */
internal data class CliOption(val name: String, val value: String?)

/** A plugin declaration. [config] holds the values the parser reports: strings without quotes, integers as `Long`, lists, maps. */
internal data class PluginDeclaration(
    val name: String,
    val disabled: Boolean,
    val inherit: Boolean,
    val config: Map<String, Any?>,
    val cliOptions: List<CliOption>,
)

/** What a `.zw` file declares: the `config` options, its command-line options and its plugins in document order. */
internal data class PluginModel(
    val config: Map<String, Any?>,
    val cliOptions: List<CliOption>,
    val plugins: List<PluginDeclaration>,
) {
    companion object {
        /**
         * Reads the plugin declarations of [model], parsed from [text], as the VS Code extension does (`sdk/model.ts`): a plugin
         * the parser recovered from an unfinished declaration, whose source does not end with its closing brace, is left out.
         */
        fun of(model: ZdlModel, text: String): PluginModel {
            val locations = model.getLocations()
            val plugins = model["pluginsList"].asList().mapIndexedNotNull { index, raw ->
                @Suppress("UNCHECKED_CAST")
                val fields = raw as? Map<String, Any?> ?: return@mapIndexedNotNull null
                val span = locations["pluginsList.$index"] as? IntArray
                if (span == null || span.size < 2 || span[0] < 0 || span[1] < span[0] || span[1] > text.length) return@mapIndexedNotNull null
                if (!text.substring(span[0], span[1]).trimEnd().endsWith("}")) return@mapIndexedNotNull null
                PluginDeclaration(
                    name = fields["name"] as? String ?: "plugin$index",
                    disabled = fields["disabled"] == true,
                    inherit = readsAsInheritance(fields["options"].asMap()["inherit"]),
                    config = fields["config"].asMap(),
                    cliOptions = cliOptions(fields["cliOptions"]),
                )
            }
            return PluginModel(model["config"].asMap(), cliOptions(model["cliOptions"]), plugins)
        }

        private fun readsAsInheritance(value: Any?): Boolean =
            if (value is String) value.replace(Regex("\\s"), "").lowercase() != "inheritfalse" else value != false

        private fun cliOptions(value: Any?): List<CliOption> =
            value.asMap().map { (name, option) -> CliOption(name, option?.toString()) }
    }
}

internal enum class Origin { GLOBAL, PLUGIN }

/** One resolved option. [overrides] is true when the plugin's own value replaced an inherited one of the same name. */
internal data class ResolvedProperty(val name: String, val value: String, val origin: Origin, val overrides: Boolean)

/** One resolved command-line option; [value] is null for a flag. */
internal data class ResolvedCliOption(val name: String, val value: String?, val origin: Origin, val overrides: Boolean)

internal data class ResolvedPlugin(val properties: List<ResolvedProperty>, val cliOptions: List<ResolvedCliOption>)

/**
 * What a plugin declaration comes to once the enclosing `config` is applied, by the rules the VS Code extension uses to build
 * a command (`sdk/command.ts`, `buildSdkArguments`, and sdk-runner SR-R2): the config options are inherited unless the plugin
 * says `(inherit false)`, an option of the plugin replaces an inherited one of the same name keeping the inherited one's
 * place, and a plugin command-line option replaces an inherited one and moves to the end.
 *
 * Not resolved here, because they need a file or belong to running: `zdlFile`, `${this.file.path}`, `--deps` from Maven
 * imports, the configured command and extra arguments, and everything about the shell.
 */
internal object PluginResolution {

    fun resolve(model: PluginModel, plugin: PluginDeclaration): ResolvedPlugin {
        val properties = linkedMapOf<String, ResolvedProperty>()
        if (plugin.inherit) {
            for ((name, value) in model.config) properties[name] = ResolvedProperty(name, formatValue(value), Origin.GLOBAL, false)
        }
        for ((name, value) in plugin.config) {
            properties[name] = ResolvedProperty(name, formatValue(value), Origin.PLUGIN, properties.containsKey(name))
        }

        val cliOptions = linkedMapOf<String, ResolvedCliOption>()
        if (plugin.inherit) {
            for (option in model.cliOptions) cliOptions[option.name] = ResolvedCliOption(option.name, cliValue(option), Origin.GLOBAL, false)
        }
        for (option in plugin.cliOptions) {
            // Moving an override to the end keeps the plugin's own declaration order.
            val overrides = cliOptions.remove(option.name) != null
            cliOptions[option.name] = ResolvedCliOption(option.name, cliValue(option), Origin.PLUGIN, overrides)
        }
        return ResolvedPlugin(properties.values.toList(), cliOptions.values.toList())
    }

    /** The text of a value as the SDK receives it: lists joined by commas, objects as JSON, everything else as written. */
    fun formatValue(value: Any?): String = when (value) {
        is List<*> -> value.joinToString(",") { formatValue(it) }
        is Map<*, *> -> Json.encodeToString(JsonElement.serializer(), toJson(value))
        else -> value.toString()
    }

    /** The largest integer a JavaScript number holds exactly, 2^53 - 1: the boundary of `NodeJsExports.kt`, which `model.ts` reads. */
    private const val MAX_SAFE_INTEGER = 9007199254740991L

    private fun toJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
        is List<*> -> JsonArray(value.map { toJson(it) })
        is Boolean -> JsonPrimitive(value)
        // The client turns an integer outside JavaScript's safe range into its exact decimal string (sdk/model.ts).
        is Long -> if (value in -MAX_SAFE_INTEGER..MAX_SAFE_INTEGER) JsonPrimitive(value) else JsonPrimitive(value.toString())
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    /** The value of a CLI option: parsers give it in source form, and one shell word is that word without its quotes. */
    fun cliValue(option: CliOption): String? {
        val value = option.value ?: return null
        val words = splitCommandLine(value)
        return if (words.size == 1) words[0] else value
    }

    /** Splits a source-form CLI value into words, POSIX style: quotes group, a backslash escapes in the cases a shell does. */
    fun splitCommandLine(commandLine: String): List<String> {
        val words = mutableListOf<String>()
        val word = StringBuilder()
        var quote: Char? = null
        var escaped = false
        var started = false
        val trimmed = commandLine.trim()
        for (index in trimmed.indices) {
            val character = trimmed[index]
            val next = trimmed.getOrNull(index + 1)
            when {
                escaped -> {
                    word.append(character)
                    escaped = false
                    started = true
                }
                character == '\\' && quote != '\'' -> {
                    val escapable = if (quote == '"') next != null && next in "\"\\$`" else next != null && (next.isWhitespace() || next in "\"'\\")
                    if (escapable) escaped = true else word.append(character)
                    started = true
                }
                quote != null -> {
                    if (character == quote) quote = null else word.append(character)
                    started = true
                }
                character == '\'' || character == '"' -> {
                    quote = character
                    started = true
                }
                character.isWhitespace() -> if (started) {
                    words += word.toString()
                    word.clear()
                    started = false
                }
                else -> {
                    word.append(character)
                    started = true
                }
            }
        }
        if (escaped) word.append('\\')
        if (started) words += word.toString()
        return words
    }
}
