@file:JsExport

import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zdl.PluginArguments
import io.zenwave360.language.zfl.ZflParser
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * API for parsing ZDL content
 *
 * Usage:
 * ```javascript
 * import { parseZdl } from '@zenwave360/dsl';
 * const model = parseZdl(zdlContent);
 * console.log(JSON.stringify(model, null, 2));
 * ```
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
fun parseZdl(input: String): Any? {
    val parser = ZdlParser()
    val model = parser.parseModel(input)
    return convertToPlain(model as Map<*, *>)
}

/**
 * API for parsing ZFL (ZenWave Flow Language) content
 *
 * Usage:
 * ```javascript
 * import { parseZfl } from '@zenwave360/dsl';
 * const model = parseZfl(zflContent);
 * console.log(JSON.stringify(model, null, 2));
 * ```
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
fun parseZfl(input: String): Any? {
    val parser = ZflParser()
    val model = parser.parseModel(input)
    return convertToPlain(model as Map<*, *>)
}

/**
 * Builds the unquoted SDK arguments for a plugin returned by [parseZdl].
 * The result is a plain JavaScript array; command selection, extra arguments and shell quoting remain the caller's job.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
fun buildPluginArguments(model: Any?, plugin: Any?, filePath: String): Array<String> {
    val kotlinModel = plainToKotlin(model) as? Map<String, Any?> ?: emptyMap()
    val kotlinPlugin = plainToKotlin(plugin) as? Map<String, Any?> ?: emptyMap()
    return PluginArguments.build(kotlinModel, kotlinPlugin, filePath).toTypedArray()
}

/** Largest integer a JS number represents exactly: 2^53 - 1. */
private const val MAX_SAFE_INTEGER = 9007199254740991L

private fun convertToPlain(value: Any?): Any? {
    return when (value) {
        null -> null
        is Map<*, *> -> {
            val result = js("{}")
            value.forEach { (k, v) ->
                result[k as String] = convertToPlain(v)
            }
            result
        }
        is Collection<*> -> {
            val result = js("[]")
            value.forEachIndexed { index, item ->
                result[index] = convertToPlain(item)
            }
            result
        }
        // Kotlin Long is not a JS number: export integers as numbers when exactly representable,
        // otherwise as their decimal string, so no Kotlin runtime object reaches JS consumers.
        is Long -> if (value in -MAX_SAFE_INTEGER..MAX_SAFE_INTEGER) value.toDouble() else value.toString()
        else -> value
    }
}


private fun plainToKotlin(value: dynamic): Any? = when {
    value == null -> null
    js("Array.isArray(value)") as Boolean -> {
        val result = mutableListOf<Any?>()
        val length = value.length as Int
        for (index in 0 until length) result += plainToKotlin(value[index])
        result
    }
    js("typeof value === 'object'") as Boolean -> {
        val result = linkedMapOf<String, Any?>()
        val keys = js("Object.keys(value)") as Array<String>
        for (key in keys) result[key] = plainToKotlin(value[key])
        result
    }
    else -> value as Any?
}
