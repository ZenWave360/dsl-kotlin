package io.zenwave360.language.views

import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals

class ZdlModelSummaryViewTest {

    @Test
    fun rendersEntityCountOfAFixtureModel() {
        val model = ZdlParser().parseModel(readTestFile("complete.zdl"))

        assertEquals("entities: ${model.getEntities().size}", ZdlModelSummaryView.render(model))
        assertEquals(true, model.getEntities().isNotEmpty())
    }
}
