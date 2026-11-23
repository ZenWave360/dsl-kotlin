package io.zenwave360.language.zdl.view

/**
 * The light look of the class and state diagrams, so they stay readable on a dark page.
 *
 * Two parts: [FRONT_MATTER] forces Mermaid's `default` (light) theme and a mid-grey line colour that
 * shows on both light and dark pages, and a per-class `style` line gives each class a white (or tinted)
 * fill with dark text. `style` is used instead of `classDef` + `cssClass` because it needs no extra
 * statements and every Mermaid 10/11 build parses it; the front matter is the YAML config block
 * (`config: theme: default`), preferred over `%%{init}%%` because it is the documented form in Mermaid 11.
 */
object MermaidLight {
    /** YAML front matter placed before the diagram type line. */
    const val FRONT_MATTER: String =
        "---\n" +
            "config:\n" +
            "  theme: default\n" +
            "  themeVariables:\n" +
            "    lineColor: \"#888888\"\n" +
            "    edgeLabelBackground: \"#ffffff\"\n" +
            "    transitionColor: \"#888888\"\n" +
            "    transitionLabelColor: \"#111111\"\n" +
            "---\n"

    /**
     * Front matter for a flowchart whose every subgraph has a light fill. Mermaid draws a subgraph title with the
     * theme's `titleColor`, not with the `color` of the subgraph's `style`, so on a dark page the title would be
     * light on light. Only the title colour changes: the theme stays the page's.
     */
    const val SUBGRAPH_TITLE_FRONT_MATTER: String =
        "---\n" +
            "config:\n" +
            "  themeVariables:\n" +
            "    titleColor: \"#111111\"\n" +
            "---\n"

    /** Mermaid's own light subgraph colours, for a subgraph that has none of its own next to filled ones. */
    const val PLAIN_BOX_STYLE: String = "fill:#ffffde,stroke:#aaaa33,color:#111"

    private const val BASE = "stroke:#333333,color:#111111"

    /** Fill by stereotype: aggregate root pink (as in the EventStorming diagram), event light orange, enum grey. */
    fun classStyle(stereotype: String?): String {
        val fill = when (stereotype) {
            "aggregate" -> "#fbd3ee"
            "event" -> "#ffe0bf"
            "enumeration" -> "#f2f2f2"
            else -> "#ffffff"
        }
        return "fill:$fill,$BASE"
    }

    /** `    style <id> fill:...` line (with newline). */
    fun styleLine(id: String, stereotype: String?): String = "    style $id ${classStyle(stereotype)}\n"
}
