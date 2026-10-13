package io.zenwave360.language

// Fixtures are read through process.getBuiltinModule (Node 22.3+) rather than a static
// `@JsModule("fs")` import. A static import would be bundled into the browser test run
// (jsBrowserTest bundles the whole jsTest compilation) and fail it. Only Node tests read fixtures.
private val nodeFs: dynamic by lazy {
    val process = js("globalThis.process")
    if (process == undefined || process.getBuiltinModule == undefined) {
        error("readTestFile needs Node.js 22.3+ (process.getBuiltinModule); fixtures are not readable in a browser")
    }
    process.getBuiltinModule("node:fs")
}

actual fun readTestFile(fileName: String): String {
    val fullPath = "../../../../src/commonTest/resources/$fileName"
    return nodeFs.readFileSync(fullPath, "utf8") as String
}
