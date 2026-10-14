package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.internal.Markdown
import io.zenwave360.language.docs.internal.problemText
import io.zenwave360.language.eventflow.application.GenerateMermaidFromZfl
import io.zenwave360.language.zfl.ZflParser

/** `flows.md`: per ZFL file, its flowchart and one sequence diagram per outcome. */
internal object FlowsPage {

    const val PATH = "flows.md"
    const val TITLE = "Event flows"

    fun render(sources: List<DocsSource>, problems: MutableList<String>): String = buildString {
        append(Markdown.header(sources))
        append("# ").append(TITLE).append("\n\n")
        for (source in sources) {
            append(section(source, problems))
        }
    }

    private fun section(source: DocsSource, problems: MutableList<String>): String {
        val sourceProblems = ZflParser().parse(source.content).syntaxProblems.map { problemText(it) }
        problems += sourceProblems.map { "${source.path}: $it" }

        val view = try {
            GenerateMermaidFromZfl().execute(source.content)
        } catch (e: Exception) {
            val reason = "cannot render the flow: ${e.message?.lineSequence()?.firstOrNull() ?: e::class.simpleName}"
            problems += "${source.path}: $reason"
            return "## ${source.path}\n\n" + Markdown.problems(sourceProblems + reason)
        }

        return buildString {
            append("## ").append(Markdown.inline(view.flowName.ifBlank { source.path })).append("\n\n")
            append("Source: ").append(Markdown.code(source.path)).append("\n\n")
            append(Markdown.problems(sourceProblems))
            append("### Flowchart\n\n")
            append(Markdown.mermaid(view.flowchart)).append('\n')
            for (sequence in view.sequences) {
                append("### Sequence: ").append(Markdown.inline(sequence.title.ifBlank { sequence.endOutcome })).append("\n\n")
                append(Markdown.mermaid(sequence.mermaid)).append('\n')
            }
        }
    }
}
