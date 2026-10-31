package io.zenwave360.language.docs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One option of a plugin as the catalogue describes it. [defaultValue] is its text, null when the catalogue gives none. */
data class CatalogueOption(
    val name: String,
    val description: String? = null,
    val type: String? = null,
    val defaultValue: String? = null,
    val values: List<String> = emptyList(),
    val documentation: String? = null,
)

/** One plugin of the catalogue. [name] is how a model declares it. [options] are keyed by option name, in catalogue order. */
data class CataloguePlugin(
    val name: String,
    val configClassName: String,
    val title: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val documentation: String? = null,
    val options: Map<String, CatalogueOption> = emptyMap(),
)

/**
 * What the ZenWave SDK says about its plugins: what each is for and what it accepts, to describe the plugin declarations of a
 * `.zw` file. [parse] reads the JSON the VS Code extension ships (`sdk-plugin-catalogue.json`), with the same rules as the
 * extension's reader.
 */
class PluginCatalogue(
    val source: String,
    val version: String,
    val plugins: List<CataloguePlugin>,
) {
    /** The plugin a model declares as [name]: the first with exactly that name, as the extension matches it. Null when it is not in the catalogue. */
    fun plugin(name: String): CataloguePlugin? = plugins.firstOrNull { it.name == name }

    companion object {
        /** A catalogue with no plugins. */
        val EMPTY = PluginCatalogue("no catalogue", "unknown", emptyList())

        /**
         * Reads the catalogue JSON. Throws [IllegalArgumentException] with a message that says what is wrong when [json] is not
         * JSON, is not an object, or has no `plugins` array. Like the extension, it skips a plugin entry that is not an object
         * or has no `configClassName`, ignores fields it does not know, and takes the plugin name from `name`,
         * `configClassSimpleName`, or the last segment of `configClassName`.
         */
        fun parse(json: String): PluginCatalogue {
            val root = try {
                Json.parseToJsonElement(json)
            } catch (e: Exception) {
                throw IllegalArgumentException("The plugin catalogue is not valid JSON: ${e.message}", e)
            }
            val obj = root as? JsonObject ?: throw IllegalArgumentException("The plugin catalogue must be a JSON object")
            val rawPlugins = obj["plugins"] as? JsonArray ?: throw IllegalArgumentException("The plugin catalogue has no \"plugins\" array")
            val version = obj.text("version") ?: "unknown"
            return PluginCatalogue(obj.text("source") ?: "ZenWave SDK $version", version, rawPlugins.mapNotNull { plugin(it) })
        }

        private fun plugin(raw: JsonElement): CataloguePlugin? {
            val obj = raw as? JsonObject ?: return null
            val className = (obj["configClassName"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val details = obj["plugin"] as? JsonObject ?: JsonObject(emptyMap())
            val options = linkedMapOf<String, CatalogueOption>()
            (obj["options"] as? JsonObject)?.forEach { (key, value) -> option(key, value)?.let { options[key] = it } }
            return CataloguePlugin(
                name = obj.text("name") ?: obj.text("configClassSimpleName") ?: className.substringAfterLast('.'),
                configClassName = className,
                title = obj.text("title") ?: details.text("title"),
                summary = obj.text("summary") ?: details.text("summary"),
                description = obj.text("description") ?: details.text("description"),
                documentation = documentation(obj) ?: documentation(details),
                options = options,
            )
        }

        private fun option(key: String, raw: JsonElement): CatalogueOption? {
            val obj = raw as? JsonObject ?: return null
            return CatalogueOption(
                name = obj.text("name") ?: key,
                description = obj.text("description"),
                type = obj.text("type"),
                defaultValue = obj["defaultValue"]?.takeIf { it !is JsonNull }?.let { display(it) },
                values = (obj["values"] as? JsonArray)?.map { display(it) } ?: emptyList(),
                documentation = documentation(obj),
            )
        }

        private fun documentation(obj: JsonObject) = obj.text("documentation") ?: obj.text("documentationUrl") ?: obj.text("docLink")

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

        private fun display(element: JsonElement): String = if (element is JsonPrimitive) element.content else element.toString()
    }
}
