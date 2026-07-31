package io.zenwave360.language.zdl

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZdlEditorTest {

    @Test
    fun setConfigString_replacesOnlyTheExistingValue() {
        val file = Files.createTempFile("zdl-editor", ".zdl")
        try {
            Files.writeString(
                file,
                """
                config {
                    id "urn:example:orders"
                    version "0.0.0-SNAPSHOT"
                    title "Orders"
                }
                """.trimIndent(),
            )

            ZdlEditor().setConfigString(file, "version", "1.2.3-SNAPSHOT")

            assertEquals(
                """
                config {
                    id "urn:example:orders"
                    version "1.2.3-SNAPSHOT"
                    title "Orders"
                }
                """.trimIndent(),
                Files.readString(file),
            )
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun setConfigString_escapesTheReplacementValue() {
        val file = Files.createTempFile("zdl-editor", ".zdl")
        try {
            Files.writeString(file, "config { version \"0.0.0\" }")

            ZdlEditor().setConfigString(file, "version", "one\\two\nthree")

            assertEquals("config { version \"one\\\\two\\nthree\" }", Files.readString(file))
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun setConfigString_requiresAnExistingStringOption() {
        val file = Files.createTempFile("zdl-editor", ".zdl")
        try {
            Files.writeString(file, "config { id \"urn:example:orders\" }")

            assertFailsWith<IllegalArgumentException> {
                ZdlEditor().setConfigString(file, "version", "1.2.3")
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
