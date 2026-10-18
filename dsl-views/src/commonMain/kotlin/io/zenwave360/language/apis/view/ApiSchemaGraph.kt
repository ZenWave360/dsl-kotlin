package io.zenwave360.language.apis.view

/** A document found through a `$ref` or an Avro name: [document] is its parsed tree, [base] its identity for further relative refs. */
data class ResolvedNode(val document: Any?, val base: String)

/**
 * Loads the document a reference points to. [ref] is the file part of the reference (no `#fragment`),
 * as written, [base] the identity of the document that contains it (what the previous [ResolvedNode]
 * returned, or the base given to the builder). Null when the document cannot be read, which is not an
 * error: the reference becomes an `<<external>>` stub. dsl-views never touches files or the network.
 */
fun interface RefResolver {
    fun resolve(ref: String, base: String): ResolvedNode?
}

enum class ApiSchemaKind { OBJECT, ENUM, EXTERNAL }

/**
 * One field. [type] is the text shown to people (`string(date-time)`, `Order[]`, `map<string, Item>`,
 * `oneOf: A | B`), [many] is true for arrays and maps, [targets] the classes of the graph the field
 * points to, [required] is null when the source does not distinguish required from optional.
 */
data class ApiField(
    val name: String,
    val type: String,
    val many: Boolean = false,
    val required: Boolean? = null,
    val description: String? = null,
    val targets: List<String> = emptyList(),
)

data class ApiSchema(
    val name: String,
    val kind: ApiSchemaKind,
    val description: String? = null,
    val fields: List<ApiField> = emptyList(),
    val values: List<String> = emptyList(),
)

/** Named classes found in a spec; [unresolved] lists the references that became `<<external>>` stubs. */
class ApiSchemaGraph internal constructor(
    val schemas: Map<String, ApiSchema>,
    val unresolved: List<String>,
) {
    /** [root] first, then everything reachable from it breadth-first, in field order. Empty when [root] is unknown. */
    fun reachableFrom(root: String): List<ApiSchema> {
        val first = schemas[root] ?: return emptyList()
        val seen = linkedSetOf(root)
        val result = mutableListOf(first)
        var i = 0
        while (i < result.size) {
            for (field in result[i++].fields) for (target in field.targets) {
                val schema = schemas[target] ?: continue
                if (seen.add(target)) result += schema
            }
        }
        return result
    }
}
