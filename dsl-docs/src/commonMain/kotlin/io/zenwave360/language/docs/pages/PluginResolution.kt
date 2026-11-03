package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.internal.asList
import io.zenwave360.language.docs.internal.asMap
import io.zenwave360.language.zdl.ZdlModel
import io.zenwave360.language.zdl.PluginArgumentOrigin
import io.zenwave360.language.zdl.PluginArguments

/** A command-line option of a `.zw` file as the parser reports it: [value] is in source form, quotes included, or null. */
internal data class CliOption(val name: String, val value: String?)

/** A plugin declaration. [config] holds the values the parser reports: strings without quotes, integers as `Long`, lists, maps. */
internal data class PluginDeclaration(
    val raw: Map<String, Any?>,
    val name: String,
    val disabled: Boolean,
    val inherit: Boolean,
    val config: Map<String, Any?>,
    val cliOptions: List<CliOption>,
)

/** What a `.zw` file declares: the `config` options, its command-line options and its plugins in document order. */
internal data class PluginModel(
    val raw: ZdlModel,
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
                    raw = fields,
                    name = fields["name"] as? String ?: "plugin$index",
                    disabled = fields["disabled"] == true,
                    inherit = readsAsInheritance(fields["options"].asMap()["inherit"]),
                    config = fields["config"].asMap(),
                    cliOptions = cliOptions(fields["cliOptions"]),
                )
            }
            return PluginModel(model, model["config"].asMap(), cliOptions(model["cliOptions"]), plugins)
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
        val resolved = PluginArguments.resolve(model.raw, plugin.raw, "preview.zw")
        return ResolvedPlugin(
            resolved.properties.map { ResolvedProperty(it.name, it.value, if (it.origin == PluginArgumentOrigin.CONFIG) Origin.GLOBAL else Origin.PLUGIN, it.overrides) },
            resolved.cliOptions.map { ResolvedCliOption(it.name, it.value, if (it.origin == PluginArgumentOrigin.CONFIG) Origin.GLOBAL else Origin.PLUGIN, it.overrides) },
        )
    }

    /** The text of a value as the SDK receives it: lists joined by commas, objects as JSON, everything else as written. */
    fun formatValue(value: Any?): String = PluginArguments.formatValue(value)

    /** The value of a CLI option: parsers give it in source form, and one shell word is that word without its quotes. */
    fun cliValue(option: CliOption): String? = PluginArguments.cliValue(option.value)

    /** Splits a source-form CLI value into words, POSIX style: quotes group, a backslash escapes in the cases a shell does. */
    fun splitCommandLine(commandLine: String): List<String> = PluginArguments.splitCommandLine(commandLine)
}
