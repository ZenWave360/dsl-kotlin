package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.ZdlModel
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.classBlock
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.id
import io.zenwave360.language.zdl.view.ZdlToMermaidClassDiagramTransformer.Companion.memberText

/**
 * One Mermaid `classDiagram` per aggregate root, plus one for the entities no aggregate reaches.
 *
 * An aggregate diagram holds the root (`<<aggregate>>`) and everything reachable from it through
 * fields of entity or enum type and relationships: entities (`<<entity>>`, `<<embedded>>`) with
 * their fields and enums with their values. Another aggregate root met on the way is drawn as an
 * empty `<<aggregate>>` class and never descended into. No methods, inputs, outputs, events or
 * services: those belong to other sections. Fields honor `@copy` (see [ZdlDomain.fieldsOf]).
 *
 * Arrows follow [ZdlToMermaidClassDiagramTransformer]: composition `*--` to embedded types,
 * `-->` to other entities and enums (labeled with the field name), and relationships with their
 * cardinality.
 */
class ZdlAggregateToMermaidClassDiagramTransformer {

    fun transform(model: ZdlModel): AggregateClassDiagrams = transform(ZdlDomain(model))

    fun transform(domain: ZdlDomain): AggregateClassDiagrams {
        val diagrams = domain.aggregates.filter { domain.kindOf(it.root) == ZdlTypeKind.ENTITY }.map { aggregate ->
            val reachable = domain.reachableFrom(aggregate.root)
            val full = listOf(aggregate.root) + reachable.entities + reachable.enums
            AggregateClassDiagram(aggregate.name, aggregate.root, render(domain, full, reachable.otherRoots.toSet(), aggregate.root))
        }
        val others = domain.unreachableTypes()
        val othersDiagram = if (others.isEmpty()) null else render(domain, others, domain.aggregateRoots, null)
        return AggregateClassDiagrams(diagrams, othersDiagram)
    }

    /**
     * [full] are drawn with their members; [stubCandidates] (aggregate roots) are drawn empty when
     * an arrow reaches them. [root] gets the `<<aggregate>>` stereotype.
     */
    private fun render(domain: ZdlDomain, full: List<String>, stubCandidates: Set<String>, root: String?): String {
        val fullSet = full.toSet()
        val links = LinkedHashSet<String>()
        val stubs = linkedSetOf<String>()

        fun reaches(target: String) = target in fullSet || target in stubCandidates

        for (name in full) {
            if (domain.kindOf(name) != ZdlTypeKind.ENTITY) continue
            for (field in domain.fieldsOf(name)) {
                val target = field.type
                val kind = domain.kindOf(target)
                if (target == name || (kind != ZdlTypeKind.ENTITY && kind != ZdlTypeKind.ENUM) || !reaches(target)) continue
                if (target !in fullSet) stubs += target
                val arrow = if (domain.types.getValue(target).embedded) "*--" else "-->"
                val cardinality = if (field.isArray) "\"*\" " else ""
                links += "${id(name)} $arrow $cardinality${id(target)} : ${memberText(field.name)}"
            }
        }
        for (r in domain.relationships) {
            if (!reaches(r.from) || !reaches(r.to) || (r.from !in fullSet && r.to !in fullSet)) continue
            if (domain.kindOf(r.from) != ZdlTypeKind.ENTITY || domain.kindOf(r.to) != ZdlTypeKind.ENTITY) continue
            for (n in listOf(r.from, r.to)) if (n !in fullSet) stubs += n
            val arrow = when {
                r.fromField != null && r.toField == null -> "-->"
                r.fromField == null && r.toField != null -> "<--"
                else -> "--"
            }
            val label = listOfNotNull(r.fromField, r.toField).joinToString(" / ") { memberText(it) }
            val suffix = if (label.isBlank()) "" else " : $label"
            links += "${id(r.from)} \"${r.fromCardinality}\" $arrow \"${r.toCardinality}\" ${id(r.to)}$suffix"
        }

        return buildString {
            append("classDiagram\n")
            for (name in full) {
                val members = when (domain.kindOf(name)) {
                    ZdlTypeKind.ENUM -> domain.enumValues(name).map { v ->
                        memberText(if (v.value == null) v.name else "${v.name} = ${v.value}")
                    }
                    else -> domain.fieldsOf(name).map { f -> memberText(f.type + (if (f.isArray) "[]" else "") + " " + f.name) }
                }
                val type = domain.types.getValue(name)
                val stereotype = when {
                    type.kind == ZdlTypeKind.ENUM -> "enumeration"
                    name == root -> "aggregate"
                    type.embedded -> "embedded"
                    else -> "entity"
                }
                append(classBlock(name, stereotype, members))
            }
            for (name in stubs) append(classBlock(name, "aggregate", emptyList()))
            for (link in links) append("    ").append(link).append('\n')
        }
    }
}

/** The class diagram of one aggregate: [aggregate] is the aggregate (or root) name, [root] the root entity. */
data class AggregateClassDiagram(val aggregate: String, val root: String, val mermaid: String)

/** [others] is the diagram of entities and enums no aggregate reaches, null when there are none. */
data class AggregateClassDiagrams(val aggregates: List<AggregateClassDiagram>, val others: String?)
