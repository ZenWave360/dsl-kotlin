package io.zenwave360.language.docs

import java.io.File

private val resources = File("src/commonTest/resources")

actual fun readTestFile(path: String): String = File(resources, path).readText().replace("\r\n", "\n")

actual fun updateSnapshot(path: String, content: String): Boolean {
    if (System.getenv("DSL_DOCS_UPDATE_SNAPSHOTS") != "true") return false
    File(resources, path).also { it.parentFile.mkdirs() }.writeBytes(content.encodeToByteArray())
    return true
}
