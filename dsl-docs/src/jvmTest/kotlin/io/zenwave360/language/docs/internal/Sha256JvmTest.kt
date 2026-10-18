package io.zenwave360.language.docs.internal

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The common SHA-256 against the JDK's, for non-ASCII text and every padding length around a block boundary. */
class Sha256JvmTest {

    @Test
    fun matchesMessageDigest() {
        val samples = listOf("é", "日本語 ✓", "\n", "x".repeat(1000)) + (50..130).map { "a".repeat(it) }
        for (text in samples) {
            val expected = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, Sha256.hex(text), "length ${text.length}")
        }
    }
}
