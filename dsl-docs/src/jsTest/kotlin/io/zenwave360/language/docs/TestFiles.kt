package io.zenwave360.language.docs

// Same approach as dsl-views: no static `fs` import, so the browser test bundle still loads.
// Only Node tests read fixtures. Node runs the tests from build/js/packages/<module>-test.
private val nodeFs: dynamic by lazy {
    val process = js("globalThis.process")
    if (process == undefined || process.getBuiltinModule == undefined) {
        error("readTestFile needs Node.js 22.3+ (process.getBuiltinModule); fixtures are not readable in a browser")
    }
    process.getBuiltinModule("node:fs")
}

actual fun readTestFile(path: String): String =
    (nodeFs.readFileSync("../../../../dsl-docs/src/commonTest/resources/$path", "utf8") as String).replace("\r\n", "\n")

actual fun updateSnapshot(path: String, content: String): Boolean = false
