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
        FlowVariantGroup(outcome, root.children.values.map { child -> child.toNode(outcome, emptyList()) })
    }

/** The tree line [id] names, or null: a leaf is named by its variant's id. */
fun flowVariantNode(variants: List<FlowVariant>, id: String): FlowVariantNode? {
    fun find(nodes: List<FlowVariantNode>): FlowVariantNode? =
        nodes.firstNotNullOfOrNull { if (it.id == id) it else find(it.children) }
    return flowVariantTree(variants).firstNotNullOfOrNull { find(it.nodes) }
}

/**
 * The ids of the variants a variant id or a tree line id stands for, in the flow's order, or null when it is neither.
 * Every variant id selects that variant, wherever the tree puts it.
 */
fun flowVariantSelection(variants: List<FlowVariant>, id: String): List<String>? {
    if (variants.any { it.id == id }) return listOf(id)
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

    fun toNode(outcome: String, parentPath: List<String>): FlowVariantNode {
        collapse()
        val path = parentPath + steps
        val branches = children.values.map { child -> child.toNode(outcome, path) }
        val leaf = branches.isEmpty() && own.size == 1
        // A variant that ends at a line with other variants under it, or shares its steps with another, is a leaf of its own
        // that adds no steps, so every variant id stays selectable from the tree.
        val ends = if (leaf) emptyList() else own.map { variant ->
            FlowVariantNode(variant.id, emptyList(), path, variant.title, listOf(variant.id), emptyList())
        }
        val kids = ends + branches
        return FlowVariantNode(
            id = if (leaf) own.first().id else branchId(outcome, path),
            steps = steps,
            path = path,
            title = variantTitle(path, outcome),
            variantIds = if (leaf) own.map { it.id } else kids.flatMap { it.variantIds },
            children = kids
        )
    }
}

/**
 * `branch:<outcome>:<hash of the steps down to the line>`: it names what the line is, not where it sits among its
 * siblings, so an edit that inserts or reorders variants never makes a kept id select another line.
 */
private fun branchId(outcome: String, path: List<String>): String {
    var hash = -3750763034362895579L // FNV-1a, 64 bit
    for (char in path.joinToString("\u001f")) {
        hash = (hash xor char.code.toLong()) * 1099511628211L
    }
    return "branch:$outcome:${hash.toULong().toString(16)}"
}
