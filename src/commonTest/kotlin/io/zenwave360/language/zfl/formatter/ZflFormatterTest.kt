package io.zenwave360.language.zfl.formatter

import io.zenwave360.language.readTestFile
import io.zenwave360.language.zfl.ZflParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZflFormatterTest {

    @Test
    fun format_preserves_existing_subscriptions_flow() {
        val input = readTestFile("flow/subscriptions.zfl")

        val formatted = ZflFormatter().format(input)

        assertEquals(normalizeForComparison(input), normalizeForComparison(formatted))
    }

    @Test
    fun format_normalizes_indentation_spacing_and_trailing_whitespace() {
        val input = """
            systems   {
            TestSystem {
            service TestService   {
            commands: doSomething   
            }
            }
            }
            
            flow  SimpleFlow {
            start   UserAction {}
            
            
            // comment about the flow   
            when   UserAction   do   doSomething   {
            event SomethingDone    
            }
            
            end   {
            completed:   SomethingDone   
            }
            }
        """.trimIndent()

        val expected = """
            systems {
                TestSystem {
                    service TestService {
                        commands: doSomething
                    }
                }
            }

            flow SimpleFlow {
                start UserAction {}


                // comment about the flow
                when UserAction do doSomething {
                    event SomethingDone
                }

                end {
                    completed: SomethingDone
                }
            }
        """.trimIndent() + "\n"

        val formatted = ZflFormatter().format(input)

        assertEquals(expected, formatted)
    }

    @Test
    fun format_is_idempotent_for_subscriptions_fixture() {
        val input = readTestFile("flow/subscriptions.zfl")

        val once = ZflFormatter().format(input)
        val twice = ZflFormatter().format(once)

        assertEquals(once, twice)
    }

    @Test
    fun format_is_idempotent_for_place_order_fixture() {
        val input = readTestFile("flow/place-order-flow.zfl")

        val once = ZflFormatter().format(input)
        val twice = ZflFormatter().format(once)

        assertEquals(once, twice)
    }

    @Test
    fun format_preserves_parseability_for_place_order_fixture() {
        val input = readTestFile("flow/place-order-flow.zfl")

        val formatted = ZflFormatter().format(input)
        val model = ZflParser().parseModel(formatted)

        assertTrue(model.getProblems().isEmpty(), "Formatted ZFL should parse without problems")
    }

    @Test
    fun format_preserves_javadoc_and_empty_block_shape() {
        val input = """
            /**
             * Flow doc
             */
            flow SampleFlow {
            start Trigger {}
            }
        """.trimIndent()

        val expected = """
            /**
             * Flow doc
             */
            flow SampleFlow {
                start Trigger {}
            }
        """.trimIndent() + "\n"

        val formatted = ZflFormatter().format(input)

        assertEquals(expected, formatted)
    }

    private fun withTrailingNewline(value: String): String =
        if (value.endsWith("\n")) value else "$value\n"

    private fun normalizeLineEndings(value: String): String =
        value.replace("\r\n", "\n")

    private fun normalizeForComparison(value: String): String =
        withTrailingNewline(normalizeLineEndings(value).trimEnd('\n'))
}
