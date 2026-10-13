package io.zenwave360.language.views

import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Reads nothing from a filesystem, so it runs on Node and in a headless browser.
 */
class JsBrowserSmokeTest {

    @Test
    fun rendersAnInlineModel() {
        val model = ZdlParser().parseModel("entity Order { name String }")

        assertEquals("entities: 1", ZdlModelSummaryView.render(model))
    }
}
