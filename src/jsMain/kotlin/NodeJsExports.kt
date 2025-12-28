@file:JsExport

import io.zenwave360.language.zdl.ZdlParser
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
        is List<*> -> {
            val result = js("[]")
            value.forEachIndexed { index, item ->
                result[index] = convertToPlain(item)
            }
            result
        }
        else -> value
    }
}
