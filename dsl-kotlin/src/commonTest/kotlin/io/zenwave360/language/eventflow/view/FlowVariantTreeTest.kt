package io.zenwave360.language.eventflow.view

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowVariantTreeTest {
    private fun variant(index: Int, outcome: String, vararg steps: String) =
        FlowVariant("sequence:$outcome:$index", variantTitle(steps.toList(), outcome), outcome, steps.toList())

    private val variants = listOf(
        variant(0, "completed", "Start", "reserved", "authorized", "shipped"),
        variant(1, "completed", "Start", "reserved", "authorized", "pickup"),
        variant(2, "completed", "Start", "reserved", "retried"),
        variant(3, "failed", "Start", "reserved", "declined"),
        variant(4, "failed", "Start", "out-of-stock")
    )

    @Test
    fun groupsByOutcomeInTheOrderOfTheVariants() {
        assertEquals(listOf("completed", "failed"), flowVariantTree(variants).map { it.outcome })
    }

    @Test
    fun aRunWithNoBranchIsOneLineAndLinesShowOnlyWhatTheyAdd() {
        val completed = flowVariantTree(variants).first().nodes.single()
        assertEquals(listOf("Start", "reserved"), completed.steps)
        assertEquals(listOf("authorized", "retried").map { listOf(it) }, completed.children.map { it.steps })
        val authorized = completed.children.first()
        assertEquals(listOf(listOf("shipped"), listOf("pickup")), authorized.children.map { it.steps })
        assertEquals(listOf("Start", "reserved", "authorized", "shipped"), authorized.children.first().path)
    }

    @Test
    fun aLeafIsItsVariantAndALineWithChildrenIsABranch() {
        val completed = flowVariantTree(variants).first().nodes.single()
        val authorized = completed.children.first()
        assertTrue(authorized.children.all { it.isLeaf })
        assertEquals(listOf("sequence:completed:0", "sequence:completed:1"), authorized.children.map { it.id })
        assertTrue(authorized.id.startsWith("branch:completed:") && completed.id.startsWith("branch:completed:") && authorized.id != completed.id)
        assertEquals((0..2).map { "sequence:completed:$it" }, completed.variantIds)
        assertEquals("from Start → reserved → authorized to completed", authorized.title)
    }

    @Test
    fun aGroupWithOneVariantIsOneLeafLine() {
        val node = flowVariantTree(listOf(variant(0, "done", "Start", "a", "b"))).single().nodes.single()
        assertTrue(node.isLeaf)
        assertEquals(listOf("Start", "a", "b"), node.steps)
        assertEquals("sequence:done:0", node.id)
    }

    @Test
    fun differentStartsAreSeparateRoots() {
        val nodes = flowVariantTree(listOf(variant(0, "done", "A", "x"), variant(1, "done", "B", "y"))).single().nodes
        assertEquals(listOf("A", "x"), nodes[0].steps)
        assertEquals(listOf("B", "y"), nodes[1].steps)
    }

    @Test
    fun aVariantThatEndsBeforeAnotherOneIsALeafOfItsLineSoItStaysSelectable() {
        val all = listOf(variant(0, "done", "A", "x"), variant(1, "done", "A", "x", "y"))
        val node = flowVariantTree(all).single().nodes.single()
        assertEquals(listOf("A", "x"), node.steps)
        assertEquals(listOf("sequence:done:0", "sequence:done:1"), node.variantIds)
        assertTrue(node.id.startsWith("branch:done:"))
        val ends = node.children.first()
        assertTrue(ends.isLeaf)
        assertEquals("sequence:done:0", ends.id)
        assertEquals(emptyList(), ends.steps)
        assertEquals(listOf("sequence:done:0"), flowVariantSelection(all, "sequence:done:0"))
    }

    @Test
    fun variantsWithIdenticalStepsAreEachALeafUnderOneLine() {
        val all = listOf(variant(0, "done", "A", "x"), variant(1, "done", "A", "x"))
        val node = flowVariantTree(all).single().nodes.single()
        assertEquals(listOf("sequence:done:0", "sequence:done:1"), node.children.map { it.id })
        assertTrue(node.children.all { it.isLeaf })
        assertEquals(listOf("sequence:done:1"), flowVariantSelection(all, "sequence:done:1"))
        assertEquals(listOf("sequence:done:0", "sequence:done:1"), flowVariantSelection(all, node.id))
    }

    @Test
    fun everyVariantIdIsAnIdInTheTree() {
        val all = variants + variant(5, "completed", "Start", "reserved") + variant(6, "completed", "Start", "reserved")
        fun ids(nodes: List<FlowVariantNode>): List<String> = nodes.flatMap { listOf(it.id) + ids(it.children) }
        val inTree = flowVariantTree(all).flatMap { ids(it.nodes) }.toSet()
        assertTrue(all.all { it.id in inTree })
    }

    @Test
    fun aBranchIdNamesItsStepsNotItsPlaceAmongItsSiblings() {
        val before = flowVariantTree(variants).first().nodes.single().children.first { it.steps == listOf("authorized") }
        val inserted = listOf(variant(9, "completed", "Start", "reserved", "aaa-new")) + variants
        val after = flowVariantTree(inserted).first().nodes.single().children.first { it.steps == listOf("authorized") }
        assertEquals(before.id, after.id)
        assertEquals(before.variantIds, after.variantIds)
        assertEquals(flowVariantTree(variants).first().nodes.single().id, flowVariantTree(inserted).first().nodes.single().id)
        val reordered = flowVariantTree(variants.reversed()).first { it.outcome == "completed" }.nodes.single().children.first { it.steps == listOf("authorized") }
        assertEquals(before.id, reordered.id)
    }

    @Test
    fun selectionOfAVariantAnInnerLineAndAnUnknownId() {
        assertEquals(listOf("sequence:failed:3"), flowVariantSelection(variants, "sequence:failed:3"))
        val authorized = flowVariantTree(variants).first().nodes.single().children.first { it.steps == listOf("authorized") }
        assertEquals(listOf("sequence:completed:0", "sequence:completed:1"), flowVariantSelection(variants, authorized.id))
        val failed = flowVariantTree(variants).last().nodes.single()
        assertEquals(listOf(3, 4).map { "sequence:failed:$it" }, flowVariantSelection(variants, failed.id))
        assertNull(flowVariantSelection(variants, "branch:completed:nope"))
    }

    @Test
    fun noVariantsNoGroups() {
        assertEquals(emptyList(), flowVariantTree(emptyList()))
    }
}
