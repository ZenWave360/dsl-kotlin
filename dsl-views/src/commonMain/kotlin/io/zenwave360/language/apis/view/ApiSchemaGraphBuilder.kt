package io.zenwave360.language.apis.view

/**
 * Builds an [ApiSchemaGraph] from the Map/List trees of a parsed OpenAPI/AsyncAPI/JSON Schema/Avro file
 * (every scalar a string, as dsl-docs reads them). One builder per spec file: add each root you want to
 * draw with [addJsonSchema] or [addAvro], then call [build]; classes shared by several roots appear once.
 *
 * [document] is the tree that local references (`#/components/schemas/X`) resolve against and [base] its
 * identity for [resolver]. References that cannot be resolved never throw: they become `EXTERNAL` stubs
 * named after the last segment of the reference, and are listed in [ApiSchemaGraph.unresolved].
 */
class ApiSchemaGraphBuilder(
    private val document: Any? = null,
    private val base: String = "",
    private val resolver: RefResolver? = null,
) {
    private class Ctx(val document: Any?, val base: String)

    private class Info(val text: String, val many: Boolean = false, val targets: List<String> = emptyList())

    private val schemas = linkedMapOf<String, ApiSchema>()
    private val building = mutableSetOf<String>()
    private val unresolved = linkedSetOf<String>()
    private val avroNames = mutableMapOf<String, String>()
    private val loaded = mutableMapOf<String, ResolvedNode?>()

    fun build(): ApiSchemaGraph = ApiSchemaGraph(schemas.toMap(), unresolved.toList())

    // ---------------------------------------------------------------- JSON Schema

    /**
     * Adds a JSON Schema (or a `{$ref}` to one) and everything it reaches; returns the name of its class.
     * [name] names an inline schema; a `$ref` is named after the reference. An array root is unwrapped to
     * its item class, and a root that is not an object nor an enum becomes an empty class.
     */
    fun addJsonSchema(name: String?, schema: Any?, document: Any? = this.document, base: String = this.base): String =
        jsonRoot(name, schema, Ctx(document, base), 0)

    private fun jsonRoot(name: String?, schema: Any?, ctx: Ctx, depth: Int): String {
        val s = schema.asMap()
        s.str("\$ref")?.let { ref ->
            val r = resolveRef(ref, ctx) ?: return stub(ref)
            return jsonRoot(refName(ref), r.first, r.second, depth + 1)
        }
        val own = name ?: "Schema"
        if (depth > 30) return own
        if (typeOf(s) == "array" && s["items"] != null) return jsonRoot(name, s["items"], ctx, depth + 1)
        return jsonClass(own, s, ctx)
    }

    private fun jsonClass(name: String, s: Map<String, Any?>, ctx: Ctx): String {
        if (name in schemas || !building.add(name)) return name
        val enumValues = s["enum"].asList().map { it.toString() }
        val schema = if (enumValues.isNotEmpty()) {
            ApiSchema(name, ApiSchemaKind.ENUM, s.str("description"), values = enumValues)
        } else {
            val props = linkedMapOf<String, Pair<Any?, Ctx>>()
            val required = linkedSetOf<String>()
            val descriptions = mutableListOf<String>()
            collect(s, ctx, props, required, descriptions, 0)
            val fields = props.map { (field, p) ->
                val schemaOfField = p.first.asMap()
                val info = jsonType(schemaOfField, p.second, name, field, 0)
                ApiField(field, info.text, info.many, field in required, schemaOfField.str("description"), info.targets)
            }.toMutableList()
            if (fields.isEmpty()) {
                for (combinator in listOf("oneOf", "anyOf")) {
                    if (s[combinator].asList().isEmpty()) continue
                    val info = jsonType(s, ctx, name, combinator, 0)
                    fields += ApiField(combinator, info.text.removePrefix("$combinator: "), false, null, null, info.targets)
                }
            }
            ApiSchema(name, ApiSchemaKind.OBJECT, descriptions.firstOrNull(), fields)
        }
        building.remove(name)
        schemas[name] = schema
        return name
    }

    /** Properties, required names and description of [s], with `allOf` parts (inline or `$ref`) merged in. */
    private fun collect(
        s: Map<String, Any?>, ctx: Ctx, props: MutableMap<String, Pair<Any?, Ctx>>,
        required: MutableSet<String>, descriptions: MutableList<String>, depth: Int,
    ) {
        if (depth > 30) return
        for (part in s["allOf"].asList()) {
            val p = part.asMap()
            val ref = p.str("\$ref")
            if (ref != null) {
                val r = resolveRef(ref, ctx)
                if (r == null) stub(ref) else collect(r.first.asMap(), r.second, props, required, descriptions, depth + 1)
            } else {
                collect(p, ctx, props, required, descriptions, depth + 1)
            }
        }
        s.str("description")?.let { descriptions += it }
        for ((k, v) in s["properties"].asMap()) props[k] = v to ctx
        s["required"].asList().forEach { required += it.toString() }
    }

    private fun jsonType(s: Map<String, Any?>, ctx: Ctx, parent: String, field: String, depth: Int): Info {
        if (depth > 30) return Info("any")
        s.str("\$ref")?.let { ref ->
            val r = resolveRef(ref, ctx) ?: return stub(ref).let { Info(it, false, listOf(it)) }
            val node = r.first.asMap()
            val name = refName(ref)
            return if (classLike(node)) Info(jsonClass(name, node, r.second), false, listOf(name))
            else jsonType(node, r.second, parent, field, depth + 1)
        }
        val allOf = s["allOf"].asList()
        if (allOf.size == 1 && allOf[0].asMap().containsKey("\$ref")) return jsonType(allOf[0].asMap(), ctx, parent, field, depth + 1)
        val inlineName = parent + field.replaceFirstChar { it.uppercase() }
        if (allOf.isNotEmpty() || s["enum"].asList().isNotEmpty() || hasProperties(s)) {
            return Info(jsonClass(inlineName, s, ctx), false, listOf(inlineName))
        }
        for (combinator in listOf("oneOf", "anyOf")) {
            val parts = s[combinator].asList()
            if (parts.isEmpty()) continue
            val infos = parts.map { jsonType(it.asMap(), ctx, parent, field, depth + 1) }
            return Info("$combinator: " + infos.joinToString(" | ") { it.text }, false, infos.flatMap { it.targets }.distinct())
        }
        when (typeOf(s)) {
            "array" -> {
                val items = s["items"].asMap()
                val inner = if (items.isEmpty()) Info("any") else jsonType(items, ctx, parent, field, depth + 1)
                return Info(inner.text + "[]", true, inner.targets)
            }
            "object" -> {
                val additional = s["additionalProperties"]
                if (additional is Map<*, *>) {
                    val inner = jsonType(additional.asMap(), ctx, parent, field, depth + 1)
                    return Info("map<string, ${inner.text}>", true, inner.targets)
                }
                return Info("object")
            }
        }
        val type = typeOf(s) ?: return Info("any")
        return Info(s.str("format")?.let { "$type($it)" } ?: type)
    }

    private fun hasProperties(s: Map<String, Any?>) = s["properties"].asMap().isNotEmpty()

    private fun classLike(s: Map<String, Any?>): Boolean =
        hasProperties(s) || s["allOf"].asList().isNotEmpty() || s["enum"].asList().isNotEmpty() ||
            s["oneOf"].asList().isNotEmpty() || s["anyOf"].asList().isNotEmpty() ||
            (typeOf(s) == "object" && s["additionalProperties"] !is Map<*, *>)

    /** `type`, or the first non-null entry of an OpenAPI 3.1 `type: [string, "null"]`. */
    private fun typeOf(s: Map<String, Any?>): String? = when (val t = s["type"]) {
        is String -> t
        is List<*> -> t.map { it.toString() }.firstOrNull { it != "null" }
        else -> if (hasProperties(s)) "object" else null
    }

    // ---------------------------------------------------------------- references

    /** The node a reference points to and the context to read it in; null when it cannot be resolved. */
    private fun resolveRef(ref: String, ctx: Ctx): Pair<Any?, Ctx>? {
        val file = ref.substringBefore('#')
        val pointer = if ('#' in ref) ref.substringAfter('#') else ""
        var target = ctx
        if (file.isNotEmpty()) {
            if (file.startsWith("http://") || file.startsWith("https://")) return null
            val resolved = load(file, ctx.base) ?: return null
            target = Ctx(resolved.document, resolved.base)
        }
        return pointer(target.document, pointer)?.let { it to target }
    }

    private fun load(file: String, base: String): ResolvedNode? =
        loaded.getOrPut("$base\u0000$file") { resolver?.resolve(file, base) }

    private fun pointer(document: Any?, pointer: String): Any? {
        var node = document
        for (raw in pointer.split('/').filter { it.isNotEmpty() }) {
            val key = raw.replace("~1", "/").replace("~0", "~")
            node = when (node) {
                is Map<*, *> -> node[key]
                is List<*> -> key.toIntOrNull()?.let { node.getOrNull(it) }
                else -> null
            } ?: return null
        }
        return node
    }

    private fun refName(ref: String): String {
        val pointer = ref.substringAfter('#', "")
        val last = if (pointer.isNotEmpty()) pointer.substringAfterLast('/') else ref.substringBefore('#').substringAfterLast('/')
        return last.substringBeforeLast('.', last).ifEmpty { last }.ifEmpty { "External" }
    }

    private fun stub(ref: String): String {
        val name = refName(ref)
        unresolved += ref
        if (name !in schemas) schemas[name] = ApiSchema(name, ApiSchemaKind.EXTERNAL, "Defined in $ref")
        return name
    }

    // ---------------------------------------------------------------- Avro

    /**
     * Adds an Avro schema (record, enum, or a union/array of them) and every named type it defines or
     * reaches; returns the name of its class. A name that is not defined in the schema is loaded through
     * the resolver as `<Name>.avsc` next to the current document (the layout of the `avro/` folders),
     * else it becomes an `<<external>>` stub.
     */
    fun addAvro(schema: Any?, base: String = this.base): String {
        val info = avro(schema, base, "", 0).first
        val name = info.targets.firstOrNull() ?: "Schema"
        if (name !in schemas) schemas[name] = ApiSchema(name, ApiSchemaKind.OBJECT)
        return name
    }

    private val primitives = setOf("null", "boolean", "int", "long", "float", "double", "bytes", "string")

    /** The type and whether it is optional (a union with `null`). */
    private fun avro(t: Any?, base: String, namespace: String, depth: Int): Pair<Info, Boolean> {
        if (depth > 40) return Info("any") to false
        when (t) {
            is String -> return avroName(t, base, namespace, depth) to false
            is List<*> -> {
                val optional = t.any { it == "null" }
                val parts = t.filter { it != "null" }.map { avro(it, base, namespace, depth + 1).first }
                val info = when (parts.size) {
                    0 -> Info("null")
                    1 -> parts[0]
                    else -> Info(parts.joinToString(" | ") { it.text }, false, parts.flatMap { it.targets }.distinct())
                }
                return info to optional
            }
            is Map<*, *> -> {
                val m = t.asMap()
                val ns = m.str("namespace") ?: m.str("name")?.takeIf { '.' in it }?.substringBeforeLast('.') ?: namespace
                return when (val type = m["type"]) {
                    "record", "error" -> avroRecord(m, base, ns, depth) to false
                    "enum" -> avroEnum(m, ns) to false
                    "array" -> avro(m["items"], base, ns, depth + 1).first.let { Info(it.text + "[]", true, it.targets) } to false
                    "map" -> avro(m["values"], base, ns, depth + 1).first.let { Info("map<string, ${it.text}>", true, it.targets) } to false
                    "fixed" -> Info("fixed(${m.str("size") ?: ""})") to false
                    is String -> if (type in primitives) {
                        Info(m.str("logicalType")?.let { "$type($it)" } ?: type) to false
                    } else avro(type, base, ns, depth + 1)
                    else -> avro(type, base, ns, depth + 1)
                }
            }
            else -> return Info("any") to false
        }
    }

    private fun avroName(name: String, base: String, namespace: String, depth: Int): Info {
        if (name in primitives) return Info(name)
        val known = avroNames[name] ?: avroNames["$namespace.$name"]
        if (known != null) return Info(known, false, listOf(known))
        val simple = name.substringAfterLast('.')
        val resolved = load("$simple.avsc", base)
        if (resolved != null) return avro(resolved.document, resolved.base, namespace, depth + 1).first
        unresolved += name
        if (simple !in schemas) schemas[simple] = ApiSchema(simple, ApiSchemaKind.EXTERNAL, "Defined in $name")
        return Info(simple, false, listOf(simple))
    }

    private fun avroRecord(m: Map<String, Any?>, base: String, namespace: String, depth: Int): Info {
        val full = m.str("name").orEmpty()
        val name = full.substringAfterLast('.')
        register(name, full, namespace)
        if (name in schemas || !building.add(name)) return Info(name, false, listOf(name))
        val fields = m["fields"].asList().map { raw ->
            val f = raw.asMap()
            val (info, optional) = avro(f["type"], base, namespace, depth + 1)
            ApiField(f.str("name").orEmpty(), info.text, info.many, !optional, f.str("doc"), info.targets)
        }
        building.remove(name)
        schemas[name] = ApiSchema(name, ApiSchemaKind.OBJECT, m.str("doc"), fields)
        return Info(name, false, listOf(name))
    }

    private fun avroEnum(m: Map<String, Any?>, namespace: String): Info {
        val full = m.str("name").orEmpty()
        val name = full.substringAfterLast('.')
        register(name, full, namespace)
        schemas.getOrPut(name) { ApiSchema(name, ApiSchemaKind.ENUM, m.str("doc"), values = m["symbols"].asList().map { it.toString() }) }
        return Info(name, false, listOf(name))
    }

    private fun register(name: String, full: String, namespace: String) {
        avroNames[name] = name
        avroNames[full] = name
        if (namespace.isNotEmpty()) avroNames["$namespace.$name"] = name
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

private fun Any?.asList(): List<Any?> = this as? List<Any?> ?: emptyList()

private fun Map<String, Any?>.str(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }
