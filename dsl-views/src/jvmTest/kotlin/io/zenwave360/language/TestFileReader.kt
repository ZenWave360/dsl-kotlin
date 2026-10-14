package io.zenwave360.language

import java.nio.file.Files
import java.nio.file.Paths

actual fun readTestFile(fileName: String): String {
    val fullPath = "../dsl-kotlin/src/commonTest/resources/$fileName"
    return String(Files.readAllBytes(Paths.get(fullPath)))
}
