package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.ZdlModel

/**
 * Renders a parsed ZDL model as a Mermaid `classDiagram`.
 *
 * The diagram carries the content of the SDK's PlantUML class diagrams (ZdlToMarkdownPlugin):
 * aggregates with their commands, entities with their fields, enums with their values, inputs,
 * outputs and events, services with their commands, and the relationships between them with
 * their cardinality.
 *
 * The output is inert: it never contains `click`, `link` or `callback` directives, init
 * directives, styles, links or remote references. Names are reduced to Mermaid-safe
 * identifiers and member text is stripped of characters Mermaid would interpret.
 */
class ZdlToMermaidClassDiagramTransformer {

    fun transform(model: ZdlModel): String {
        val aggregates = model.section("aggregates")
        val entities = model.section("entities")
        val enums = model.section("enums")
        val inputs = model.section("inputs")
        val outputs = model.section("outputs")
        val events = model.section("events")
        val services = model.section("services")

        val declared = linkedMapOf<String, String>()
        aggregates.keys.forEach { declared[it] = "aggregates" }
        entities.keys.forEach { declared.getOrPut(it) { "entities" } }
        enums.keys.forEach { declared.getOrPut(it) { "enums" } }
        inputs.keys.forEach { declared.getOrPut(it) { "inputs" } }
        outputs.keys.forEach { declared.getOrPut(it) { "outputs" } }
        events.keys.forEach { declared.getOrPut(it) { "events" } }

        val allTypes = linkedMapOf<String, Any?>().apply {
            putAll(events); putAll(outputs); putAll(inputs); putAll(enums); putAll(entities)
        }

        val aggregateRoots = aggregates.values.mapNotNull { it.map()["aggregateRoot"] as? String }.toSet()

        val classes = mutableListOf<String>()
        val links = LinkedHashSet<String>()

        for ((name, aggregate) in aggregates) {
            val members = aggregate.map().section("commands").values.map { command ->
                val c = command.map()
                val parameter = (c["parameter"] as? String)?.let { it + if (c["parameterIsOptional"] == true) "?" else "" }
                method(c["name"] as? String ?: "", listOfNotNull(parameter), null, c["withEvents"])
            }
            classes += classBlock(name, "aggregate", members)
            (aggregate.map()["aggregateRoot"] as? String)?.let { root ->
                if (entities.containsKey(root)) links += "${id(name)} *-- ${id(root)}"
            }
        }

        for ((name, entity) in entities) {
            val e = entity.map()
            val stereotype = when {
                name in aggregateRoots || e.section("options")["aggregate"] == true -> "aggregate"
                e.section("options")["embedded"] == true -> "embedded"
                else -> null
            }
            classes += classBlock(name, stereotype, fieldMembers(e))
            links += fieldLinks(name, e, declared, allTypes)
        }

        for ((name, enum) in enums) {
            val members = enum.map().section("values").values.map { v ->
                val value = v.map()
                val valueName = value["name"] as? String ?: ""
                val literal = value["value"]?.toString()
                if (literal == null) member(valueName) else member("$valueName = $literal")
            }
            classes += classBlock(name, "enumeration", members)
        }

        for ((collection, stereotype) in listOf(inputs to "input", outputs to "output", events to "event")) {
            for ((name, type) in collection) {
                classes += classBlock(name, stereotype, fieldMembers(type.map()))
                links += fieldLinks(name, type.map(), declared, allTypes)
            }
        }

        for ((name, service) in services) {
            val s = service.map()
            val methods = s.section("methods").values.map { it.map() }
            val members = methods.map { m ->
                val params = listOfNotNull(
                    m["paramId"]?.let { "id" + if (m["paramIdIsOptional"] == true) "?" else "" },
                    (m["parameter"] as? String)?.let { it + if (m["parameterIsOptional"] == true) "?" else "" },
                )
                val returnType = (m["returnType"] as? String)?.let {
                    it + (if (m["returnTypeIsArray"] == true) "[]" else "") + (if (m["returnTypeIsOptional"] == true) "?" else "")
                }
                method(m["name"] as? String ?: "", params, returnType, m["withEvents"])
            }
            classes += classBlock(name, "service", members)
            for (aggregate in (s["aggregates"] as? List<*>).orEmpty()) {
                val target = aggregate as? String ?: continue
                if (declared.containsKey(target)) links += "${id(name)} ..> ${id(target)}"
            }
            for (m in methods) {
                val types = listOfNotNull(m["parameter"] as? String, m["returnType"] as? String) +
                    flattenEvents(m["withEvents"])
                for (type in types) {
                    val kind = declared[type] ?: continue
                    if (kind == "inputs" || kind == "outputs" || kind == "events") {
                        links += "${id(name)} ..> ${id(type)}"
                    }
                }
            }
        }

        val relationships = model.section("relationships")
        for ((relationshipType, byName) in relationships) {
            val (fromCardinality, toCardinality) = when (relationshipType) {
                "OneToOne" -> "1" to "1"
                "OneToMany" -> "1" to "*"
                "ManyToOne" -> "*" to "1"
                "ManyToMany" -> "*" to "*"
                else -> continue
            }
            for (relationship in byName.map().values) {
                val r = relationship.map()
                val from = r["from"] as? String ?: continue
                val to = r["to"] as? String ?: continue
                val fromField = r["injectedFieldInFrom"] as? String
                val toField = r["injectedFieldInTo"] as? String
                val arrow = when {
                    fromField != null && toField == null -> "-->"
                    fromField == null && toField != null -> "<--"
                    else -> "--"
                }
                val label = listOfNotNull(fromField, toField).joinToString(" / ") { memberText(it) }
                val suffix = if (label.isBlank()) "" else " : $label"
                links += "${id(from)} \"$fromCardinality\" $arrow \"$toCardinality\" ${id(to)}$suffix"
            }
        }

        return buildString {
            append("classDiagram\n")
            for (block in classes) append(block)
            for (link in links) append("    ").append(link).append('\n')
            // A classDiagram with no statements is not valid Mermaid; say why it is empty instead.
            if (classes.isEmpty() && links.isEmpty()) append("    note \"No types declared\"\n")
        }
    }

    private fun fieldMembers(type: Map<String, Any?>): List<String> =
        type.section("fields").values.map { f ->
            val field = f.map()
            val fieldType = (field["type"] as? String ?: "") + if (field["isArray"] == true) "[]" else ""
            member("$fieldType ${field["name"] as? String ?: ""}")
        }

    private fun fieldLinks(
        source: String,
        type: Map<String, Any?>,
        declared: Map<String, String>,
        allTypes: Map<String, Any?>,
    ): List<String> = type.section("fields").values.mapNotNull { f ->
        val field = f.map()
        val target = field["type"] as? String ?: return@mapNotNull null
        if (target == source || !declared.containsKey(target)) return@mapNotNull null
        val embedded = allTypes[target].map().section("options")["embedded"] == true
        val arrow = if (embedded) "*--" else "-->"
        val cardinality = if (field["isArray"] == true) "\"*\" " else ""
        "${id(source)} $arrow $cardinality${id(target)} : ${memberText(field["name"] as? String ?: "")}"
    }

    private fun classBlock(name: String, stereotype: String?, members: List<String>): String = buildString {
        val identifier = id(name)
        val label = labelText(name)
        if (identifier != name) {
            append("    class ").append(identifier).append("[\"").append(label).append("\"]\n")
        }
        if (stereotype == null && members.isEmpty()) {
            if (identifier == name) append("    class ").append(identifier).append('\n')
            return@buildString
        }
        append("    class ").append(identifier).append(" {\n")
        stereotype?.let { append("        <<").append(it).append(">>\n") }
        members.filter { it.isNotBlank() }.forEach { append("        ").append(it).append('\n') }
        append("    }\n")
    }

    private fun method(name: String, params: List<String>, returnType: String?, withEvents: Any?): String {
        val events = renderEvents(withEvents)
        val tail = listOfNotNull(returnType, events?.let { "withEvents $it" }).joinToString(" ")
        return memberText("$name(${params.joinToString(", ")})" + if (tail.isEmpty()) "" else " $tail")
    }

    private fun member(text: String): String = memberText(text)

    private fun renderEvents(withEvents: Any?): String? {
        val list = withEvents as? List<*> ?: return null
        if (list.isEmpty()) return null
        return list.joinToString(" ") { event ->
            when (event) {
                is List<*> -> event.joinToString(" or ") { it.toString() }
                else -> event.toString()
            }
        }
    }

    private fun flattenEvents(withEvents: Any?): List<String> =
        (withEvents as? List<*>).orEmpty().flatMap { e ->
            when (e) {
                is List<*> -> e.mapNotNull { it as? String }
                is String -> listOf(e)
                else -> emptyList()
            }
        }

    companion object {
        private val RESERVED = setOf(
            "classDiagram", "class", "namespace", "direction", "click", "link", "callback", "call",
            "href", "note", "style", "classDef", "cssClass", "end", "accTitle", "accDescr", "title",
        )

        /** A Mermaid-safe class identifier: letters, digits and underscores, never a keyword. */
        internal fun id(name: String): String {
            var identifier = name.replace(Regex("[^A-Za-z0-9_]"), "_")
            if (identifier.isEmpty() || identifier[0].isDigit()) identifier = "_$identifier"
            if (RESERVED.any { it.equals(identifier, ignoreCase = true) }) identifier += "_"
            return identifier
        }

        /** Member and label text with every character Mermaid could interpret removed. */
        internal fun memberText(text: String): String =
            text.replace(Regex("[\\r\\n]+"), " ")
                .replace(Regex("[{}<>\"'`~;#%:$*\\\\]"), "")
                .trim()

        private fun labelText(text: String): String = memberText(text).replace("[", "").replace("]", "")
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any?.map(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any?>.section(key: String): Map<String, Any?> = this[key] as? Map<String, Any?> ?: emptyMap()
