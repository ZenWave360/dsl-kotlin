package io.zenwave360.language.docs.internal

/**
 * The heading anchors of one page, the way GitHub and VS Code compute them: lowercase, spaces become
 * `-`, punctuation other than `-` and `_` is dropped, and a repeated anchor gets `-1`, `-2`, ... in
 * heading order.
 *
 * A page is rendered twice. The first pass uses an empty registry: [heading] records each heading
 * and [link] returns only the label. [resolve] then gives the registry the second pass links with,
 * so a link can point to a heading that comes later on the page, and a name that appears as two
 * headings still gets the right anchor.
 */
internal class Anchors private constructor(
    private val resolved: Map<String, String>?,
    private val entries: List<TocEntry> = emptyList(),
) {

    constructor() : this(null)

    private class TocEntry(val level: Int, val text: String, val anchor: String)

    private class Registered(val level: Int, val key: String?, val text: String)

    private val headings = mutableListOf<Registered>()

    /**
     * A heading line of the given [level]. [key] is what [link] looks it up by (for example
     * `type:Order`); headings that nobody links to need none.
     */
    fun heading(level: Int, text: String, key: String? = null): String {
        headings += Registered(level, key, text)
        return "#".repeat(level) + " " + text + "\n\n"
    }

    /** True when a heading was registered under [key] (only known in the second pass). */
    fun has(key: String): Boolean = resolved?.containsKey(key) == true

    /** `[label](#anchor)` when [key] has a heading, else [label]. */
    fun link(key: String, label: String): String {
        val anchor = resolved?.get(key) ?: return label
        return "[$label](#$anchor)"
    }

    /** The registry for the second pass. A key registered twice points to the first heading. */
    fun resolve(): Anchors {
        val anchors = slugs(headings.map { it.text })
        val map = linkedMapOf<String, String>()
        headings.forEachIndexed { i, h -> if (h.key != null && h.key !in map) map[h.key] = anchors[i] }
        val entries = headings.mapIndexed { i, h -> TocEntry(h.level, h.text, anchors[i]) }
        return Anchors(map, entries)
    }

    /**
     * `## Contents` and, from the second pass on, a nested bullet list of the `##` and `###` headings of the
     * page (the Contents heading itself left out). Nothing in the second pass when the page has no such heading.
     */
    fun toc(): String {
        if (resolved == null) return heading(2, "Contents")
        val items = entries.filter { it.level in 2..3 && it.text != "Contents" }
        if (items.isEmpty()) return ""
        val list = items.joinToString("") { "${"  ".repeat(it.level - 2)}- [${it.text}](#${it.anchor})\n" }
        return heading(2, "Contents") + list + "\n"
    }

    companion object {
        /** The anchor of each heading, in order. */
        fun slugs(headings: List<String>): List<String> {
            val used = mutableSetOf<String>()
            val counts = mutableMapOf<String, Int>()
            return headings.map { text ->
                val base = Markdown.anchor(text)
                var candidate = base
                while (!used.add(candidate)) {
                    val n = (counts[base] ?: 0) + 1
                    counts[base] = n
                    candidate = "$base-$n"
                }
                candidate
            }
        }
    }
}
