package io.zenwave360.language.docs.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class AnchorsTest {

    @Test
    fun slugsFollowGitHubRules() {
        assertEquals(
            listOf("order-items", "my_type", "a-b", "events-v2"),
            Anchors.slugs(listOf("Order Items", "My_Type", "A-B", "Events (v2)!")),
        )
    }

    @Test
    fun duplicatesGetNumberedSuffixesInHeadingOrder() {
        assertEquals(
            listOf("order", "lifecycle", "order-1", "lifecycle-1", "order-2", "lifecycle-2"),
            Anchors.slugs(listOf("Order", "Lifecycle", "Order", "Lifecycle", "Order", "Lifecycle")),
        )
        // A heading that already reads like a numbered duplicate does not collide with it.
        assertEquals(listOf("a", "a-1", "a-1-1"), Anchors.slugs(listOf("A", "A", "A-1")))
    }

    @Test
    fun linksResolveInTheSecondPassToTheRightHeading() {
        val first = Anchors()
        assertEquals("### Order\n\n", first.heading(3, "Order"))
        first.heading(4, "Order", "type:Order")
        first.heading(3, "Later", "event:Later")
        // First pass: no links yet.
        assertEquals("Order", first.link("type:Order", "Order"))

        val second = first.resolve()
        assertEquals("[Order](#order-1)", second.link("type:Order", "Order"))
        assertEquals("[Later](#later)", second.link("event:Later", "Later"))
        assertEquals("Unknown", second.link("type:Unknown", "Unknown"))
        assertEquals(true, second.has("type:Order"))
        assertEquals(false, second.has("type:Unknown"))
    }
}
