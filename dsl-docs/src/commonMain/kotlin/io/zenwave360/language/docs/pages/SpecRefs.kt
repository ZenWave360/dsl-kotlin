package io.zenwave360.language.docs.pages

import io.zenwave360.language.apis.view.RefResolver
import io.zenwave360.language.apis.view.ResolvedNode
import io.zenwave360.language.docs.DocsFileReader
import io.zenwave360.language.docs.DocsSource
import io.zenwave360.language.docs.SourceKind
import io.zenwave360.language.docs.internal.Yaml

/** A parsed document and its identity (the project-relative path), for relative references found inside it. */
internal class RefCtx(val document: Any?, val base: String)

/** A node found by following `$ref`s: its [ctx], the last segment it was named by and a unique [id]. */
internal class Resolved(val value: Any?, val ctx: RefCtx, val name: String?, val id: String?)

/**
 * Loads the files a spec refers to with relative `$ref`s through a [DocsFileReader]. Paths stay relative to the
 * project, `http(s)` and absolute references are never loaded, and a file is read once. Without a reader every
 * file reference is unresolved. The files read are kept in [read] (they go into the page header) and the
 * relative references that could not be read in [problems].
 */
internal class SpecRefs(private val reader: DocsFileReader?, private val kind: SourceKind) {

    private val cache = linkedMapOf<String, ResolvedNode?>()
    private val contents = linkedMapOf<String, String>()
    private val failures = linkedSetOf<String>()

    /** The files that were read, in the order they were first needed. */
    val read: List<DocsSource> get() = contents.map { (path, text) -> DocsSource(kind, path, text) }

    val problems: List<String> get() = failures.toList()

    val resolver = RefResolver { ref, base -> load(ref, base) }

    fun load(ref: String, base: String): ResolvedNode? {
        if (reader == null || ref.contains("://") || ref.startsWith("/") || ref.startsWith("\\") || ref.getOrNull(1) == ':') return null
        val path = normalize(base.substringBeforeLast('/', "").let { if (it.isEmpty()) ref else "$it/$ref" })
        if (cache.containsKey(path)) return cache[path]
        val text = reader.read(path)
        val node = if (text == null) {
            failures += "referenced file $path not found"
            null
        } else {
            val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
            contents[path] = normalized
            try {
                ResolvedNode(Yaml.readAny(normalized), path)
            } catch (e: Exception) {
                failures += "cannot read referenced file $path: ${e.message?.lineSequence()?.firstOrNull() ?: e::class.simpleName}"
                null
            }
        }
        cache[path] = node
        return node
    }

    /**
     * Follows `$ref`s from [value] until a node that is not a reference. Null when a reference cannot be
     * resolved (remote, missing file, missing pointer, or a loop); [failed] then has the `$ref` that stopped it.
     */
    fun deref(value: Any?, ctx: RefCtx, failed: ((String) -> Unit)? = null): Resolved? {
        var v = value
        var c = ctx
        var name: String? = null
        var id: String? = null
        var hops = 0
        while (true) {
            val ref = (v as? Map<*, *>)?.get("\$ref") as? String ?: return Resolved(v, c, name, id)
            if (++hops > 20) { failed?.invoke(ref); return null }
            val file = ref.substringBefore('#')
            val pointer = if ('#' in ref) ref.substringAfter('#') else ""
            var target = c
            if (file.isNotEmpty()) {
                val node = load(file, c.base)
                if (node == null) { failed?.invoke(ref); return null }
                target = RefCtx(node.document, node.base)
            }
            val next = pointer(target.document, pointer)
            if (next == null) { failed?.invoke(ref); return null }
            v = next
            c = target
            name = pointerName(pointer, file)
            id = "${target.base}#$pointer"
        }
    }

    private fun pointer(document: Any?, pointer: String): Any? {
        var node = document
        for (raw in pointer.split('/').filter { it.isNotEmpty() }) {
            val key = raw.replace("~1", "/").replace("~0", "~")
            node = when (node) {
                is Map<*, *> -> node[key]
                is List<*> -> key.toIntOrNull()?.let { node.getOrNull(it) }
                else -> null
            } ?: return null
        }
        return node
    }

    private fun pointerName(pointer: String, file: String): String =
        (if (pointer.isNotEmpty()) pointer.substringAfterLast('/') else file.substringAfterLast('/').substringBeforeLast('.'))
            .replace("~1", "/").replace("~0", "~")

    companion object {
        /** Collapses `.` and inner `..` segments; leading `..` stay. */
        fun normalize(path: String): String {
            val out = mutableListOf<String>()
            for (segment in path.replace('\\', '/').split('/')) {
                when {
                    segment.isEmpty() || segment == "." -> {}
                    segment == ".." -> if (out.isEmpty() || out.last() == "..") out += segment else out.removeAt(out.size - 1)
                    else -> out += segment
                }
            }
            return out.joinToString("/")
        }
    }
}
