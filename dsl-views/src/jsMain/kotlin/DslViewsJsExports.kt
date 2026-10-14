import io.zenwave360.language.zdl.application.GenerateMermaidFromZdl
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Generates a Mermaid `classDiagram` for ZDL content: aggregates, entities with their fields,
 * enums, inputs, outputs, events, services with their commands, and relationships.
 * The result is inert Mermaid text (no click, link or callback directives).
 *
 * Usage:
 * ```javascript
 * const mermaid = generateMermaidFromZdl(zdlContent); // starts with "classDiagram"
 * ```
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
fun generateMermaidFromZdl(input: String): String = GenerateMermaidFromZdl().execute(input)
