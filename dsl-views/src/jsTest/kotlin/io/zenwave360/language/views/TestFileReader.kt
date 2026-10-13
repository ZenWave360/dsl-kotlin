package io.zenwave360.language.views

// Same approach as dsl-kotlin's jsTest TestFileReader: no static `fs` import, so the
// browser test bundle still loads. Only Node tests read fixtures.
private val nodeFs: dynamic by lazy {
    val process = js("globalThis.process")
    if (process == undefined || process.getBuiltinModule == undefined) {
        error("readTestFile needs Node.js 22.3+ (process.getBuiltinModule); fixtures are not readable in a browser")
    }
    process.getBuiltinModule("node:fs")
}

actual fun readTestFile(fileName: String): String {
    val fullPath = "../../../../dsl-kotlin/src/commonTest/resources/$fileName"
    return nodeFs.readFileSync(fullPath, "utf8") as String
}
