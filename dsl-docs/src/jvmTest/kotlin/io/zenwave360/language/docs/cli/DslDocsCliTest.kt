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
            // The CLI links the hand-written page relative to the output folder, so only that line differs.
            val expected = File(snapshots, file.name).readText().replace("\r\n", "\n")
            assertEquals(expected.replace("(../SUMMARY.md)", "(${relativeSummary(docs)})"), generated, file.name)
        }
        assertTrue(out.toString().startsWith("Wrote 5 files to"))
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
        assertTrue(err.toString().contains("Usage: dsl-docs build --config <dsl-docs.yml> --out <dir>"))
    }

    private fun relativeSummary(docs: File): String =
        File(fixtures, "orders-checkout-api/SUMMARY.md").canonicalFile.relativeTo(docs.canonicalFile).invariantSeparatorsPath
}
