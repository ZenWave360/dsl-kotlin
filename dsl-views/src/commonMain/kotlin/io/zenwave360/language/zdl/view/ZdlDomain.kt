package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.ZdlModel

/** The kind of a named type declared in a ZDL file. */
enum class ZdlTypeKind { ENTITY, ENUM, INPUT, OUTPUT, EVENT }

/**
 * A type declared in the ZDL. [embedded] is true for inline nested types (`items Item[] { ... }`).
 * [channel] and [topic] come from `@asyncapi` and are only set on events.
 */
data class ZdlType(
    val name: String,
    val kind: ZdlTypeKind,
    val javadoc: String?,
    val embedded: Boolean,
    val channel: String? = null,
    val topic: String? = null,
)

/**
 * A field of an entity, input, output or event. [copied] is true when it comes from an `@copy(X)`;
 * [json] is true when the field is annotated with `@json`.
 */
data class ZdlField(
    val name: String,
    val type: String,
    val isArray: Boolean,
    val required: Boolean,
    val javadoc: String?,
    val copied: Boolean = false,
    val json: Boolean = false,
) {
    /** The type as written in the ZDL, with `@json` and `[]` when present: `@json OrderItem[]`. */
    val typeText: String
        get() = (if (json) "@json " else "") + type + (if (isArray) "[]" else "")
}

data class ZdlEnumValue(val name: String, val value: String?, val javadoc: String?)

/** `@transition(from?, to)` of a command, both sides normalized to lists. No `from` means creation. */
data class ZdlTransition(val from: List<String>, val to: List<String>)

/** `@lifecycle(field, initial)` of an aggregate root. */
data class ZdlLifecycle(val field: String, val initial: String?)

/**
 * What triggers a command: a REST endpoint, an event listener or an AsyncAPI channel.
 * Read from the method annotations by [ZdlDomain]; `ArchCommand.adapters` adds the resolved [module].
 */
sealed interface CommandAdapter {
    /**
     * `@get/@post/@put/@patch/@delete` with an optional path (`@post`, `@post("/x")`, `@post({ path: "/x" })`).
     * [method] is upper case; [path] is the service-level `@rest` base joined with the method path,
     * slashes normalized (leading `/`, no double or trailing `/`; `/` when both are empty).
     */
    data class Rest(val method: String, val path: String) : CommandAdapter

    /**
     * `@listener(event: E)` or `@listener(zdl: X, event: E)`. [zdl] is the client name, null for an own event;
     * [module] is the id of the module that owns the event, when it could be resolved.
     */
    data class Listener(val event: String, val zdl: String?, val module: String? = null) : CommandAdapter

    /**
     * `@asyncapi(channel: C)` or `@asyncapi(api: A, channel: C)`. [module] and [event] identify the event of
     * that channel when it belongs to a module in the set (null for a foreign event).
     */
    data class AsyncApi(val channel: String, val api: String?, val module: String? = null, val event: String? = null) : CommandAdapter
}

/**
 * A service method or an aggregate command.
 *
 * [events] holds one list per `withEvents` entry; a list with several names is an alternative
 * (`[A | B]`). A command is a [query] when it has no events, no `@transition`, no `@listener`,
 * no `@asyncapi` and no `@calls`; a query changes nothing and is left out of the flow diagrams.
 */
data class ZdlCommand(
    val owner: String,
    val name: String,
    val hasId: Boolean,
    val parameter: String?,
    val parameterIsOptional: Boolean,
    val returnType: String?,
    val returnIsArray: Boolean,
    val returnIsOptional: Boolean,
    val events: List<List<String>>,
    val query: Boolean,
    val transition: ZdlTransition?,
    val javadoc: String?,
    /** The adapters that trigger the command, in declaration order. Queries may have a REST adapter too. */
    val adapters: List<CommandAdapter> = emptyList(),
) {
    /** Every event name, in order of appearance, without repeats. */
    val eventNames: List<String>
        get() = events.flatten().distinct()
}

/** A service. [aggregates] are the names in `service X for (A, B)`: aggregate or entity names. */
data class ZdlService(
    val name: String,
    val aggregates: List<String>,
    val javadoc: String?,
    val methods: List<ZdlCommand>,
) {
    val commands: List<ZdlCommand>
        get() = methods.filter { !it.query }
    val queries: List<ZdlCommand>
        get() = methods.filter { it.query }
}

/**
 * An aggregate: an `aggregate X(Root) { ... }` block or an entity annotated `@aggregate`.
 * [name] is the block name, or the entity name when there is no block; [root] is the root entity.
 * [commands] are the commands declared inside an aggregate block.
 */
data class ZdlAggregate(
    val name: String,
    val root: String,
    val javadoc: String?,
    val lifecycle: ZdlLifecycle?,
    val commands: List<ZdlCommand>,
)

/**
 * What an aggregate root reaches. [entities] and [enums] are in discovery order (breadth first,
 * fields before relationships) and exclude the root; [otherRoots] are other aggregate roots met on
 * the way, which are never descended into.
 */
data class ZdlReachable(
    val root: String,
    val entities: List<String>,
    val enums: List<String>,
    val otherRoots: List<String>,
)

/** A relationship as declared: `From{fromField} to To{toField}` with the cardinality of each side. */
internal class ZdlRelationship(
    val from: String,
    val to: String,
    val fromField: String?,
    val toField: String?,
    val fromCardinality: String,
    val toCardinality: String,
)

/**
 * Read helpers over a parsed [ZdlModel], shared by the diagram transformers and the documentation
 * pages so nobody re-derives them: types, services and commands (command vs query), aggregates,
 * lifecycle, `@copy` field resolution and reachability from an aggregate root.
 */
class ZdlDomain(private val model: ZdlModel) {

    private val sections = ZdlTypeKind.entries.associateWith { kind -> model.sub(sectionOf(kind)) }

    /** All declared types by name; entities win over the other kinds on a name clash, like in the class diagram. */
    val types: Map<String, ZdlType>

    val services: List<ZdlService>
    val aggregates: List<ZdlAggregate>

    /** Entities that are the root of an aggregate. */
    val aggregateRoots: Set<String>

    init {
        val all = linkedMapOf<String, ZdlType>()
        for (kind in listOf(ZdlTypeKind.ENTITY, ZdlTypeKind.ENUM, ZdlTypeKind.INPUT, ZdlTypeKind.OUTPUT, ZdlTypeKind.EVENT)) {
            for ((name, declaration) in sections.getValue(kind)) {
                val d = declaration.asMap()
                val options = d.sub("options")
                val asyncapi = options.sub("asyncapi")
                all.getOrPut(name) {
                    ZdlType(
                        name, kind, d.text("javadoc"), options["embedded"] == true,
                        asyncapi.text("channel"), asyncapi.text("topic"),
                    )
                }
            }
        }
        types = all

        val found = linkedMapOf<String, ZdlAggregate>()
        for ((name, block) in model.sub("aggregates")) {
            val b = block.asMap()
            val root = b.text("aggregateRoot") ?: continue
            val rootEntity = model.sub("entities").sub(root)
            found[name] = ZdlAggregate(
                name, root, b.text("javadoc") ?: rootEntity.text("javadoc"), lifecycleOf(rootEntity, b),
                b.sub("commands").map { (commandName, c) -> command(name, commandName, c.asMap()) },
            )
        }
        for ((name, entity) in model.sub("entities")) {
            val e = entity.asMap()
            if (e.sub("options")["aggregate"] != true || found.values.any { it.root == name }) continue
            found[name] = ZdlAggregate(name, name, e.text("javadoc"), lifecycleOf(e, emptyMap()), emptyList())
        }
        aggregates = found.values.toList()
        aggregateRoots = aggregates.map { it.root }.toSet()

        services = model.sub("services").map { (name, service) ->
            val s = service.asMap()
            ZdlService(
                name, (s["aggregates"] as? List<*>).orEmpty().filterIsInstance<String>(), s.text("javadoc"),
                s.sub("methods").map { (methodName, m) -> command(name, methodName, m.asMap(), restBase(s)) },
            )
        }
    }

    /** Events declared at the top level of the ZDL (inline nested types are not events). */
    val events: List<ZdlType>
        get() = types.values.filter { it.kind == ZdlTypeKind.EVENT && !it.embedded }

    fun typesOf(kind: ZdlTypeKind): List<ZdlType> = types.values.filter { it.kind == kind }

    fun kindOf(type: String): ZdlTypeKind? = types[type]?.kind

    /** The aggregates a service works on: `for (A, B)` entries matched by aggregate name or root name. */
    fun aggregatesOf(service: ZdlService): List<ZdlAggregate> =
        service.aggregates.mapNotNull { n -> aggregates.firstOrNull { it.name == n } ?: aggregates.firstOrNull { it.root == n } }

    // ---- fields ------------------------------------------------------------------------------

    /**
     * Fields of [type] with `@copy(X)` resolved: X's fields come first, followed by the type's own
     * fields; an own field with the same name replaces the copied one in place. Relationships are
     * not copied. X may be an entity, input, output or event of the same ZDL (resolved recursively);
     * an unknown X, an enum or a cycle contributes nothing. Works for any type.
     */
    fun fieldsOf(type: String): List<ZdlField> = resolveFields(type, mutableSetOf())

    /** Own fields only, ignoring `@copy`. */
    fun ownFieldsOf(type: String): List<ZdlField> =
        declaration(type)?.sub("fields")?.map { (name, f) -> field(name, f.asMap()) }.orEmpty()

    /** Names given to `@copy` on [type], in order. */
    fun copiesOf(type: String): List<String> = names(declaration(type).asMap().sub("options")["copy"])

    private fun resolveFields(type: String, visiting: MutableSet<String>): List<ZdlField> {
        val own = ownFieldsOf(type)
        if (!visiting.add(type)) return own
        val result = mutableListOf<ZdlField>()
        for (source in copiesOf(type)) {
            if (source in visiting || types[source] == null || types[source]?.kind == ZdlTypeKind.ENUM) continue
            for (f in resolveFields(source, visiting)) {
                val i = result.indexOfFirst { it.name == f.name }
                val copied = f.copy(copied = true)
                if (i >= 0) result[i] = copied else result += copied
            }
        }
        visiting.remove(type)
        for (f in own) {
            val i = result.indexOfFirst { it.name == f.name }
            if (i >= 0) result[i] = f else result += f
        }
        return result
    }

    fun enumValues(name: String): List<ZdlEnumValue> =
        model.sub("enums").sub(name).sub("values").map { (valueName, v) ->
            val value = v.asMap()
            ZdlEnumValue(valueName, value["value"]?.toString(), value.text("javadoc"))
        }

    /**
     * Types declared inline inside [type] (`items Item[] { ... }`), recursively, in order of
     * appearance. The parser stores them as separate embedded entries; this finds the ones that
     * belong to [type].
     */
    fun nestedTypesOf(type: String): List<String> {
        val result = linkedSetOf<String>()
        fun visit(name: String) {
            for (f in ownFieldsOf(name)) {
                val t = types[f.type] ?: continue
                if (t.embedded && t.kind == types[type]?.kind && t.name != type && result.add(t.name)) visit(t.name)
            }
        }
        visit(type)
        return result.toList()
    }

    // ---- reachability ------------------------------------------------------------------------

    internal val relationships: List<ZdlRelationship> by lazy {
        val result = mutableListOf<ZdlRelationship>()
        for ((relationshipType, byName) in model.sub("relationships")) {
            val (fromCardinality, toCardinality) = when (relationshipType) {
                "OneToOne" -> "1" to "1"
                "OneToMany" -> "1" to "*"
                "ManyToOne" -> "*" to "1"
                "ManyToMany" -> "*" to "*"
                else -> continue
            }
            for (relationship in byName.asMap().values) {
                val r = relationship.asMap()
                val from = r.text("from") ?: continue
                val to = r.text("to") ?: continue
                result += ZdlRelationship(from, to, r.text("injectedFieldInFrom"), r.text("injectedFieldInTo"), fromCardinality, toCardinality)
            }
        }
        result
    }

    /** `A{b} to B` reaches B from A, `A to B{a}` reaches A from B, both fields reach both ways. */
    private val relationshipTargets: Map<String, List<String>> by lazy {
        val result = linkedMapOf<String, MutableList<String>>()
        for (r in relationships) {
            if (r.fromField != null || r.toField == null) result.getOrPut(r.from) { mutableListOf() } += r.to
            if (r.toField != null) result.getOrPut(r.to) { mutableListOf() } += r.from
        }
        result
    }

    /** Everything [root] reaches through fields of entity or enum type and relationships. */
    fun reachableFrom(root: String): ZdlReachable {
        val entities = mutableListOf<String>()
        val enums = mutableListOf<String>()
        val others = mutableListOf<String>()
        val seen = mutableSetOf(root)
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (target in fieldsOf(current).map { it.type } + relationshipTargets[current].orEmpty()) {
                val kind = kindOf(target)
                if (kind != ZdlTypeKind.ENTITY && kind != ZdlTypeKind.ENUM) continue
                if (!seen.add(target)) continue
                when {
                    kind == ZdlTypeKind.ENUM -> enums += target
                    target in aggregateRoots -> others += target
                    else -> { entities += target; queue.addLast(target) }
                }
            }
        }
        return ZdlReachable(root, entities, enums, others)
    }

    /** Entities and enums that no aggregate root is or reaches (the "other entities"), in declaration order. */
    fun unreachableTypes(): List<String> {
        val reached = mutableSetOf<String>()
        for (root in aggregateRoots) {
            val r = reachableFrom(root)
            reached += root; reached += r.entities; reached += r.enums
        }
        return types.values
            .filter { (it.kind == ZdlTypeKind.ENTITY || it.kind == ZdlTypeKind.ENUM) && it.name !in reached }
            .map { it.name }
    }

    // ---- internals ---------------------------------------------------------------------------

    private fun declaration(type: String): Map<String, Any?>? {
        val kind = types[type]?.kind ?: return null
        return sections.getValue(kind)[type].asMap()
    }

    private fun lifecycleOf(entity: Map<String, Any?>, block: Map<String, Any?>): ZdlLifecycle? {
        val map = entity.sub("lifecycle").ifEmpty { entity.sub("options").sub("lifecycle") }
            .ifEmpty { block.sub("lifecycle") }.ifEmpty { block.sub("options").sub("lifecycle") }
        val field = map.text("field") ?: return null
        return ZdlLifecycle(field, map.text("initial"))
    }

    private fun field(name: String, f: Map<String, Any?>) = ZdlField(
        name, f.text("type") ?: "", f["isArray"] == true, f.sub("validations").containsKey("required"), f.text("javadoc"),
        json = f.sub("options").containsKey("json"),
    )

    /** The service-level `@rest("/base")`: `@rest` alone or `@rest({ path: ... })` are also read. */
    private fun restBase(service: Map<String, Any?>): String = pathOf(service.sub("options")["rest"]).orEmpty()

    private fun adaptersOf(m: Map<String, Any?>, base: String): List<CommandAdapter> {
        val result = mutableListOf<CommandAdapter>()
        for (option in (m["optionsList"] as? List<*>).orEmpty()) {
            val o = option.asMap()
            val value = o["value"]
            val args = value.asMap()
            when (val optionName = o["name"] as? String) {
                in REST_METHODS -> result += CommandAdapter.Rest(optionName!!.uppercase(), joinPath(base, pathOf(value)))
                "listener" -> args.text("event")?.let { result += CommandAdapter.Listener(it, args.text("zdl")) }
                "asyncapi" -> args.text("channel")?.let { result += CommandAdapter.AsyncApi(it, args.text("api")) }
            }
        }
        return result
    }

    /** `"/x"` or `{ path: "/x" }`; `true` (annotation without arguments) and anything else is no path. */
    private fun pathOf(value: Any?): String? = when (value) {
        is String -> value
        is Map<*, *> -> value["path"] as? String
        else -> null
    }

    private fun joinPath(base: String, path: String?): String {
        val joined = "/" + (base + "/" + path.orEmpty()).split('/').filter { it.isNotEmpty() }.joinToString("/")
        return joined
    }

    private fun command(owner: String, name: String, m: Map<String, Any?>, restBase: String = ""): ZdlCommand {
        val events = (m["withEvents"] as? List<*>).orEmpty().map { e ->
            when (e) {
                is List<*> -> e.filterIsInstance<String>()
                else -> listOf(e.toString())
            }
        }.filter { it.isNotEmpty() }
        val transition = m.sub("transition").ifEmpty { m.sub("options").sub("transition") }
        val parsed = transition.takeIf { it.isNotEmpty() }?.let {
            val to = names(it["to"])
            if (to.isEmpty()) null else ZdlTransition(names(it["from"]), to)
        }
        val optionNames = (m["optionsList"] as? List<*>).orEmpty().mapNotNull { it.asMap()["name"] as? String } + m.sub("options").keys
        val query = events.isEmpty() && transition.isEmpty() && optionNames.none { it in TRIGGERS }
        return ZdlCommand(
            owner, name, m["paramId"] != null, m.text("parameter"), m["parameterIsOptional"] == true,
            m.text("returnType"), m["returnTypeIsArray"] == true, m["returnTypeIsOptional"] == true,
            events, query, parsed, m.text("javadoc"), adaptersOf(m, restBase),
        )
    }

    private companion object {
        val TRIGGERS = setOf("transition", "listener", "asyncapi", "calls")
        val REST_METHODS = setOf("get", "post", "put", "patch", "delete")

        fun sectionOf(kind: ZdlTypeKind) = when (kind) {
            ZdlTypeKind.ENTITY -> "entities"
            ZdlTypeKind.ENUM -> "enums"
            ZdlTypeKind.INPUT -> "inputs"
            ZdlTypeKind.OUTPUT -> "outputs"
            ZdlTypeKind.EVENT -> "events"
        }

        fun names(value: Any?): List<String> = when (value) {
            is List<*> -> value.mapNotNull { it?.toString() }
            null -> emptyList()
            else -> listOf(value.toString())
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

private fun Map<String, Any?>.sub(key: String): Map<String, Any?> = this[key].asMap()

private fun Map<String, Any?>.text(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }
