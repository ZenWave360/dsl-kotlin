@file:JsExport

import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zdl.application.GenerateMermaidFromZdl
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
 * Generates a Mermaid `classDiagram` for ZDL content: aggregates, entities with their fields,
 * enums, inputs, outputs, events, services with their commands, and relationships.
 * The result is inert Mermaid text (no click, link or callback directives).
 *
 * Usage:
 * ```javascript
 * import { generateMermaidFromZdl } from '@zenwave360/dsl';
 * const mermaid = generateMermaidFromZdl(zdlContent); // starts with "classDiagram"
 * ```
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
fun generateMermaidFromZdl(input: String): String = GenerateMermaidFromZdl().execute(input)

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
