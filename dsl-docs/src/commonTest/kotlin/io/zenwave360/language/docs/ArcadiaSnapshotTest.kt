package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Compares the documentation of the Arcadia Editions orders-checkout service with the files under
 * src/commonTest/resources/snapshots/orders-checkout. After an intended change, regenerate them with
 * `DSL_DOCS_UPDATE_SNAPSHOTS=true ./gradlew :dsl-docs:jvmTest` and review the diff.
 */
class ArcadiaSnapshotTest {

    @Test
    fun matchesTheSnapshots() {
        val output = DslDocs().generate(DocsInput(arcadiaOrdersCheckout(), links = listOf(DocsLink("Service summary", "../SUMMARY.md")), sourcesHref = "../", fileReader = arcadiaFileReader))

        assertEquals(emptyList(), output.problems)
        assertEquals(listOf("README.md", "apis.md", "architecture.md", "domain.md", "events.md", "flows.md", "flows/place-order-flow.md"), output.files.map { it.path })
        for (file in output.files) {
            val snapshot = "snapshots/orders-checkout/${file.path}"
            if (updateSnapshot(snapshot, file.content)) continue
            val expected = try {
                readTestFile(snapshot)
            } catch (e: Throwable) {
                fail("Missing snapshot $snapshot. Run the JVM tests with DSL_DOCS_UPDATE_SNAPSHOTS=true to create it.")
            }
            assertEquals(expected, file.content, "Generated ${file.path} differs from $snapshot")
        }
    }

    @Test
    fun modulithMatchesTheSnapshots() {
        val output = DslDocs().generate(DocsInput(arcadiaModulith(), title = "Arcadia Editions Modulith", sourcesHref = "../"))

        assertEquals(emptyList(), output.problems)
        assertEquals(
            listOf("README.md", "architecture.md") + arcadiaModulithFiles.map { "modules/" + it.removeSuffix("-domain-model.zdl") + ".md" },
            output.files.map { it.path },
        )
        for (file in output.files) {
            val snapshot = "snapshots/arcadia-modulith/${file.path}"
            if (updateSnapshot(snapshot, file.content)) continue
            val expected = try {
                readTestFile(snapshot)
            } catch (e: Throwable) {
                fail("Missing snapshot $snapshot. Run the JVM tests with DSL_DOCS_UPDATE_SNAPSHOTS=true to create it.")
            }
            assertEquals(expected, file.content, "Generated ${file.path} differs from $snapshot")
        }
    }

    /** The flow pages of the modulith run link their systems to the module pages. */
    @Test
    fun modulithFlowsMatchTheSnapshots() {
        val zfl = DocsSource(SourceKind.ZFL, "../business-flows/place-order-flow.zfl", readTestFile("arcadia-editions/business-flows/place-order-flow.zfl"))
        val output = DslDocs().generate(DocsInput(arcadiaModulith() + zfl, title = "Arcadia Editions Modulith", sourcesHref = "../"))

        assertEquals(emptyList(), output.problems)
        val flows = output.files.filter { it.path == "flows.md" || it.path.startsWith("flows/") }
        assertEquals(listOf("flows.md", "flows/place-order-flow.md"), flows.map { it.path })
        val page = flows.last().content
        assertTrue(page.contains("| `OrdersCheckout` | `OrdersCheckoutService` | `startOrderCheckout`, `confirmOrder`, `cancelOrder` | [Orders Checkout](../modules/orders-checkout.md) |"))
        assertTrue(page.contains("](../modules/payments-processing.md) |"))
        for (file in flows) {
            val snapshot = "snapshots/arcadia-modulith-zfl/${file.path}"
            if (updateSnapshot(snapshot, file.content)) continue
            val expected = try {
                readTestFile(snapshot)
            } catch (e: Throwable) {
                fail("Missing snapshot $snapshot. Run the JVM tests with DSL_DOCS_UPDATE_SNAPSHOTS=true to create it.")
            }
            assertEquals(expected, file.content, "Generated ${file.path} differs from $snapshot")
        }
    }
}
