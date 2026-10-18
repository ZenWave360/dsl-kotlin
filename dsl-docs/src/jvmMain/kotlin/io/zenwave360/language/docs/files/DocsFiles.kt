package io.zenwave360.language.docs.files

import io.zenwave360.language.docs.DocsFileReader
import io.zenwave360.language.docs.DocsInput
import io.zenwave360.language.docs.DocsLink
import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.SourceKind
import java.io.File

/** Reads the files of a project into a [DocsInput]. */
object DocsFiles {

    /**
     * Reads the files (paths relative to [baseDir], or absolute) into a [DocsInput]. API files are classified with
     * [SourceKind.ofApi]; a file that is neither OpenAPI nor AsyncAPI, or a missing file, is an
     * [IllegalArgumentException] naming it (all the problems of one kind are listed together).
     *
     * Source paths in the pages are relative to [baseDir] (the absolute path when there is no relative one, for
     * example another drive). `sourcesHref` is the path from [outDir] to [baseDir], so pages link to the sources.
     * The file reader loads the relative `$ref` files next to each source; it never reads `http(s)`.
     */
    fun input(
        baseDir: File,
        outDir: File,
        zdlFiles: List<String> = emptyList(),
        zflFiles: List<String> = emptyList(),
        apiFiles: List<String> = emptyList(),
        title: String? = null,
        version: String? = null,
        date: String? = null,
        links: List<DocsLink> = emptyList(),
    ): DocsInput {
        val base = baseDir.absoluteFile
        val resolved = listOf(SourceKind.ZDL to zdlFiles, SourceKind.ZFL to zflFiles, null to apiFiles)
            .flatMap { (kind, paths) -> paths.map { kind to resolve(base, it) } }

        val missing = resolved.filter { !it.second.isFile }
        require(missing.isEmpty()) { "Files not found: ${missing.joinToString(", ") { display(base, it.second) }}" }

        val unknown = mutableListOf<String>()
        val sources = resolved.mapNotNull { (kind, file) ->
            val text = file.readText()
            val actual = kind ?: SourceKind.ofApi(text)
            if (actual == null) {
                unknown += display(base, file)
                null
            } else {
                DocsSource(actual, display(base, file), text)
            }
        }
        require(unknown.isEmpty()) {
            "Not an OpenAPI or AsyncAPI file (no top level openapi, swagger or asyncapi key): ${unknown.joinToString(", ")}"
        }

        return DocsInput(
            sources = sources,
            title = title,
            version = version,
            date = date,
            links = links,
            sourcesHref = sourcesHref(base, outDir.absoluteFile),
            fileReader = DocsFileReader { path -> resolve(base, path).takeIf { it.isFile }?.readText() },
        )
    }

    private fun resolve(base: File, path: String): File {
        val file = File(path)
        return (if (file.isAbsolute) file else File(base, path)).canonicalFile
    }

    /** Path of [file] relative to [base] with `/` separators, or the absolute path when there is no relative one. */
    private fun display(base: File, file: File): String = try {
        file.relativeTo(base.canonicalFile).invariantSeparatorsPath
    } catch (e: IllegalArgumentException) {
        file.invariantSeparatorsPath
    }

    /** Path from the output folder to the base folder, with a trailing `/` (empty when they are the same), or null when there is none. */
    private fun sourcesHref(base: File, out: File): String? = try {
        base.canonicalFile.relativeTo(out.canonicalFile).invariantSeparatorsPath.let { if (it.isEmpty()) "" else "$it/" }
    } catch (e: IllegalArgumentException) {
        null
    }
}
