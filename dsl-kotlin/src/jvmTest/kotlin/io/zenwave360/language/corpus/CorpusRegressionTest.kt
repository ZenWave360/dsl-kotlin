package io.zenwave360.language.corpus

import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zfl.ZflParser
import java.io.File
import kotlin.test.Test

/**
 * Parses a corpus of .zdl, .zfl and .zw files with this build and with a released dsl-kotlin jar, and compares errors and models.
 *
 * Off by default: it needs files outside the repository. Enable with
 * `ZENWAVE_CORPUS_OLD_JAR=<dsl-kotlin-jvm-x.y.z.jar>` and `ZENWAVE_CORPUS_ROOTS=<dir>;<dir>...`.
 * `ZENWAVE_CORPUS_IGNORE=<regex>` drops model paths that exist only in the new build and match, as intentional additions.
 * The report is written to `build/corpus-regression.txt`.
 */
class CorpusRegressionTest {

    @Test
    fun corpusParsesAsOnTheReleasedVersion() {
        val oldJar = System.getenv("ZENWAVE_CORPUS_OLD_JAR") ?: return
        val roots = System.getenv("ZENWAVE_CORPUS_ROOTS") ?: return
        val work = File("build/corpus").apply { mkdirs() }
        val newCp = System.getProperty("java.class.path").split(File.pathSeparator)
        // the released jar replaces this module's own main classes
        val ownMain = newCp.filter { it.replace(Regex("[\\\\/]+"), "/").let { p -> p.contains("/dsl-kotlin/build/") && (p.endsWith("/classes/kotlin/jvm/main") || p.endsWith("/classes/java/jvmMain") || p.endsWith("/processedResources/jvm/main") || p.contains("/build/libs/")) } }
        check(ownMain.isNotEmpty()) { "own main classes not found in $newCp" }
        val oldCp = newCp.filterNot { it in ownMain } + oldJar
        val java = File(System.getProperty("java.home"), "bin/java").path
        for ((name, cp) in listOf("old" to oldCp, "new" to newCp)) {
            val out = File(work, "$name.tsv")
            val proc = ProcessBuilder(listOf(java, "-cp", cp.joinToString(File.pathSeparator), "io.zenwave360.language.corpus.CorpusRegressionTest", out.path, roots))
                .redirectErrorStream(true).redirectOutput(File(work, "$name.log")).start()
            check(proc.waitFor() == 0) { "dump $name failed, see $work/$name.log" }
        }
        val report = compare(read(File(work, "old.tsv")), read(File(work, "new.tsv")), Regex(System.getenv("ZENWAVE_CORPUS_IGNORE") ?: "(?!)"))
        File("build/corpus-regression.txt").writeText(report)
        println(report.lines().take(40).joinToString("\n"))
    }

    private fun read(f: File): Map<String, List<Pair<String, String>>> {
        val files = LinkedHashMap<String, MutableList<Pair<String, String>>>()
        f.forEachLine { line ->
            val parts = line.split('\t', limit = 3)
            files.getOrPut(parts[0]) { mutableListOf() }.add(parts[1] to parts.getOrElse(2) { "" })
        }
        return files
    }

    private fun norm(key: String) = key.replace(Regex("\\[\\d+]"), "[]")

    private fun compare(old: Map<String, List<Pair<String, String>>>, new: Map<String, List<Pair<String, String>>>, ignore: Regex): String {
        val sb = StringBuilder()
        val newErrors = mutableListOf<String>()
        val fixedErrors = mutableListOf<String>()
        val changed = mutableListOf<String>()
        val removed = mutableListOf<String>()
        val addedPatterns = sortedMapOf<String, Int>()
        val ignoredPatterns = sortedMapOf<String, Int>()
        val newSyntax = mutableListOf<String>()
        var errorsOld = 0
        var errorsNew = 0
        for ((file, oldEntries) in old) {
            val newEntries = new[file] ?: continue
            val oldMap = oldEntries.filter { it.first != "PROBLEM" }.toMap()
            val newMap = newEntries.filter { it.first != "PROBLEM" }.toMap()
            val oldProblems = oldEntries.filter { it.first == "PROBLEM" }.map { it.second }
            val newProblems = newEntries.filter { it.first == "PROBLEM" }.map { it.second }
            errorsOld += oldProblems.size
            errorsNew += newProblems.size
            if (oldProblems.sorted() != newProblems.sorted()) {
                val gained = newProblems - oldProblems.toSet()
                val lost = oldProblems - newProblems.toSet()
                if (gained.isNotEmpty()) newErrors += "$file: ${gained.take(3)} (old ${oldProblems.size}, new ${newProblems.size})"
                if (lost.isNotEmpty()) fixedErrors += "$file: ${lost.take(3)} (old ${oldProblems.size}, new ${newProblems.size})"
            }
            // a file the old build could not parse uses new syntax: nothing to compare
            if (oldProblems.isNotEmpty() && newProblems.isEmpty()) { newSyntax += file; continue }
            for ((k, v) in oldMap) {
                val nv = newMap[k]
                if (nv == null) removed += "$file $k = $v"
                else if (nv != v) changed += "$file $k: '$v' -> '$nv'"
            }
            for (k in newMap.keys) if (k !in oldMap) {
                if (ignore.containsMatchIn(k)) ignoredPatterns.merge(norm(k), 1, Int::plus) else addedPatterns.merge(norm(k), 1, Int::plus)
            }
        }
        sb.appendLine("files old=${old.size} new=${new.size}")
        sb.appendLine("problems old=$errorsOld new=$errorsNew")
        sb.appendLine("NEW ERRORS (${newErrors.size} files)"); newErrors.forEach { sb.appendLine("  $it") }
        sb.appendLine("ERRORS GONE (${fixedErrors.size} files)"); fixedErrors.forEach { sb.appendLine("  $it") }
        sb.appendLine("NEW SYNTAX, not compared (${newSyntax.size} files)"); newSyntax.forEach { sb.appendLine("  $it") }
        sb.appendLine("CHANGED VALUES (${changed.size})"); changed.take(200).forEach { sb.appendLine("  $it") }
        sb.appendLine("REMOVED KEYS (${removed.size})"); removed.take(200).forEach { sb.appendLine("  $it") }
        sb.appendLine("ADDED KEY PATTERNS not ignored (${addedPatterns.size})"); addedPatterns.forEach { (k, n) -> sb.appendLine("  $n x $k") }
        sb.appendLine("ADDED KEY PATTERNS ignored (${ignoredPatterns.size})"); ignoredPatterns.forEach { (k, n) -> sb.appendLine("  $n x $k") }
        return sb.toString()
    }

    companion object {
        private val skipDirs = setOf("node_modules", "target", "build")

        @JvmStatic
        fun main(args: Array<String>) {
            val out = File(args[0])
            val roots = args[1].split(';').filter { it.isNotBlank() }.map { File(it) }
            val files = roots.flatMap { root ->
                root.walkTopDown().onEnter { it == root || (it.name !in skipDirs && !it.name.startsWith(".")) }
                    .filter { it.isFile && it.extension in setOf("zdl", "zfl", "zw") }.toList()
            }.map { it.absoluteFile.invariantSeparatorsPath }.distinct().sorted()
            out.printWriter().use { w ->
                for (path in files) {
                    val text = File(path).readText()
                    try {
                        val model: Map<String, Any?> = if (path.endsWith(".zfl")) ZflParser().parseModel(text) else ZdlParser().parseModel(text)
                        val problems = model["problems"] as? List<*> ?: emptyList<Any?>()
                        problems.forEach { p ->
                            val m = p as? Map<*, *>
                            w.println("$path\tPROBLEM\t${esc("${m?.get("path")} | ${m?.get("message")} | ${m?.get("value")}")}")
                        }
                        flatten("", model.filterKeys { it != "locations" && it != "problems" }) { k, v -> w.println("$path\t$k\t${esc(v)}") }
                    } catch (e: Throwable) {
                        w.println("$path\tPROBLEM\t${esc("EXCEPTION ${e::class.simpleName}: ${e.message}")}")
                    }
                }
            }
        }

        private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t").replace("\r", "")

        private fun flatten(prefix: String, value: Any?, emit: (String, String) -> Unit) {
            when (value) {
                is Map<*, *> -> {
                    if (value.isEmpty()) emit(prefix, "{}")
                    value.entries.sortedBy { it.key.toString() }.forEach { flatten(if (prefix.isEmpty()) "${it.key}" else "$prefix.${it.key}", it.value, emit) }
                }
                is List<*> -> {
                    if (value.isEmpty()) emit(prefix, "[]")
                    value.forEachIndexed { i, v -> flatten("$prefix[$i]", v, emit) }
                }
                else -> emit(prefix, value.toString())
            }
        }
    }
}
