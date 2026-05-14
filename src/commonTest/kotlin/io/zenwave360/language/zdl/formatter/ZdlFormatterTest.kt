package io.zenwave360.language.zdl.formatter

import io.zenwave360.language.readTestFile
import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZdlFormatterTest {

    @Test
    fun format_normalizes_basic_zdl_structure_without_reordering() {
        val input = """
            @import("com.example:artifact:RELEASE")   
            
            entity   Customer {
            firstName   String   required    
            
            // keep this comment   
            lastName String
            }
        """.trimIndent()

        val expected = """
            @import("com.example:artifact:RELEASE")

            entity Customer {
                firstName String required

                // keep this comment
                lastName String
            }
        """.trimIndent() + "\n"

        val formatted = ZdlFormatter().format(input)

        assertEquals(expected, formatted)
    }

    @Test
    fun format_is_idempotent_for_complete_zdl_fixture() {
        val input = readTestFile("complete.zdl")

        val once = ZdlFormatter().format(input)
        val twice = ZdlFormatter().format(once)

        assertEquals(once, twice)
    }

    @Test
    fun format_preserves_parseability_for_complete_zdl_fixture() {
        val input = readTestFile("complete.zdl")

        val formatted = ZdlFormatter().format(input)
        val model = ZdlParser().parseModel(formatted)

        assertTrue(model.getProblems().isEmpty(), "Formatted ZDL should parse without problems")
    }

    @Test
    fun format_preserves_single_line_object_array_and_suffix_javadoc_forms() {
        val input = """
            @array_annotation([item1, item2, item3])
            @object_annotation({item1: value1, item2: value2})
            entity Sample {
            field String required /** field javadoc */
            }
        """.trimIndent()

        val expected = """
            @array_annotation([item1, item2, item3])
            @object_annotation({item1: value1, item2: value2})
            entity Sample {
                field String required /** field javadoc */
            }
        """.trimIndent() + "\n"

        val formatted = ZdlFormatter().format(input)

        assertEquals(expected, formatted)
    }
}
