package io.zenwave360.language.docs.cli

import io.zenwave360.language.docs.DocsConfig
import io.zenwave360.language.docs.DocsFileReader
import io.zenwave360.language.docs.DocsInput
import io.zenwave360.language.docs.DocsLink
import io.zenwave360.language.docs.DocsOutput
import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.DslDocs
import io.zenwave360.language.docs.internal.GENERATED_MARKER
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

private const val USAGE = "Usage: dsl-docs build --config <dsl-docs.yml> --out <dir> [--check]"

fun main(args: Array<String>) {
    exitProcess(DslDocsCli(System.out, System.err).run(args))
}

/**
 * `dsl-docs build --config <file> --out <dir> [--check]`: reads the files the config names, relative to the
 * config file, and writes the Markdown pages into the output folder.
 *
 * Ownership is by marker, not by folder: a `.md` file (at any depth) whose first line is the generated-file
 * marker belongs to dsl-docs. It is overwritten, and deleted when this run no longer produces it. Any other
 * file in the output folder is hand-written and never touched. The run stops, writing nothing, only when a
 * page it would generate has the path of a hand-written file.
 *
 * With `--check` nothing is written: the pages are generated in memory and compared with the folder
 * (line endings ignored). Missing, changed and stale generated files are listed.
 *
 * Exit codes: 0 done, or with `--check` the folder is up to date (problems in the inputs are printed as
 * warnings); 1 a config or input file cannot be read, or a generated page collides with a hand-written file;
 * 2 wrong arguments; 3 `--check` found the folder out of date.
 */
class DslDocsCli(private val out: PrintStream, private val err: PrintStream) {

    fun run(args: Array<String>): Int {
        if (args.firstOrNull() != "build") return usage("Expected the build command")
        var config: File? = null
        var outDir: File? = null
        var check = false
        var i = 1
        while (i < args.size) {
            val value = args.getOrNull(i + 1)
            when (args[i]) {
                "--config" -> { config = value?.let(::File); i += 2 }
                "--out" -> { outDir = value?.let(::File); i += 2 }
                "--check" -> { check = true; i += 1 }
                else -> return usage("Unknown argument ${args[i]}")
            }
        }
        if (config == null || outDir == null) return usage("--config and --out are required")

        return try {
            val plan = plan(config, outDir)
            plan.output.problems.forEach { err.println("WARNING $it") }
            if (check) {
                if (plan.differences.isEmpty()) {
                    out.println("Up to date: ${plan.output.files.size} files in ${outDir.path}")
                    0
                } else {
                    plan.differences.forEach { err.println("OUT OF DATE ${it.path} (${it.reason})") }
                    err.println("The documentation in ${outDir.path} is out of date: run dsl-docs build without --check and commit the result.")
                    3
                }
            } else {
                apply(plan)
                out.println("Wrote ${plan.output.files.size} files to ${outDir.path}")
                0
            }
        } catch (e: IllegalArgumentException) {
            err.println("ERROR ${e.message}")
            1
        }
    }

    /** Generates the documentation of [configFile] into [outDir] and returns what was written. */
    fun build(configFile: File, outDir: File): DocsOutput = plan(configFile, outDir).also { apply(it) }.output

    private class Difference(val path: String, val reason: String)

    private class Plan(val output: DocsOutput, val out: File, val differences: List<Difference>, val stale: List<File>)

    /** Generates in memory and compares with the folder; nothing is written. */
    private fun plan(configFile: File, outDir: File): Plan {
        require(configFile.isFile) { "Config file not found: ${configFile.path}" }
        val config = DocsConfig.parse(configFile.readText())
        val base = configFile.absoluteFile.parentFile
        val out = outDir.absoluteFile

        val missing = config.sources.map { (_, path) -> path }.filter { !File(base, it).isFile }
        require(missing.isEmpty()) { "Files named in ${configFile.name} not found: ${missing.joinToString(", ")}" }

        val sources = config.sources.map { (kind, path) ->
            val file = File(base, path).canonicalFile
            DocsSource(kind, file.relativeTo(base.canonicalFile).invariantSeparatorsPath, file.readText())
        }
        val links = config.pages.map { page ->
            DocsLink(page.title, File(base, page.href).canonicalFile.relativeTo(out.canonicalFile).invariantSeparatorsPath)
        }
        val output = DslDocs().generate(
            DocsInput(
                sources = sources, title = config.title, version = config.version, date = config.date, links = links,
                sourcesHref = sourcesHref(base, out),
                fileReader = DocsFileReader { path -> File(base, path).takeIf { it.isFile }?.readText() },
            ),
        )

        val manual = output.files.map { File(out, it.path) }.filter { it.isFile && !isGenerated(it) }
        require(manual.isEmpty()) {
            "Not overwriting files that dsl-docs did not generate: ${manual.joinToString(", ") { it.relativeTo(out).invariantSeparatorsPath }}. " +
                "Move them, or choose another output folder: only files that start with the generated-file marker are replaced."
        }

        val produced = output.files.map { it.path }.toSet()
        val differences = mutableListOf<Difference>()
        for (file in output.files) {
            val existing = File(out, file.path)
            when {
                !existing.isFile -> differences += Difference(file.path, "missing")
                existing.readText().replace("\r\n", "\n") != file.content -> differences += Difference(file.path, "changed")
            }
        }
        val stale = if (out.isDirectory) {
            out.walkTopDown().filter { it.isFile && it.name.endsWith(".md") && isGenerated(it) }
                .filter { it.relativeTo(out).invariantSeparatorsPath !in produced }.sortedBy { it.path }.toList()
        } else {
            emptyList()
        }
        stale.forEach { differences += Difference(it.relativeTo(out).invariantSeparatorsPath, "stale, no longer generated") }
        return Plan(output, out, differences.sortedBy { it.path }, stale)
    }

    private fun apply(plan: Plan) {
        plan.out.mkdirs()
        for (file in plan.stale) {
            file.delete()
            // Drop subfolders (modules/) that the deletion left empty.
            var dir = file.parentFile
            while (dir != plan.out && dir.list().isNullOrEmpty() && dir.delete()) dir = dir.parentFile
        }
        for (file in plan.output.files) {
            // Bytes, not text: the output always uses \n, whatever the platform's line separator.
            File(plan.out, file.path).also { it.parentFile.mkdirs() }.writeBytes(file.content.encodeToByteArray())
        }
    }

    /** Path from the output folder to the config folder, with a trailing `/` (empty when they are the same), or null when there is none. */
    private fun sourcesHref(base: File, out: File): String? = try {
        base.canonicalFile.relativeTo(out.canonicalFile).invariantSeparatorsPath.let { if (it.isEmpty()) "" else "$it/" }
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun isGenerated(file: File): Boolean =
        file.bufferedReader().use { it.readLine() } == GENERATED_MARKER

    private fun usage(message: String): Int {
        err.println("$message\n$USAGE")
        return 2
    }
}
