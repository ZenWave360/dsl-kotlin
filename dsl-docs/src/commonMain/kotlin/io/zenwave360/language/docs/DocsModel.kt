package io.zenwave360.language.docs

/** The kinds of model and specification files dsl-docs reads. */
enum class SourceKind {
    ZDL,
    ZFL,
    OPENAPI,
    ASYNCAPI,
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
 */
data class DocsInput(
    val sources: List<DocsSource>,
    val title: String? = null,
    val version: String? = null,
    val date: String? = null,
    val links: List<DocsLink> = emptyList(),
    val sourcesHref: String? = null,
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
