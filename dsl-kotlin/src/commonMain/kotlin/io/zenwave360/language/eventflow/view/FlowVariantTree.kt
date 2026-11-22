package io.zenwave360.language.eventflow.view

/**
 * A line of the variant tree (spec:flows/eventflow#ef-r25). [steps] are the steps this line adds to its parent, a run
 * of steps with no branch being one line; [path] is every step from the start. A node with no children and one variant
 * is that variant, and its [id] is the variant's id. Any other node has an id of its own, `branch:<outcome>:<n.n>`,
 * and stands for all of [variantIds] together.
 */
data class FlowVariantNode(
    val id: String,
    val steps: List<String>,
    val path: List<String>,
    /** The title of [path], as a variant's title is written. */
    val title: String,
    /** Every variant under this line: those that end here, then those of its children. */
    val variantIds: List<String>,
    val children: List<FlowVariantNode>
) {
    val isLeaf: Boolean get() = children.isEmpty() && variantIds.size == 1
}

/** The variants that end in [outcome], as a prefix tree of their steps. */
data class FlowVariantGroup(val outcome: String, val nodes: List<FlowVariantNode>)

/**
 * Groups [variants] by end outcome, in the order the outcomes first appear (the flow's `end` order), and builds
 * inside each group a prefix tree of the variants' [FlowVariant.steps]. It reads the steps, never the titles.
 */
fun flowVariantTree(variants: List<FlowVariant>): List<FlowVariantGroup> =
    variants.groupBy { it.outcome }.map { (outcome, ofOutcome) ->
        val root = TrieNode(emptyList())
        ofOutcome.forEach { variant ->
            var node = root
            variant.steps.forEach { step -> node = node.children.getOrPut(step) { TrieNode(listOf(step)) } }
            node.own += variant
        }
        FlowVariantGroup(outcome, root.children.values.mapIndexed { index, child -> child.toNode(outcome, emptyList(), "$index") })
    }

/** The tree line [id] names, or null: a leaf is named by its variant's id. */
fun flowVariantNode(variants: List<FlowVariant>, id: String): FlowVariantNode? {
    fun find(nodes: List<FlowVariantNode>): FlowVariantNode? =
        nodes.firstNotNullOfOrNull { if (it.id == id) it else find(it.children) }
    return flowVariantTree(variants).firstNotNullOfOrNull { find(it.nodes) }
}

/** The ids of the variants a variant id or a tree line id stands for, in the flow's order, or null when it is neither. */
fun flowVariantSelection(variants: List<FlowVariant>, id: String): List<String>? {
    val ids = flowVariantNode(variants, id)?.variantIds?.toSet() ?: return null
    return variants.filter { it.id in ids }.map { it.id }
}

private class TrieNode(var steps: List<String>) {
    val children = LinkedHashMap<String, TrieNode>()
    val own = mutableListOf<FlowVariant>()

    /** A run with no branch and no end of its own is one line: it is merged into its only child. */
    private fun collapse(): TrieNode {
        while (own.isEmpty() && children.size == 1) {
            val only = children.values.first()
            steps = steps + only.steps
            own += only.own
            children.clear()
            children.putAll(only.children)
        }
        return this
    }

    fun toNode(outcome: String, parentPath: List<String>, index: String): FlowVariantNode {
        collapse()
        val path = parentPath + steps
        val kids = children.values.mapIndexed { i, child -> child.toNode(outcome, path, "$index.$i") }
        val ids = own.map { it.id } + kids.flatMap { it.variantIds }
        val leaf = kids.isEmpty() && own.size == 1
        return FlowVariantNode(
            id = if (leaf) own.first().id else "branch:$outcome:$index",
            steps = steps,
            path = path,
            title = variantTitle(path, outcome),
            variantIds = ids,
            children = kids
        )
    }
}
