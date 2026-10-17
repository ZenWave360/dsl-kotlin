package io.zenwave360.language.docs.cli

import io.zenwave360.language.docs.internal.GENERATED_MARKER
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DslDocsCliTest {

    private val fixtures = File("src/commonTest/resources/arcadia-editions")
    private val config = File(fixtures, "orders-checkout-api/dsl-docs.yml")
    private val temp: File = Files.createTempDirectory("dsl-docs").toFile()
    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()
    private val cli = DslDocsCli(PrintStream(out), PrintStream(err))

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun run(vararg args: String) = cli.run(arrayOf(*args))

    @Test
    fun buildsTheArcadiaSnapshotsIntoAFolder() {
        val docs = File(temp, "docs/zenwave")

        assertEquals(0, run("build", "--config", config.path, "--out", docs.path), err.toString())

        val snapshots = File("src/commonTest/resources/snapshots/orders-checkout")
        assertEquals(snapshots.list()!!.sorted(), docs.list()!!.sorted())
        for (file in docs.listFiles()!!) {
            val generated = file.readText()
            assertFalse(generated.contains('\r'), "${file.name} has CRLF")
            // The CLI links the hand-written page relative to the output folder, so only those links differ.
            val expected = File(snapshots, file.name).readText().replace("\r\n", "\n")
            assertEquals(expected.replace("(../SUMMARY.md)", "(${relativeSummary(docs)})").replace("(../domain-model.zdl)", "(${relativeSummary(docs).removeSuffix("SUMMARY.md")}domain-model.zdl)"), generated, file.name)
        }
        assertTrue(out.toString().startsWith("Wrote 6 files to"))
    }

    @Test
    fun twoBuildsAreByteIdentical() {
        val first = File(temp, "first")
        val second = File(temp, "second")
        run("build", "--config", config.path, "--out", first.path)
        run("build", "--config", config.path, "--out", second.path)

        for (name in first.list()!!) {
            assertTrue(File(first, name).readBytes().contentEquals(File(second, name).readBytes()), name)
        }
    }

    @Test
    fun deletesStaleGeneratedPagesAndKeepsOtherFiles() {
        val docs = File(temp, "docs").also { it.mkdirs() }
        val stale = File(docs, "old-page.md").also { it.writeText("$GENERATED_MARKER\nold") }
        val manual = File(docs, "notes.md").also { it.writeText("# Notes written by hand") }

        assertEquals(0, run("build", "--config", config.path, "--out", docs.path))

        assertFalse(stale.exists())
        assertEquals("# Notes written by hand", manual.readText())
    }

    @Test
    fun refusesToOverwriteAPageWrittenByHand() {
        val docs = File(temp, "docs").also { it.mkdirs() }
        val readme = File(docs, "README.md").also { it.writeText("# My project") }

        assertEquals(1, run("build", "--config", config.path, "--out", docs.path))

        assertEquals("# My project", readme.readText())
        assertTrue(err.toString().contains("Not overwriting files that dsl-docs did not generate: README.md"), err.toString())
        assertEquals(listOf("README.md"), docs.list()!!.toList())
    }

    @Test
    fun reportsMissingFilesAndConfigErrors() {
        val missing = File(temp, "dsl-docs.yml").also { it.writeText("zdl: [missing.zdl]\nopenapi: [also-missing.yml]") }
        assertEquals(1, run("build", "--config", missing.path, "--out", temp.path))
        assertTrue(err.toString().contains("not found: missing.zdl, also-missing.yml"), err.toString())

        val invalid = File(temp, "invalid.yml").also { it.writeText("zdls: []") }
        assertEquals(1, run("build", "--config", invalid.path, "--out", temp.path))
        assertTrue(err.toString().contains("ERROR Unknown config keys: zdls."), err.toString())

        assertEquals(1, run("build", "--config", File(temp, "nope.yml").path, "--out", temp.path))
        assertTrue(err.toString().contains("Config file not found"), err.toString())
    }

    @Test
    fun printsProblemsAsWarnings() {
        File(temp, "broken.yml").writeText("openapi: [unclosed")
        val config = File(temp, "dsl-docs.yml").also { it.writeText("openapi: [broken.yml]") }

        assertEquals(0, run("build", "--config", config.path, "--out", File(temp, "docs").path))
        assertTrue(err.toString().startsWith("WARNING broken.yml: cannot read the file as YAML"), err.toString())
    }

    @Test
    fun wrongArguments() {
        assertEquals(2, run())
        assertEquals(2, run("serve"))
        assertEquals(2, run("build", "--config", config.path))
        assertEquals(2, run("build", "--config", config.path, "--out", temp.path, "--watch"))
        assertTrue(err.toString().contains("Usage: dsl-docs build --config <dsl-docs.yml> --out <dir> [--check]"))
    }

    private val modulith = File(fixtures.parentFile, "arcadia-editions-modulith/dsl-docs.yml")

    private fun generated(body: String) = "$GENERATED_MARKER\n$body"

    @Test
    fun buildsTheModulithIntoPagesPerModule() {
        val docs = File(temp, "docs")

        assertEquals(0, run("build", "--config", modulith.path, "--out", docs.path), err.toString())

        assertTrue(out.toString().startsWith("Wrote 8 files to"), out.toString())
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

        assertEquals(0, run("build", "--config", modulith.path, "--out", docs.path), err.toString())

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

        assertEquals(0, run("build", "--config", config.path, "--out", docs.path), err.toString())

        assertFalse(old.exists())
        assertFalse(File(docs, "modules").exists())
    }

    @Test
    fun refusesToOverwriteAModulePageWrittenByHandAndWritesNothing() {
        val docs = File(temp, "docs")
        val page = File(docs, "modules/orders-checkout.md").also { it.parentFile.mkdirs(); it.writeText("# Mine") }

        assertEquals(1, run("build", "--config", modulith.path, "--out", docs.path))

        assertEquals("# Mine", page.readText())
        assertTrue(err.toString().contains("Not overwriting files that dsl-docs did not generate: modules/orders-checkout.md"), err.toString())
        assertEquals(listOf("modules"), docs.list()!!.toList())
        assertEquals(listOf("orders-checkout.md"), File(docs, "modules").list()!!.toList())
    }

    @Test
    fun checkPassesWhenTheFolderIsUpToDate() {
        val docs = File(temp, "docs")
        run("build", "--config", modulith.path, "--out", docs.path)
        out.reset()

        assertEquals(0, run("build", "--config", modulith.path, "--out", docs.path, "--check"), err.toString())

        assertTrue(out.toString().startsWith("Up to date: 8 files"), out.toString())
        // Line endings do not count: a checkout with CRLF is still up to date.
        val page = File(docs, "architecture.md")
        page.writeText(page.readText().replace("\n", "\r\n"))
        assertEquals(0, run("build", "--config", modulith.path, "--out", docs.path, "--check"), err.toString())
    }

    @Test
    fun checkFailsWithExit3ListsTheDifferencesAndWritesNothing() {
        val docs = File(temp, "docs")
        run("build", "--config", modulith.path, "--out", docs.path)
        val changed = File(docs, "architecture.md").also { it.appendText("edited\n") }
        val missing = File(docs, "modules/payments-processing.md").also { it.delete() }
        val stale = File(docs, "modules/gone.md").also { it.writeText(generated("old")) }
        val manual = File(docs, "notes.md").also { it.writeText("# Mine") }
        val before = changed.readText()
        err.reset()

        assertEquals(3, run("build", "--config", modulith.path, "--out", docs.path, "--check"))

        val message = err.toString()
        assertTrue(message.contains("OUT OF DATE architecture.md (changed)"), message)
        assertTrue(message.contains("OUT OF DATE modules/payments-processing.md (missing)"), message)
        assertTrue(message.contains("OUT OF DATE modules/gone.md (stale"), message)
        assertFalse(message.contains("notes.md"), message)
        assertEquals(before, changed.readText())
        assertFalse(missing.exists())
        assertTrue(stale.exists())
        assertEquals("# Mine", manual.readText())
    }

    @Test
    fun checkOnAnEmptyFolderIsOutOfDateAndCreatesNothing() {
        assertEquals(3, run("build", "--config", config.path, "--out", File(temp, "docs").path, "--check"))
        assertFalse(File(temp, "docs").exists())
    }

    @Test
    fun checkStillStopsOnACollisionWithAHandWrittenFile() {
        val docs = File(temp, "docs").also { it.mkdirs() }
        File(docs, "README.md").writeText("# My project")

        assertEquals(1, run("build", "--config", config.path, "--out", docs.path, "--check"))
    }

    private fun relativeSummary(docs: File): String =
        File(fixtures, "orders-checkout-api/SUMMARY.md").canonicalFile.relativeTo(docs.canonicalFile).invariantSeparatorsPath
}
