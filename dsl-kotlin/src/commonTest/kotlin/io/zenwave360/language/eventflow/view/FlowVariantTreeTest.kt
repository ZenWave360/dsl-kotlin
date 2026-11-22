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
        assertEquals("branch:completed:0.0", authorized.id)
        assertEquals("branch:completed:0", completed.id)
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
    fun aVariantThatEndsBeforeAnotherOneKeepsTheLineAsABranchOfBoth() {
        val node = flowVariantTree(listOf(variant(0, "done", "A", "x"), variant(1, "done", "A", "x", "y"))).single().nodes.single()
        assertEquals(listOf("A", "x"), node.steps)
        assertEquals(listOf("sequence:done:0", "sequence:done:1"), node.variantIds)
        assertEquals("branch:done:0", node.id)
    }

    @Test
    fun selectionOfAVariantAnInnerLineAndAnUnknownId() {
        assertEquals(listOf("sequence:failed:3"), flowVariantSelection(variants, "sequence:failed:3"))
        assertEquals(listOf("sequence:completed:0", "sequence:completed:1"), flowVariantSelection(variants, "branch:completed:0.0"))
        assertEquals(listOf(3, 4).map { "sequence:failed:$it" }, flowVariantSelection(variants, "branch:failed:0"))
        assertNull(flowVariantSelection(variants, "branch:completed:9"))
    }

    @Test
    fun noVariantsNoGroups() {
        assertEquals(emptyList(), flowVariantTree(emptyList()))
    }
}
