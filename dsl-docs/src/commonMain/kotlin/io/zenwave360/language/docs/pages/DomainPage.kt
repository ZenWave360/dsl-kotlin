package io.zenwave360.language.docs.pages

import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.internal.asMap
import io.zenwave360.language.docs.internal.problemText
import io.zenwave360.language.docs.internal.string
import io.zenwave360.language.zdl.ZdlModel
import io.zenwave360.language.zdl.ZdlParser

/** A ZDL file with its parsed model, read once and shared by the pages that need it. */
internal class ZdlDocument(val source: DocsSource) {
    val model: ZdlModel = ZdlParser().parseModel(source.content)
    val title: String? = model["config"].asMap().string("title")
    val version: String? = model["config"].asMap().string("version")

    val problems: List<String> = model.getProblems().map { problemText(it) }
}

/** `domain.md` (a single ZDL file): the same sections as a module page, see [ModulePage]. */
internal object DomainPage {

    const val PATH = "domain.md"
    const val TITLE = "Domain model"

    fun render(context: ArchitectureContext, problems: MutableList<String>): String {
        val document = context.documents.single()
        val module = context.architecture.modules.firstOrNull { !it.referenced && context.document(it) === document }
        return ModulePage.render(context, document, module, problems)
    }
}
