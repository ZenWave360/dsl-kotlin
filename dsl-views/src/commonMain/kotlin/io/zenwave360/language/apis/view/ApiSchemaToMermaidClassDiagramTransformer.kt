package io.zenwave360.language.apis.view

import io.zenwave360.language.zdl.view.MermaidLight
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.classBlock
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.id
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.memberText

/**
 * A Mermaid class diagram of a schema root and everything reachable from it (root first, then
 * breadth-first). The root carries [stereotype] (`response`, `request` or `message`); enums are
 * `<<enumeration>>`, unresolved references `<<external>>`. Optional fields get a `?` after the type.
 * Arrays and maps of objects are `*-- "*"` compositions labeled with the field, single objects `*--`,
 * enums and external stubs `-->`. Light styling as in [MermaidLight]: messages use the event tint,
 * responses the aggregate tint, the rest white.
 */
class ApiSchemaToMermaidClassDiagramTransformer {

    /** Null when [root] is not in [graph]. */
    fun transform(root: String, graph: ApiSchemaGraph, stereotype: String = "response"): String? {
        val reachable = graph.reachableFrom(root).takeIf { it.isNotEmpty() } ?: return null
        val classes = StringBuilder()
        val styles = StringBuilder()
        val links = LinkedHashSet<String>()
        for (schema in reachable) {
            val isRoot = schema.name == root
            val kind = when (schema.kind) {
                ApiSchemaKind.ENUM -> "enumeration"
                ApiSchemaKind.EXTERNAL -> "external"
                ApiSchemaKind.OBJECT -> if (isRoot) stereotype else null
            }
            val members = when (schema.kind) {
                ApiSchemaKind.ENUM -> schema.values.map { memberText(it) }
                else -> schema.fields.map { typeText(it) + (if (it.required == false) "?" else "") + " " + memberText(it.name) }
            }
            classes.append(classBlock(schema.name, kind, members))
            val tint = when {
                kind == "enumeration" -> "enumeration"
                isRoot && kind == stereotype && stereotype == "message" -> "event"
                isRoot && kind == stereotype && stereotype == "response" -> "aggregate"
                else -> null
            }
            styles.append(MermaidLight.styleLine(id(schema.name), tint))
            for (field in schema.fields) for (target in field.targets) {
                val targetSchema = graph.schemas[target] ?: continue
                val label = memberText(field.name)
                links += if (targetSchema.kind == ApiSchemaKind.OBJECT) {
                    "${id(schema.name)} *-- ${if (field.many) "\"*\" " else ""}${id(target)} : $label"
                } else {
                    "${id(schema.name)} --> ${id(target)} : $label"
                }
            }
        }
        return buildString {
            append(MermaidLight.FRONT_MATTER)
            append("classDiagram\n")
            append(classes)
            for (link in links) append("    ").append(link).append('\n')
            append(styles)
        }
    }

    /**
     * Type text safe inside a Mermaid member: `map<string, T>` becomes the generic `map~string, T~`,
     * `string(date-time)` becomes `string/date-time` (parentheses would make a method) and
     * `oneOf: A | B` becomes `oneOf A or B`.
     */
    private fun typeText(field: ApiField): String {
        val generic = field.type.replace("<", "").replace(">", "")
            .replace(Regex("\\(([^)]*)\\)"), "/$1")
            .replace(": ", " ")
            .replace(" | ", " or ")
        return memberText(generic).replace("", "~")
    }
}
