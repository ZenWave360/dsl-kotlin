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
internal class Anchors private constructor(private val resolved: Map<String, String>?) {

    constructor() : this(null)

    private val headings = mutableListOf<Pair<String?, String>>()

    /**
     * A heading line of the given [level]. [key] is what [link] looks it up by (for example
     * `type:Order`); headings that nobody links to need none.
     */
    fun heading(level: Int, text: String, key: String? = null): String {
        headings += key to text
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
        val anchors = slugs(headings.map { it.second })
        val map = linkedMapOf<String, String>()
        headings.forEachIndexed { i, (key, _) -> if (key != null && key !in map) map[key] = anchors[i] }
        return Anchors(map)
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
