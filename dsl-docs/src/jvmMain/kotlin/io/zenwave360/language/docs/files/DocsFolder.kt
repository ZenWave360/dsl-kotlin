package io.zenwave360.language.docs.files

import io.zenwave360.language.docs.DocsOutput
import io.zenwave360.language.docs.internal.GENERATED_MARKER
import java.io.File

/** A difference between generated pages and the output folder. [reason] is `missing`, `changed` or `stale, no longer generated`. */
data class DocsDifference(val path: String, val reason: String)

/** What [DocsFolder.write] did: the pages written and the stale generated pages deleted, as paths relative to the folder. */
data class DocsWriteResult(val written: List<String>, val deleted: List<String>)

/**
 * The folder the pages are written to.
 *
 * Ownership is by marker, not by folder: a `.md` file (at any depth) whose first line is the generated-file
 * marker belongs to dsl-docs. It is overwritten, and deleted when a run no longer produces it. Any other file
 * in the folder is hand-written and never touched. The only error is a page that would be generated at the
 * path of a hand-written file: nothing is written then.
 */
class DocsFolder(val outDir: File) {

    private val out: File get() = outDir.absoluteFile

    /**
     * Differences between [output] and the folder, sorted by path; empty means up to date. Nothing is written
     * (line endings are ignored). Throws [IllegalArgumentException] when a generated page would collide with a
     * hand-written file.
     */
    fun check(output: DocsOutput): List<DocsDifference> {
        requireNoCollisions(output)
        val differences = mutableListOf<DocsDifference>()
        for (file in output.files) {
            val existing = File(out, file.path)
            when {
                !existing.isFile -> differences += DocsDifference(file.path, "missing")
                existing.readText().replace("\r\n", "\n") != file.content -> differences += DocsDifference(file.path, "changed")
            }
        }
        stale(output).forEach { differences += DocsDifference(relative(it), "stale, no longer generated") }
        return differences.sortedBy { it.path }
    }

    /**
     * Writes [output]: only generated files are overwritten, stale generated pages are deleted together with the
     * subfolders that this leaves empty. Pages are written as bytes, so they always use `\n`. Throws
     * [IllegalArgumentException], writing nothing, when a generated page would collide with a hand-written file.
     */
    fun write(output: DocsOutput): DocsWriteResult {
        requireNoCollisions(output)
        out.mkdirs()
        val stale = stale(output)
        for (file in stale) {
            file.delete()
            var dir = file.parentFile
            while (dir != out && dir.list().isNullOrEmpty() && dir.delete()) dir = dir.parentFile
        }
        for (file in output.files) {
            File(out, file.path).also { it.parentFile.mkdirs() }.writeBytes(file.content.encodeToByteArray())
        }
        return DocsWriteResult(output.files.map { it.path }, stale.map { relative(it) })
    }

    private fun requireNoCollisions(output: DocsOutput) {
        val manual = output.files.map { File(out, it.path) }.filter { it.isFile && !isGenerated(it) }
        require(manual.isEmpty()) {
            "Not overwriting files that dsl-docs did not generate: ${manual.joinToString(", ") { relative(it) }}. " +
                "Move them, or choose another output folder: only files that start with the generated-file marker are replaced."
        }
    }

    private fun stale(output: DocsOutput): List<File> {
        if (!out.isDirectory) return emptyList()
        val produced = output.files.map { it.path }.toSet()
        return out.walkTopDown().filter { it.isFile && it.name.endsWith(".md") && isGenerated(it) }
            .filter { relative(it) !in produced }.sortedBy { it.path }.toList()
    }

    private fun relative(file: File): String = file.relativeTo(out).invariantSeparatorsPath

    private fun isGenerated(file: File): Boolean = file.bufferedReader().use { it.readLine() } == GENERATED_MARKER
}
