package io.zenwave360.language.views

import io.zenwave360.language.zdl.ZdlModel

/**
 * Placeholder view: proves that dsl-views reads the semantic model through the public dsl-kotlin API.
 */
object ZdlModelSummaryView {

    fun render(model: ZdlModel): String = "entities: ${model.getEntities().size}"
}
