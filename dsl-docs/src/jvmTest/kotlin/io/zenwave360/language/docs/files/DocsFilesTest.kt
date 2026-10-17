package io.zenwave360.language.docs.files

import io.zenwave360.language.docs.DocsLink
import io.zenwave360.language.docs.DocsOutput
import io.zenwave360.language.docs.DslDocs
import io.zenwave360.language.docs.SourceKind
import io.zenwave360.language.docs.internal.GENERATED_MARKER
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocsFilesTest {

    private val fixtures = File("src/commonTest/resources/arcadia-editions")
    private val orders = File(fixtures, "orders-checkout-api")
    private val modulithDir = File("src/commonTest/resources/arcadia-editions-modulith")
    private val temp: File = Files.createTempDirectory("dsl-docs").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private val modulithZdl = listOf(
        "catalog-inventory-domain-model.zdl", "catalog-products-domain-model.zdl", "fulfillment-shipping-domain-model.zdl",
        "notifications-consumer-domain-model.zdl", "orders-checkout-domain-model.zdl", "payments-processing-domain-model.zdl",
    )

    private fun ordersOutput(docs: File, absolute: Boolean = false): DocsOutput {
        fun p(path: String) = if (absolute) File(orders, path).absolutePath else path
        val summary = File(orders, "SUMMARY.md").canonicalFile.relativeTo(docs.canonicalFile).invariantSeparatorsPath
        return DslDocs().generate(
            DocsFiles.input(
                baseDir = orders, outDir = docs,
                zdlFiles = listOf(p("domain-model.zdl")),
                zflFiles = listOf(p("../business-flows/place-order-flow.zfl")),
                apiFiles = listOf(p("openapi.yml"), p("asyncapi.yml"), p("asyncapi-client.yml")),
                links = listOf(DocsLink("Service summary", summary)),
            ),
        )
    }

    private fun modulithOutput(docs: File): DocsOutput =
        DslDocs().generate(DocsFiles.input(modulithDir, docs, zdlFiles = modulithZdl, title = "Arcadia Editions Modulith"))

    private fun generated(body: String) = "$GENERATED_MARKER\n$body"

    @Test
    fun buildsTheArcadiaSnapshotsIntoAFolder() {
        val docs = File(temp, "docs/zenwave")
        val output = ordersOutput(docs)

        val result = DocsFolder(docs).write(output)

        assertEquals(7, result.written.size)
        assertEquals(emptyList(), result.deleted)
        val snapshots = File("src/commonTest/resources/snapshots/orders-checkout")
        assertEquals(markdownFiles(snapshots), markdownFiles(docs))
        val summary = File(orders, "SUMMARY.md").canonicalFile.relativeTo(docs.canonicalFile).invariantSeparatorsPath
        for (name in markdownFiles(docs)) {
            val generated = File(docs, name).readText()
            assertFalse(generated.contains('\r'), "$name has CRLF")
            // The snapshots link the sources from a sibling folder, so only those links differ.
            val expected = File(snapshots, name).readText().replace("\r\n", "\n")
                .replace("(../SUMMARY.md)", "($summary)")
                .replace("(../domain-model.zdl)", "(${summary.removeSuffix("SUMMARY.md")}domain-model.zdl)")
            assertEquals(expected, generated, name)
        }
    }

    @Test
    fun twoWritesAreByteIdentical() {
        val first = File(temp, "first")
        val second = File(temp, "second")
        DocsFolder(first).write(ordersOutput(first))
        DocsFolder(second).write(ordersOutput(second))

        assertTrue(markdownFiles(first).contains("flows/place-order-flow.md"))
        for (name in markdownFiles(first)) {
            assertTrue(File(first, name).readBytes().contentEquals(File(second, name).readBytes()), name)
        }
    }

    @Test
    fun absolutePathsGiveTheSamePagesAsRelativeOnes() {
        val docs = File(temp, "docs")
        assertEquals(ordersOutput(docs), ordersOutput(docs, absolute = true))
    }

    @Test
    fun absolutePathsOutsideTheBaseDirAreNamedRelativeToIt() {
        val docs = File(temp, "docs")
        val input = DocsFiles.input(
            baseDir = File(temp, "project").also { it.mkdirs() }, outDir = docs,
            zdlFiles = listOf(File(orders, "domain-model.zdl").absolutePath),
        )

        assertEquals(1, input.sources.size)
        assertTrue(input.sources.single().path.endsWith("orders-checkout-api/domain-model.zdl"), input.sources.single().path)
        assertEquals("../project/", input.sourcesHref)
    }

    @Test
    fun deletesStaleGeneratedPagesAndKeepsOtherFiles() {
        val docs = File(temp, "docs").also { it.mkdirs() }
        val stale = File(docs, "old-page.md").also { it.writeText("$GENERATED_MARKER\nold") }
        val manual = File(docs, "notes.md").also { it.writeText("# Notes written by hand") }

        val result = DocsFolder(docs).write(ordersOutput(docs))

        assertFalse(stale.exists())
        assertEquals(listOf("old-page.md"), result.deleted)
        assertEquals("# Notes written by hand", manual.readText())
    }

    @Test
    fun refusesToOverwriteAPageWrittenByHand() {
        val docs = File(temp, "docs").also { it.mkdirs() }
        val readme = File(docs, "README.md").also { it.writeText("# My project") }

        val e = assertFailsWith<IllegalArgumentException> { DocsFolder(docs).write(ordersOutput(docs)) }

        assertEquals("# My project", readme.readText())
        assertTrue(e.message!!.contains("Not overwriting files that dsl-docs did not generate: README.md"), e.message)
        assertEquals(listOf("README.md"), docs.list()!!.toList())
    }

    @Test
    fun reportsMissingFilesTogether() {
        val e = assertFailsWith<IllegalArgumentException> {
            DocsFiles.input(temp, temp, zdlFiles = listOf("missing.zdl"), apiFiles = listOf("also-missing.yml"))
        }
        assertTrue(e.message!!.contains("not found: missing.zdl, also-missing.yml"), e.message)
    }

    @Test
    fun classifiesApiFilesByContent() {
        val input = DocsFiles.input(orders, temp, apiFiles = listOf("asyncapi.yml", "openapi.yml", "asyncapi-client.yml"))

        assertEquals(
            listOf(SourceKind.ASYNCAPI to "asyncapi.yml", SourceKind.OPENAPI to "openapi.yml", SourceKind.ASYNCAPI to "asyncapi-client.yml"),
            input.sources.map { it.kind to it.path },
        )
    }

    @Test
    fun rejectsAnApiFileOfUnknownKind() {
        File(temp, "other.yml").writeText("info: { title: Not an API }")

        val e = assertFailsWith<IllegalArgumentException> { DocsFiles.input(temp, temp, apiFiles = listOf("other.yml")) }

        assertTrue(e.message!!.contains("other.yml"), e.message)
    }

    @Test
    fun problemsInTheInputsAreReportedInTheOutputNotThrown() {
        File(temp, "broken.yml").writeText("openapi: [unclosed")
        File(temp, "unknown.yml").writeText("asyncapi: 3.0.0\nopenapi: [unclosed")

        // Broken YAML is not classifiable: it is a caller error here, since the kind cannot be told.
        assertFailsWith<IllegalArgumentException> { DocsFiles.input(temp, temp, apiFiles = listOf("broken.yml")) }
    }

    @Test
    fun buildsTheModulithIntoPagesPerModule() {
        val docs = File(temp, "docs")

        val result = DocsFolder(docs).write(modulithOutput(docs))

        assertEquals(8, result.written.size)
        assertEquals(listOf("README.md", "architecture.md", "modules"), docs.list()!!.sorted())
        assertEquals(
            listOf("catalog-inventory.md", "catalog-products.md", "fulfillment-shipping.md", "notifications-consumer.md", "orders-checkout.md", "payments-processing.md"),
            File(docs, "modules").list()!!.sorted(),
        )
        assertTrue(File(docs, "architecture.md").readText().contains("[`orders-checkout-domain-model.zdl`]("))
    }

    @Test
    fun keepsHandWrittenFilesNextToGeneratedOnesAtAnyDepth() {
        val docs = File(temp, "docs")
        File(docs, "adr").mkdirs()
        val notes = File(docs, "reading-zdl.md").also { it.writeText("# Reading ZDL") }
        val adr = File(docs, "adr/0001.md").also { it.writeText("# ADR 1") }
        val modules = File(docs, "modules").also { it.mkdirs() }
        val moduleNotes = File(modules, "notes.md").also { it.writeText("# By hand") }
        val stale = File(modules, "old-module.md").also { it.writeText(generated("old")) }

        DocsFolder(docs).write(modulithOutput(docs))

        assertEquals("# Reading ZDL", notes.readText())
        assertEquals("# ADR 1", adr.readText())
        assertEquals("# By hand", moduleNotes.readText())
        assertFalse(stale.exists())
        assertTrue(File(modules, "orders-checkout.md").isFile)
    }

    @Test
    fun deletesStaleGeneratedFilesInSubfoldersAndTheEmptyFolder() {
        val docs = File(temp, "docs")
        val old = File(docs, "modules/old.md").also { it.parentFile.mkdirs(); it.writeText(generated("old")) }

        val result = DocsFolder(docs).write(ordersOutput(docs))

        assertFalse(old.exists())
        assertEquals(listOf("modules/old.md"), result.deleted)
        assertFalse(File(docs, "modules").exists())
    }

    @Test
    fun refusesToOverwriteAModulePageWrittenByHandAndWritesNothing() {
        val docs = File(temp, "docs")
        val page = File(docs, "modules/orders-checkout.md").also { it.parentFile.mkdirs(); it.writeText("# Mine") }

        val e = assertFailsWith<IllegalArgumentException> { DocsFolder(docs).write(modulithOutput(docs)) }

        assertEquals("# Mine", page.readText())
        assertTrue(e.message!!.contains("Not overwriting files that dsl-docs did not generate: modules/orders-checkout.md"), e.message)
        assertEquals(listOf("modules"), docs.list()!!.toList())
        assertEquals(listOf("orders-checkout.md"), File(docs, "modules").list()!!.toList())
    }

    @Test
    fun checkPassesWhenTheFolderIsUpToDate() {
        val docs = File(temp, "docs")
        val output = modulithOutput(docs)
        DocsFolder(docs).write(output)

        assertEquals(emptyList(), DocsFolder(docs).check(output))
        // Line endings do not count: a checkout with CRLF is still up to date.
        val page = File(docs, "architecture.md")
        page.writeText(page.readText().replace("\n", "\r\n"))
        assertEquals(emptyList(), DocsFolder(docs).check(output))
    }

    @Test
    fun checkListsTheDifferencesAndWritesNothing() {
        val docs = File(temp, "docs")
        val output = modulithOutput(docs)
        DocsFolder(docs).write(output)
        val changed = File(docs, "architecture.md").also { it.appendText("edited\n") }
        val missing = File(docs, "modules/payments-processing.md").also { it.delete() }
        val stale = File(docs, "modules/gone.md").also { it.writeText(generated("old")) }
        val manual = File(docs, "notes.md").also { it.writeText("# Mine") }
        val before = changed.readText()

        val differences = DocsFolder(docs).check(output)

        assertEquals(
            listOf(
                DocsDifference("architecture.md", "changed"),
                DocsDifference("modules/gone.md", "stale, no longer generated"),
                DocsDifference("modules/payments-processing.md", "missing"),
            ),
            differences,
        )
        assertEquals(before, changed.readText())
        assertFalse(missing.exists())
        assertTrue(stale.exists())
        assertEquals("# Mine", manual.readText())
    }

    @Test
    fun checkOnAnEmptyFolderIsOutOfDateAndCreatesNothing() {
        val docs = File(temp, "docs")
        val output = ordersOutput(docs)

        val differences = DocsFolder(docs).check(output)

        assertEquals(output.files.map { it.path }, differences.map { it.path })
        assertTrue(differences.all { it.reason == "missing" })
        assertFalse(docs.exists())
    }

    @Test
    fun checkStillStopsOnACollisionWithAHandWrittenFile() {
        val docs = File(temp, "docs").also { it.mkdirs() }
        File(docs, "README.md").writeText("# My project")

        assertFailsWith<IllegalArgumentException> { DocsFolder(docs).check(ordersOutput(docs)) }
    }

    @Test
    fun checkNoticesAChangeInAReferencedFile() {
        val project = File(temp, "project").also { it.mkdirs() }
        File(project, "avro").mkdirs()
        File(project, "asyncapi.yml").writeText(
            """
            asyncapi: 3.0.0
            info: { title: Events }
            channels:
              created: { address: orders.created, messages: { Created: { payload: { schemaFormat: application/vnd.apache.avro+json, schema: { ${'$'}ref: avro/Created.avsc } } } } }
            """.trimIndent(),
        )
        val avro = File(project, "avro/Created.avsc").also { it.writeText("""{ "type": "record", "name": "Created", "fields": [ { "name": "id", "type": "string" } ] }""") }
        val docs = File(temp, "docs")
        fun output() = DslDocs().generate(DocsFiles.input(project, docs, apiFiles = listOf("asyncapi.yml")))

        DocsFolder(docs).write(output())
        val page = File(docs, "events.md").readText()
        assertTrue(page.contains("<!-- Source: avro/Created.avsc sha256:"), page)
        assertTrue(page.contains("| `id` | `string` |"), page)
        assertEquals(emptyList(), DocsFolder(docs).check(output()))

        avro.writeText("""{ "type": "record", "name": "Created", "fields": [ { "name": "id", "type": "string" }, { "name": "extra", "type": "int" } ] }""")
        assertEquals(listOf(DocsDifference("events.md", "changed")), DocsFolder(docs).check(output()))
    }

    /** Relative paths of the generated pages, subfolders included. */
    private fun markdownFiles(dir: File): List<String> =
        dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.sorted().toList()
}
