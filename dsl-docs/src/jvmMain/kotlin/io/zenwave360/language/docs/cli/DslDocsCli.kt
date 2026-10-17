package io.zenwave360.language.docs.cli

import io.zenwave360.language.docs.DocsConfig
import io.zenwave360.language.docs.DocsInput
import io.zenwave360.language.docs.DocsLink
import io.zenwave360.language.docs.DocsOutput
import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.DslDocs
import io.zenwave360.language.docs.internal.GENERATED_MARKER
import java.io.File
import java.io.PrintStream
import kotlin.system.exitProcess

private const val USAGE = "Usage: dsl-docs build --config <dsl-docs.yml> --out <dir>"

fun main(args: Array<String>) {
    exitProcess(DslDocsCli(System.out, System.err).run(args))
}

/**
 * `dsl-docs build --config <file> --out <dir>`: reads the files the config names, relative to the
 * config file, and writes the Markdown pages into the output folder.
 *
 * The output folder belongs to dsl-docs. Before writing, generated pages from an earlier run that this
 * run does not produce are deleted. A file that does not start with the generated-file marker is never
 * deleted or overwritten: the run stops instead.
 *
 * Exit codes: 0 done (problems in the inputs are printed as warnings), 1 a config or input file cannot be
 * read, 2 wrong arguments.
 */
class DslDocsCli(private val out: PrintStream, private val err: PrintStream) {

    fun run(args: Array<String>): Int {
        if (args.firstOrNull() != "build") return usage("Expected the build command")
        var config: File? = null
        var outDir: File? = null
        var i = 1
        while (i < args.size) {
            val value = args.getOrNull(i + 1)
            when (args[i]) {
                "--config" -> config = value?.let(::File)
                "--out" -> outDir = value?.let(::File)
                else -> return usage("Unknown argument ${args[i]}")
            }
            i += 2
        }
        if (config == null || outDir == null) return usage("--config and --out are required")

        return try {
            val output = build(config, outDir)
            output.problems.forEach { err.println("WARNING $it") }
            out.println("Wrote ${output.files.size} files to ${outDir.path}")
            0
        } catch (e: IllegalArgumentException) {
            err.println("ERROR ${e.message}")
            1
        }
    }

    /** Generates the documentation of [configFile] into [outDir] and returns what was written. */
    fun build(configFile: File, outDir: File): DocsOutput {
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
            ),
        )

        val manual = output.files.map { File(out, it.path) }.filter { it.isFile && !isGenerated(it) }
        require(manual.isEmpty()) {
            "Not overwriting files that dsl-docs did not generate: ${manual.joinToString(", ") { it.name }}. " +
                "Choose an output folder that only dsl-docs writes to, for example docs/zenwave."
        }

        out.mkdirs()
        val written = output.files.map { it.path }.toSet()
        out.listFiles { file -> file.isFile && file.name.endsWith(".md") && file.name !in written && isGenerated(file) }
            ?.forEach { it.delete() }
        for (file in output.files) {
            // Bytes, not text: the output always uses \n, whatever the platform's line separator.
            File(out, file.path).also { it.parentFile.mkdirs() }.writeBytes(file.content.encodeToByteArray())
        }
        return output
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
