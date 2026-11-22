package io.zenwave360.language.zfl.lint

import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zfl.semantic.Severity
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runs both linters on the experimental Arcadia place-order flow, outside this repository.
 * Set ARCADIA_EDITIONS_DIR to the arcadia-editions checkout; without it the runner does nothing.
 */
class ArcadiaPlaceOrderFlowLintRunner {

    private val systems = mapOf(
        "OrdersCheckout" to "orders-checkout",
        "CatalogInventory" to "catalog-inventory",
        "PaymentsProcessing" to "payments-processing",
        "FulfillmentShipping" to "fulfillment-shipping",
        "NotificationsConsumer" to "notifications-consumer",
    )

    /** The modulith ZDLs, which match the flow. */
    @Test
    fun lintExperimentalPlaceOrderFlow() {
        val root = arcadia() ?: return
        lint(root, systems.mapValues { (_, name) -> root.resolve("arcadia-editions-modulith-api/$name-domain-model.zdl") })
    }

    /** The per-service ZDLs that the `@zdl` paths of the flow name: older copies. */
    @Test
    fun lintExperimentalPlaceOrderFlowAgainstZdlPaths() {
        val root = arcadia() ?: return
        lint(root, systems.mapValues { (_, name) -> root.resolve("$name-api/domain-model.zdl") })
    }

    private fun arcadia() = System.getenv("ARCADIA_EDITIONS_DIR")?.let(::File)?.takeIf { it.isDirectory }

    private fun lint(root: File, zdlFiles: Map<String, File>) {
        val flowFile = root.resolve("arcadia-editions-architecture/business-flows/place-order-flow.experimental.zfl")
        val zdls = zdlFiles.mapValues { (_, file) -> ZdlParser().parseModel(file.readText()) }
        println("== ZDLs: ${zdlFiles.values.map { it.relativeTo(root).path }}")

        val single = ZflLinter().lint(flowFile.readText(), flowFile.name)
        val cross = ZflZdlLinter().lint(single.model, zdls)
        val flow = single.model.flows.single()
        println("== view ${flow.view?.name} by ${flow.view?.keyAggregate}.${flow.view?.keyPath?.joinToString(".")}: " +
            flow.view?.members?.joinToString { it.aggregate + (if (it.many) "[]" else "") + it.path.joinToString("") { ".$it" } })
        flow.timers.forEach { println("== timer after ${it.after} \"${it.duration}\" unless ${it.unless} do ${it.command}") }
        flow.end.outcomes.forEach { o -> o.variants.forEach { println("== end ${o.name}: ${it.events} ${it.expectedStates}") } }
        println("== single-file linter (${single.diagnostics.size})")
        single.diagnostics.forEach { println("${it.severity} ${it.sourceRef}: ${it.message}") }
        println("== cross-file linter (${cross.size})")
        cross.forEach { println("${it.severity} ${it.sourceRef}: ${it.message}") }

        assertEquals(emptyList(), single.diagnostics.filter { it.severity == Severity.ERROR }.map { it.message })
    }
}
