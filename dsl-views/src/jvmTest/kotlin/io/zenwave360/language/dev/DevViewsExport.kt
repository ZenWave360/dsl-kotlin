package io.zenwave360.language.dev

import io.zenwave360.language.eventflow.application.FlowVariantViewResult
import io.zenwave360.language.eventflow.application.GenerateFlowViewFromZfl
import io.zenwave360.language.eventflow.application.GenerateMermaidFromZfl
import io.zenwave360.language.eventflow.application.GenerateServiceViewFromZfl
import io.zenwave360.language.eventflow.view.FlowLanes
import io.zenwave360.language.eventflow.view.FlowViewModel
import io.zenwave360.language.eventflow.view.MermaidSequenceRenderMode
import io.zenwave360.language.eventflow.view.ServiceViewModel
import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zdl.application.GenerateMermaidCommandDiagramFromZdl
import io.zenwave360.language.zdl.view.ZdlAggregateToMermaidClassDiagramTransformer
import io.zenwave360.language.zdl.view.ZdlDomain
import io.zenwave360.language.zdl.view.ZdlEventToMermaidClassDiagramTransformer
import io.zenwave360.language.zdl.view.ZdlLifecycleToMermaidStateDiagramTransformer
import io.zenwave360.language.zdl.view.ZdlServiceToEventStormingTransformer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.Instant
import kotlin.test.Test
import kotlin.test.fail

/**
 * Dev-loop tool, not product code: writes EventFlow bundles and Mermaid diagrams of ZFL/ZDL files to a
 * directory that a browser dev app watches. See dsl-views/DEV-VIEWS.md.
 */
class DevViewsExport {

    private class Diagram(val id: String, val title: String, val mermaid: String, val group: String? = null)

    private class Views(
        val flowGraph: FlowViewModel,
        val serviceGraph: ServiceViewModel,
        val variants: List<Pair<String, String>>,
        val variantGraphs: Map<String, FlowViewModel>,
        val laneGraphs: Map<String, FlowViewModel>,
    )

    // Same options as zenwave-lsp's VisualizationJson, so the browser sees what the IDEs see.
    private val viewModelJson = Json { encodeDefaults = true; explicitNulls = false }
    private val pretty = Json { prettyPrint = true }

    @Test
    fun export() {
        val moduleDir = Paths.get("").toAbsolutePath()
        val repoRoot = findRepoRoot(moduleDir)
        val outDir = resolveOutputDir(repoRoot)
        Files.createDirectories(outDir)
        println("[dev-views] output dir: $outDir")

        val inputs = resolveInputs(moduleDir, repoRoot)
        val failures = mutableListOf<String>()
        for (input in inputs) {
            val name = input.fileName.toString().substringBeforeLast('.')
            val errorFile = outDir.resolve("$name.error.txt")
            try {
                val text = String(Files.readAllBytes(input), StandardCharsets.UTF_8)
                when (input.fileName.toString().substringAfterLast('.')) {
                    "zfl" -> exportZfl(name, input, text, outDir)
                    else -> exportZdl(name, input, text, outDir)
                }
                Files.deleteIfExists(errorFile)
            } catch (e: Throwable) {
                val trace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
                writeAtomic(errorFile, "Input: $input\n\n$trace")
                failures += "$input: ${e::class.simpleName}: ${e.message}"
                println("[dev-views] FAILED $input (see $errorFile)")
            }
        }
        if (failures.isNotEmpty()) {
            fail("Dev views failed for ${failures.size} input(s):\n" + failures.joinToString("\n") { " - $it" })
        }
    }

    private fun exportZfl(name: String, input: Path, text: String, outDir: Path) {
        val generatedAt = Instant.now().toString()
        val flows = GenerateFlowViewFromZfl()
        val views = runBlocking {
            val flow = flows.execute(text, FlowLanes.NONE)
            val service = GenerateServiceViewFromZfl().execute(text)
            val variantList = flows.variants(text)
            val perVariant = variantList.associate { variant ->
                val view = when (val result = flows.variant(text, variant.id)) {
                    is FlowVariantViewResult.Found -> result.view
                    is FlowVariantViewResult.UnknownVariant -> error("unknown variant ${result.id}")
                }
                variant.id to view
            }
            val lanes = mapOf(
                "systems" to flows.execute(text, FlowLanes.SYSTEMS),
                "services" to flows.execute(text, FlowLanes.SERVICES),
            )
            Views(flow, service, variantList.map { it.id to it.title }, perVariant, lanes)
        }

        val mermaid = GenerateMermaidFromZfl().execute(text, MermaidSequenceRenderMode.ALT_BLOCKS)
        val diagrams = listOf(Diagram("flowchart", "Flowchart", mermaid.flowchart)) +
            mermaid.sequences.mapIndexed { index, s ->
                Diagram("sequence:${s.endOutcome}:$index", "Sequence: ${s.title.ifBlank { s.endOutcome }}", s.mermaid)
            }
        writeMmd(outDir, name, diagrams)
        val header = header(name, input, generatedAt)
        val eventFlow = JsonObject(
            header + mapOf(
                "flowGraph" to viewModelJson.encodeToJsonElement(FlowViewModel.serializer(), views.flowGraph),
                "serviceGraph" to viewModelJson.encodeToJsonElement(ServiceViewModel.serializer(), views.serviceGraph),
                "variants" to buildJsonArray {
                    views.variants.forEach { (id, title) -> add(buildJsonObject { put("id", id); put("title", title) }) }
                },
                "variantGraphs" to JsonObject(views.variantGraphs.mapValues { viewModelJson.encodeToJsonElement(FlowViewModel.serializer(), it.value) }),
                "laneGraphs" to JsonObject(views.laneGraphs.mapValues { viewModelJson.encodeToJsonElement(FlowViewModel.serializer(), it.value) }),
            )
        )
        // JSON files last, so a watcher never sees a bundle whose .mmd files are stale.
        writeAtomic(outDir.resolve("$name.mermaid.json"), pretty.encodeToString(JsonObject.serializer(), mermaidJson(header, diagrams)))
        writeAtomic(outDir.resolve("$name.eventflow.json"), pretty.encodeToString(JsonObject.serializer(), eventFlow))
        println("[dev-views] $name: ${views.variants.size} variants, ${diagrams.size} diagrams")
    }

    /** Same diagrams, ids, titles and groups as zenwave-lsp's `zdlDiagramsPreview`. */
    private fun exportZdl(name: String, input: Path, text: String, outDir: Path) {
        val generatedAt = Instant.now().toString()
        val domain = ZdlDomain(ZdlParser().parseModel(text))
        val diagrams = buildList {
            var others: String? = null
            ZdlServiceToEventStormingTransformer().transform(domain).forEach { d ->
                d.mermaid?.let { add(Diagram("service:${d.service}", d.service, it, "Services")) }
            }
            ZdlAggregateToMermaidClassDiagramTransformer().transform(domain).let { d ->
                d.aggregates.forEach { add(Diagram("aggregate:${it.aggregate}", it.aggregate, it.mermaid, "Aggregates")) }
                others = d.unused
            }
            ZdlLifecycleToMermaidStateDiagramTransformer().transform(domain).forEach {
                add(Diagram("state-machine:${it.aggregate}", it.aggregate, it.mermaid, "State Machines"))
            }
            ZdlEventToMermaidClassDiagramTransformer().transform(domain)?.let {
                add(Diagram("events", "Events", it, "Events"))
            }
            GenerateMermaidCommandDiagramFromZdl().execute(text).forEach {
                add(Diagram("command:${it.service}:${it.command}", "${it.service}.${it.command}", it.mermaid, "Commands"))
            }
            others?.let { add(Diagram("other-entities", "Unused entities and enums", it, "Unused entities and enums")) }
        }
        writeMmd(outDir, name, diagrams)
        writeAtomic(
            outDir.resolve("$name.mermaid.json"),
            pretty.encodeToString(JsonObject.serializer(), mermaidJson(header(name, input, generatedAt), diagrams)),
        )
        println("[dev-views] $name: ${diagrams.size} diagrams")
    }

    private fun header(name: String, input: Path, generatedAt: String): Map<String, JsonElement> =
        linkedMapOf(
            "name" to JsonPrimitive(name),
            "source" to JsonPrimitive(input.toString()),
            "generatedAt" to JsonPrimitive(generatedAt),
        )

    private fun mermaidJson(header: Map<String, JsonElement>, diagrams: List<Diagram>) =
        JsonObject(
            header + ("diagrams" to buildJsonArray {
                diagrams.forEach { d ->
                    add(buildJsonObject {
                        put("id", d.id)
                        put("title", d.title)
                        put("mermaid", d.mermaid)
                        d.group?.let { put("group", it) }
                    })
                }
            })
        )

    private fun writeMmd(outDir: Path, name: String, diagrams: List<Diagram>) {
        val dir = outDir.resolve(name)
        Files.createDirectories(dir)
        val wanted = diagrams.map { sanitize(it.id) + ".mmd" }.toSet()
        Files.list(dir).use { files ->
            files.filter { it.fileName.toString().endsWith(".mmd") && it.fileName.toString() !in wanted }
                .toList().forEach { Files.deleteIfExists(it) }
        }
        diagrams.forEach { writeAtomic(dir.resolve(sanitize(it.id) + ".mmd"), it.mermaid) }
    }

    private fun sanitize(id: String) = id.replace(Regex("[^A-Za-z0-9._-]+"), "_")

    private fun writeAtomic(target: Path, content: String) {
        val tmp = Files.createTempFile(target.parent, "." + target.fileName, ".tmp")
        try {
            Files.write(tmp, content.toByteArray(StandardCharsets.UTF_8))
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    private fun setting(env: String, prop: String): String? =
        (System.getenv(env) ?: System.getProperty(prop))?.takeIf { it.isNotBlank() }

    private fun findRepoRoot(start: Path): Path {
        var dir: Path? = start
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts")) || Files.exists(dir.resolve("settings.gradle"))) return dir
            dir = dir.parent
        }
        return start.parent ?: start
    }

    private fun resolveOutputDir(repoRoot: Path): Path =
        (setting("ZENWAVE_DEV_VIEWS_DIR", "zenwave.devViews.dir")?.let { Paths.get(it) } ?: repoRoot.resolve("build/dev-views"))
            .toAbsolutePath().normalize()

    private fun resolveInputs(moduleDir: Path, repoRoot: Path): List<Path> {
        val configured = setting("ZENWAVE_DEV_VIEWS_INPUT", "zenwave.devViews.input")
        val entries: List<Path> = if (configured != null) {
            configured.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { raw ->
                val path = Paths.get(raw)
                if (path.isAbsolute) path
                else listOfNotNull(moduleDir, repoRoot, repoRoot.parent).map { it.resolve(raw) }.firstOrNull { Files.exists(it) }
                    ?: fail("Input '$raw' not found relative to $moduleDir or $repoRoot")
            }
        } else {
            val resources = listOf(moduleDir.resolve("src/commonTest/resources"), repoRoot.resolve("dsl-views/src/commonTest/resources"))
                .first { Files.isDirectory(it) }
            listOf(
                resources.resolve("flow"),
                resources.resolve("arcadia-editions-modulith/orders-checkout-domain-model.zdl"),
                resources.resolve("arcadia-editions-modulith/payments-processing-domain-model.zdl"),
                resources.resolve("arcadia-editions-modulith/fulfillment-shipping-domain-model.zdl"),
            )
        }
        val files = entries.flatMap { path ->
            if (Files.isDirectory(path)) {
                Files.list(path).use { s ->
                    s.filter { it.fileName.toString().let { n -> n.endsWith(".zfl") || n.endsWith(".zdl") } }.sorted().toList()
                }
            } else listOf(path)
        }.map { it.toAbsolutePath().normalize() }
        files.forEach { println("[dev-views] input: $it") }
        if (files.isEmpty()) fail("No .zfl/.zdl inputs found")
        return files
    }
}
