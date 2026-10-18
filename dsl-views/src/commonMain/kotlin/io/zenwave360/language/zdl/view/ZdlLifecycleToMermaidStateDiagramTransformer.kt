package io.zenwave360.language.zdl.view

import io.zenwave360.language.zdl.ZdlModel

/**
 * The lifecycle of an aggregate as a Mermaid `stateDiagram-v2`.
 *
 * The aggregate must declare `@lifecycle(field, initial)` on its root entity (or aggregate) and at
 * least one method with `@transition(from?, to)` in the services `for` it, or in its aggregate
 * block. Rules:
 * - `from` and `to` may each be one value or a list: one edge per (from, to) pair.
 * - No `from` is a creation: `[*] --> to`. If no creation targets the `initial` state, `[*] --> initial` is added.
 * - The same (from, to) pair from several methods is one edge, labels joined with `, `. Self-loops are kept.
 * - A state with no outgoing edge is terminal: `state --> [*]`.
 * - The edge label is the method name.
 */
class ZdlLifecycleToMermaidStateDiagramTransformer {

    /** One entry per aggregate that has a lifecycle with transitions. */
    fun transform(model: ZdlModel): List<AggregateLifecycle> = transform(ZdlDomain(model))

    fun transform(domain: ZdlDomain): List<AggregateLifecycle> = domain.aggregates.mapNotNull { transform(domain, it) }

    /** Null when the aggregate has no lifecycle or no transition. */
    fun transform(domain: ZdlDomain, aggregate: ZdlAggregate): AggregateLifecycle? {
        val lifecycle = aggregate.lifecycle ?: return null
        val methods = domain.services
            .filter { s -> s.aggregates.any { it == aggregate.name || it == aggregate.root } }
            .flatMap { it.methods } + aggregate.commands
        val transitions = methods.mapNotNull { m -> m.transition?.let { m.name to it } }
        if (transitions.isEmpty()) return null

        // (from or null for creation) -> to, with the labels of every method that makes that move
        val edges = linkedMapOf<Pair<String?, String>, MutableList<String>>()
        for ((method, t) in transitions) {
            for (from in t.from.ifEmpty { listOf(null) }) {
                for (to in t.to) {
                    val labels = edges.getOrPut(from to to) { mutableListOf() }
                    if (method !in labels) labels += method
                }
            }
        }
        val initial = lifecycle.initial
        val states = linkedSetOf<String>()
        for ((pair, _) in edges) { pair.first?.let { states += it }; states += pair.second }
        if (initial != null) states += initial
        val outgoing = edges.keys.mapNotNull { it.first }.toSet()

        val mermaid = buildString {
            append(MermaidLight.FRONT_MATTER)
            append("stateDiagram-v2\n")
            for (s in states) {
                if (stateId(s) != s) append("    state \"").append(s.replace("\"", "")).append("\" as ").append(stateId(s)).append('\n')
            }
            if (initial != null && edges.keys.none { it.first == null && it.second == initial }) {
                append("    [*] --> ").append(stateId(initial)).append('\n')
            }
            for ((pair, labels) in edges) {
                append("    ").append(pair.first?.let { stateId(it) } ?: "[*]").append(" --> ").append(stateId(pair.second))
                    .append(" : ").append(labels.joinToString(", ")).append('\n')
            }
            for (s in states) if (s !in outgoing) append("    ").append(stateId(s)).append(" --> [*]\n")
        }
        return AggregateLifecycle(aggregate.name, aggregate.root, lifecycle.field, initial, mermaid)
    }

    private fun stateId(state: String): String {
        val id = state.replace(Regex("[^A-Za-z0-9_]"), "_").ifEmpty { "_" }
        return if (id.lowercase() in RESERVED) id + "_" else id
    }

    private companion object {
        val RESERVED = setOf("state", "end", "note", "direction", "class", "classdef", "style", "click", "default", "as")
    }
}

/** The state diagram of one aggregate with the field that holds the state. */
data class AggregateLifecycle(
    val aggregate: String,
    val root: String,
    val field: String,
    val initial: String?,
    val mermaid: String,
) {
    /** "Field `status`, initial `CREATED`." (the initial part is left out when not declared). */
    val caption: String
        get() = "Field `${this.field}`" + (initial?.let { ", initial `$it`" } ?: "") + "."
}
