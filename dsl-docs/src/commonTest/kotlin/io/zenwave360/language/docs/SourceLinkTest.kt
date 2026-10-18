package io.zenwave360.language.docs

import io.zenwave360.language.docs.pages.collapseDots
import kotlin.test.Test
import kotlin.test.assertEquals

/** Links to ZDL files stay short when the config folder is a sibling of the output folder. */
class SourceLinkTest {

    @Test
    fun collapsesParentSegments() {
        assertEquals("../../../../a/b.zdl", collapseDots("../../cfg/../../../a/b.zdl"))
        assertEquals("../a.zdl", collapseDots("../a.zdl"))
        assertEquals("x.zdl", collapseDots("modules/../x.zdl"))
        assertEquals("a.zdl", collapseDots("a.zdl"))
    }
}
