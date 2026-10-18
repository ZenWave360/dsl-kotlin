package io.zenwave360.language.docs

import io.zenwave360.language.docs.files.DocsFiles
import io.zenwave360.language.docs.files.DocsFolder
import java.io.File
import kotlin.system.exitProcess

/**
 * Developer tool of this repository, not shipped: regenerates documentation of real projects.
 *
 * `./gradlew -q :dsl-docs:devDocs -Pargs="--zdl a.zdl,b.zdl --zfl x.zfl --api openapi.yml,asyncapi.yml --out dir [--title t] [--base dir] [--check]"`
 *
 * Comma separated lists; paths are relative to `--base` (default: the working directory, the repository root) or
 * absolute. With `--check` nothing is written and the exit code is 3 when the folder is out of date.
 */
fun main(args: Array<String>) {
    val options = mutableMapOf<String, String>()
    var check = false
    var i = 0
    while (i < args.size) {
        val name = args[i]
        if (name == "--check") {
            check = true
            i++
        } else {
            val value = args.getOrNull(i + 1)
            if (!name.startsWith("--") || value == null) usage("Unexpected argument $name")
            options[name] = value
            i += 2
        }
    }
    val known = setOf("--zdl", "--zfl", "--api", "--out", "--title", "--base")
    (options.keys - known).firstOrNull()?.let { usage("Unknown argument $it") }
    val out = options["--out"] ?: usage("--out is required")
    fun list(name: String) = options[name]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    try {
        val outDir = File(out)
        val output = DslDocs().generate(
            DocsFiles.input(
                baseDir = File(options["--base"] ?: "."), outDir = outDir,
                zdlFiles = list("--zdl"), zflFiles = list("--zfl"), apiFiles = list("--api"), title = options["--title"],
            ),
        )
        output.problems.forEach { System.err.println("WARNING $it") }
        val folder = DocsFolder(outDir)
        if (check) {
            val differences = folder.check(output)
            if (differences.isEmpty()) {
                println("Up to date: ${output.files.size} files in $out")
            } else {
                differences.forEach { System.err.println("OUT OF DATE ${it.path} (${it.reason})") }
                exitProcess(3)
            }
        } else {
            val result = folder.write(output)
            println("Wrote ${result.written.size} files to $out, deleted ${result.deleted.size}")
        }
    } catch (e: IllegalArgumentException) {
        System.err.println("ERROR ${e.message}")
        exitProcess(1)
    }
}

private fun usage(message: String): Nothing {
    System.err.println("$message\nUsage: --zdl a,b --zfl x --api y,z --out dir [--title t] [--base dir] [--check]")
    exitProcess(2)
}
