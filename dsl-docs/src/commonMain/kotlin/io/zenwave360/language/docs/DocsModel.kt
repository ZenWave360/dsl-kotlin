package io.zenwave360.language.docs

import io.zenwave360.language.docs.internal.Yaml

/** The kinds of model and specification files dsl-docs reads. */
enum class SourceKind {
    ZDL,
    ZFL,
    OPENAPI,
    ASYNCAPI,
    ;

    companion object {
        /**
         * Tells an API file from its content: [OPENAPI] when the YAML or JSON top level has an `openapi` (or
         * `swagger`) key, [ASYNCAPI] for `asyncapi`, null when it is neither or cannot be read.
         */
        fun ofApi(content: String): SourceKind? {
            val root = try {
                Yaml.read(content)
            } catch (e: Exception) {
                return null
            }
            return when {
                "openapi" in root || "swagger" in root -> OPENAPI
                "asyncapi" in root -> ASYNCAPI
                else -> null
            }
        }
    }
}

/**
 * One input file. [path] is how the documentation names the file, for example `domain-model.zdl`;
 * callers use a path relative to the project so the output does not depend on where the project is.
 */
data class DocsSource(
    val kind: SourceKind,
    val path: String,
    val content: String,
)

/**
 * Reads a file that a specification refers to with a relative `$ref` (an Avro schema, a shared schema file).
 * [path] is relative to the same folder as [DocsSource.path], with `/` separators and no `.` or inner `..` segments.
 * Returns null when there is no such file. It is never asked for `http(s)` references.
 */
fun interface DocsFileReader {
    fun read(path: String): String?
}

/** A link to a page written by hand, listed on the index page. [href] is relative to the output folder. */
data class DocsLink(
    val title: String,
    val href: String,
)

/**
 * Everything one documentation run needs. Nothing is read from the filesystem or the network:
 * callers pass the file contents in [sources].
 *
 * [title] and [version] default to the first ZDL `config` title and version, then the first
 * OpenAPI or AsyncAPI `info`. [date] is printed only when given, so the output never depends
 * on the clock.
 *
 * [sourcesHref] is the relative path from the output folder to the folder the source paths are relative to,
 * for example `../`. Pages use it to link to the ZDL files; when null they name the files without a link.
 *
 * [fileReader] loads the files that OpenAPI and AsyncAPI files refer to (`$ref: avro/Order.avsc`). Without it
 * such references are shown as external classes. The files that were read are listed, with their hashes,
 * in the header of the page that used them.
 */
data class DocsInput(
    val sources: List<DocsSource>,
    val title: String? = null,
    val version: String? = null,
    val date: String? = null,
    val links: List<DocsLink> = emptyList(),
    val sourcesHref: String? = null,
    val fileReader: DocsFileReader? = null,
)

/** One generated file. [path] is relative to the output folder and always uses `/`. */
data class DocsFile(
    val path: String,
    val content: String,
)

/**
 * The generated files, sorted by path, and the [problems] found in the inputs. A problem never stops
 * the run: the page shows what could be read and names the problem.
 */
data class DocsOutput(
    val files: List<DocsFile>,
    val problems: List<String>,
)
