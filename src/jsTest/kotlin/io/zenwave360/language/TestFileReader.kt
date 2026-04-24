package io.zenwave360.language

@JsModule("fs")
@JsNonModule
external object fs {
    fun readFileSync(path: String, encoding: String): String
}

actual fun readTestFile(fileName: String): String {
    val fullPath = "../../../../src/commonTest/resources/$fileName"
    return fs.readFileSync(fullPath, "utf8")
}
