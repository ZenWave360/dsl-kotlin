package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.ZdlModel
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.classBlock
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.id
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.memberText

/**
 * The domain events of a ZDL as one Mermaid class diagram, in declaration order. Events belong to the
 * ZDL, not to a service, so they are not grouped.
 *
 * Each event is a `<<event>>` class with its fields (`@copy` resolved, see [ZdlDomain.fieldsOf]); types
 * declared inline inside an event are `<<embedded>>` classes joined by a composition arrow (`*--`, with
 * `"*"` for arrays) labeled with the field name. Light styling as in [MermaidLight].
 */
class ZdlEventToMermaidClassDiagramTransformer {

    /** Null when the ZDL declares no events. */
    fun transform(model: ZdlModel): String? = transform(ZdlDomain(model))

    fun transform(domain: ZdlDomain): String? =
        domain.events.map { it.name }.takeIf { it.isNotEmpty() }?.let { render(domain, it) }

    private fun render(domain: ZdlDomain, events: List<String>): String {
        val classes = StringBuilder()
        val styles = StringBuilder()
        val links = LinkedHashSet<String>()
        val drawn = mutableSetOf<String>()

        fun draw(name: String, stereotype: String, nested: Set<String>) {
            if (!drawn.add(name)) return
            val fields = domain.fieldsOf(name)
            classes.append(classBlock(name, stereotype, fields.map { f -> memberText(f.typeText + " " + f.name) }))
            styles.append(MermaidLight.styleLine(id(name), stereotype))
            for (f in fields) {
                if (f.type !in nested || f.type == name) continue
                val cardinality = if (f.isArray) "\"*\" " else ""
                links += "${id(name)} *-- $cardinality${id(f.type)} : ${memberText(f.name)}"
            }
        }

        for (event in events) {
            val nested = domain.nestedTypesOf(event)
            draw(event, "event", nested.toSet())
            for (n in nested) draw(n, "embedded", nested.toSet())
        }
        return buildString {
            append(MermaidLight.FRONT_MATTER)
            append("classDiagram\n")
            append(classes)
            for (link in links) append("    ").append(link).append('\n')
            append(styles)
        }
    }
}
