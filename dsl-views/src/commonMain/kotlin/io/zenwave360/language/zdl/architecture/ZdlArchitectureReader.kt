package io.zenwave360.language.zdl.architecture

import io.zenwave360.language.zdl.ZdlModel
import io.zenwave360.language.zdl.ZdlParser

/**
 * Reads a set of ZDL files and extracts their architecture: modules (one per file), events
 * (internal or external), who consumes them (`@listener`, `@asyncapi`) and direct calls (`@calls`).
 *
 * Problems are reported in [ZdlArchitecture.problems]; nothing here throws.
 */
class ZdlArchitectureReader(private val parser: ZdlParser = ZdlParser()) {

    fun read(sources: List<ZdlSource>): ZdlArchitecture = Reading(parser).read(sources)
}

private class Parsed(val path: String, val module: ArchModule, val model: ZdlModel)

private class Reading(private val parser: ZdlParser) {
    private val problems = mutableListOf<String>()
    private val modules = linkedMapOf<String, ArchModule>()
    private val events = linkedMapOf<Pair<String, String>, ArchEvent>()
    private val foreign = linkedMapOf<Pair<String, String>, ArchForeignEvent>()
    private val consumptions = LinkedHashSet<ArchConsumption>()
    private val calls = LinkedHashSet<ArchCall>()

    fun read(sources: List<ZdlSource>): ZdlArchitecture {
        val parsed = parseAll(sources)
        for (p in parsed) {
            for ((name, event) in p.model.getEvents()) {
                val options = event.map()["options"].map()
                // Inline nested types of an event (`items Foo[] { ... }`) are stored as embedded events.
                if (options["embedded"] == true) continue
                val asyncapi = options["asyncapi"].map()
                val topic = asyncapi.str("topic")
                events[p.module.id to name] = ArchEvent(
                    p.module.id, name, if (topic != null) EventKind.EXTERNAL else EventKind.INTERNAL,
                    asyncapi.str("channel"), topic,
                )
            }
        }
        for (p in parsed) {
            for (m in methodsOf(p.model)) {
                for (option in m.options) {
                    val value = option["value"] as? Map<*, *> ?: continue
                    val args = value.entries.associate { it.key.toString() to it.value }
                    when (option["name"]) {
                        "listener" -> listener(p, m.label, args)
                        "asyncapi" -> asyncapi(p, parsed, m.label, args)
                        "calls" -> call(p, m.label, args)
                    }
                }
            }
        }
        return ZdlArchitecture(
            modules = modules.values.sortedBy { it.id },
            events = events.values.sortedWith(compareBy({ it.module }, { it.name })),
            foreignEvents = foreign.values.sortedWith(compareBy({ it.channel }, { it.api })),
            consumptions = consumptions.sortedWith(
                compareBy({ it.event.module ?: "" }, { it.event.name }, { it.consumer }, { it.method }, { it.via }),
            ),
            calls = calls.sortedWith(compareBy({ it.caller }, { it.callee }, { it.command }, { it.declaredOn })),
            problems = problems.toList(),
        )
    }

    // ---- modules ----------------------------------------------------------------------------

    private class Row(val path: String, val model: ZdlModel)

    private fun parseAll(sources: List<ZdlSource>): List<Parsed> {
        val rows = sources.map { source ->
            val path = normalizePath(source.path)
            val model = try {
                parser.parseModel(source.content)
            } catch (e: Exception) {
                problems += "$path: could not parse ZDL: ${e.message ?: e::class.simpleName}"
                ZdlModel()
            }
            for (problem in model.getProblems()) {
                if (problem["path"] == "syntax" || problem["path"] == "parser") {
                    problems += "$path: ${problem["message"]}"
                }
            }
            Row(path, model)
        }
        val names = displayNames(rows.map { it.model.section("config").str("title") })
        val result = mutableListOf<Parsed>()
        rows.forEachIndexed { i, row ->
            val config = row.model.section("config")
            val title = config.str("title")
            val slug = if (isGeneric(row.path) && !row.path.contains('/') && title != null) {
                kebab(title.substringAfterLast(" - ")).takeIf { it != "module" } ?: slugOf(row.path)
            } else {
                slugOf(row.path)
            }
            val id = pascalCase(slug)
            if (modules.containsKey(id)) {
                problems += "${row.path}: duplicate module '$id', the file is ignored"
                return@forEachIndexed
            }
            val module = ArchModule(
                id = id, slug = slug, name = names[i] ?: titleCase(slug), path = row.path,
                basePackage = config.str("basePackage") ?: config.str("moduleBasePackage"),
                referenced = false,
            )
            modules[id] = module
            result += Parsed(row.path, module, row.model)
        }
        return result
    }

    /**
     * Titles of several modules usually share a prefix like `Arcadia Editions - `: drop it.
     * A single module drops everything up to its last ` - `.
     */
    private fun displayNames(titles: List<String?>): List<String?> {
        if (titles.size == 1) return titles.map { it?.substringAfterLast(" - ") }
        if (titles.size < 2 || titles.any { it == null }) return titles
        val all = titles.filterNotNull()
        var common = all.first()
        for (t in all) common = common.commonPrefixWith(t)
        val cut = common.lastIndexOf(" - ")
        if (cut < 0) return titles
        val prefix = common.substring(0, cut + 3)
        if (all.any { it.length <= prefix.length }) return titles
        return titles.map { it!!.removePrefix(prefix) }
    }

    /** Finds the module for a `zdl client` of [from], creating a referenced module when it is not in the set. */
    private fun clientModule(from: Parsed, client: String, what: String): ArchModule? {
        val api = from.model.section("apis").section(client)
        val uri = api.str("uri")
        if (api.isEmpty() || api.str("type") != "zdl" || uri == null) {
            problems += "${from.path}: $what refers to unknown zdl client '$client'"
            return null
        }
        val isUrl = Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(uri)
        val target = if (isUrl) uri else resolvePath(from.path, uri)
        modules.values.firstOrNull { !it.referenced && it.path == target }?.let { return it }
        val fileName = target.substringAfterLast('/')
        // A ZDL never refers to itself through a client: the same file name in another folder is another module.
        val sameName = modules.values.filter { !it.referenced && it !== from.module && it.path.substringAfterLast('/') == fileName }
        if (sameName.size == 1) return sameName.first()
        // A generic file name (domain-model.zdl) says nothing about a remote module: use the client name.
        val generic = target.substringAfterLast('/').removeSuffix(".zdl") in setOf("domain-model", "model")
        val slug = if (isUrl && generic) kebab(client.removeSuffix("Zdl").removeSuffix("Api")) else slugOf(target)
        val id = pascalCase(slug)
        modules[id]?.let { return it }
        return ArchModule(id, slug, titleCase(slug), target, null, true).also { modules[id] = it }
    }

    // ---- annotations ------------------------------------------------------------------------

    private fun listener(p: Parsed, method: String, args: Map<String, Any?>) {
        val event = args.str("event") ?: return
        val client = args.str("zdl")
        val owner = if (client == null) p.module else clientModule(p, client, "@listener on $method") ?: return
        if (!hasEvent(owner, event)) {
            problems += "${p.path}: @listener on $method refers to event '$event' not found in ${owner.id}"
            return
        }
        consumptions += ArchConsumption(EventRef(owner.id, event), p.module.id, method, ConsumptionVia.LISTENER)
    }

    private fun asyncapi(p: Parsed, all: List<Parsed>, method: String, args: Map<String, Any?>) {
        val channel = args.str("channel") ?: return
        val api = args.str("api")
        val found = if (api == null) {
            events.values.firstOrNull { it.module == p.module.id && it.channel == channel }
        } else {
            events.values.filter { e -> e.channel == channel && all.any { it.module.id == e.module } }
                .minByOrNull { it.module }
        }
        if (found != null) {
            consumptions += ArchConsumption(EventRef(found.module, found.name), p.module.id, method, ConsumptionVia.ASYNCAPI)
        } else if (api != null) {
            foreign.getOrPut(api to channel) { ArchForeignEvent(channel, api) }
            consumptions += ArchConsumption(EventRef(null, channel), p.module.id, method, ConsumptionVia.ASYNCAPI)
        } else {
            problems += "${p.path}: @asyncapi on $method refers to channel '$channel' not found in ${p.module.id}"
        }
    }

    private fun call(p: Parsed, method: String, args: Map<String, Any?>) {
        val client = args.str("zdl") ?: return
        val target = args.str("command") ?: return
        val callee = clientModule(p, client, "@calls on $method") ?: return
        calls += ArchCall(p.module.id, callee.id, target.substringAfterLast('.'), target, method)
    }

    /** True when the event exists; in a referenced module the event is recorded (its ZDL is not available). */
    private fun hasEvent(owner: ArchModule, name: String): Boolean {
        if (owner.referenced) {
            events.getOrPut(owner.id to name) { ArchEvent(owner.id, name, EventKind.INTERNAL) }
            return true
        }
        return (owner.id to name) in events
    }

    private class Method(val label: String, val options: List<Map<*, *>>)

    private fun methodsOf(model: ZdlModel): List<Method> {
        val result = mutableListOf<Method>()
        for ((serviceName, service) in model.section("services")) {
            for ((name, method) in service.map().section("methods")) result += Method("$serviceName.$name", optionsOf(method))
        }
        for ((aggregateName, aggregate) in model.section("aggregates")) {
            for ((name, command) in aggregate.map().section("commands")) result += Method("$aggregateName.$name", optionsOf(command))
        }
        return result
    }

    private fun optionsOf(method: Any?): List<Map<*, *>> =
        (method.map()["optionsList"] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
}

// ---- helpers --------------------------------------------------------------------------------

@Suppress("UNCHECKED_CAST")
private fun Any?.map(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

private fun ZdlModel.section(key: String): Map<String, Any?> = this[key].map()

private fun Map<String, Any?>.section(key: String): Map<String, Any?> = this[key].map()

private fun Map<String, Any?>.str(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

private fun normalizePath(path: String): String {
    val unix = path.replace('\\', '/')
    val parts = ArrayDeque<String>()
    var leadingUps = 0
    for (part in unix.split('/')) {
        when (part) {
            "", "." -> {}
            ".." -> if (parts.isNotEmpty() && parts.last() != "..") parts.removeLast() else leadingUps++
            else -> parts.addLast(part)
        }
    }
    return (if (unix.startsWith("/")) "/" else "") + "../".repeat(leadingUps) + parts.joinToString("/")
}

private fun resolvePath(from: String, relative: String): String {
    val rel = relative.replace('\\', '/')
    if (rel.startsWith("/") || Regex("^[A-Za-z]:/").containsMatchIn(rel)) return normalizePath(rel)
    return normalizePath(from.substringBeforeLast('/', "") + "/" + rel)
}

/** `domain-model.zdl` and `model.zdl` say nothing about the module. */
private fun isGeneric(path: String) = path.substringAfterLast('/').removeSuffix(".zdl") in setOf("domain-model", "model")

/** `payments-processing-domain-model.zdl` -> `payments-processing`; `domain-model.zdl` -> its folder name. */
private fun slugOf(path: String): String {
    val file = path.substringAfterLast('/').removeSuffix(".zdl")
    val folder = path.substringBeforeLast('/', "").substringAfterLast('/')
    val stripped = file.removeSuffix("-domain-model").removeSuffix("-model")
    val base = if (file == "domain-model" || file == "model" || stripped.isEmpty()) folder.ifEmpty { file } else stripped
    return base.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "module" }
}

private fun words(slug: String) = slug.split('-').filter { it.isNotEmpty() }

private fun pascalCase(slug: String): String {
    val id = words(slug).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    return if (id.firstOrNull()?.isDigit() == true) "M$id" else id
}

private fun titleCase(slug: String) = words(slug).joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

private fun kebab(name: String) = name.replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "module" }
