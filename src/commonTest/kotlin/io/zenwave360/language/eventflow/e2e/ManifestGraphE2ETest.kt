package io.zenwave360.language.eventflow.e2e

import io.zenwave360.language.eventflow.view.FlowEdgeType
import io.zenwave360.language.eventflow.view.FlowLayoutEngine
import io.zenwave360.language.eventflow.view.ZflToFlowGraphTransformer
import io.zenwave360.language.eventflow.view.ZflToFlowViewModelTransformer
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflCallStep
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Verifies manifest-graph call and outcome semantics through the complete public pipeline. */
class ManifestGraphE2ETest {

    @Test
    fun callsHandlersExternalAsyncActionsAndTerminalOutcomesSurviveThePipeline() {
        val semanticModel = ZflParser().parseModel(
            """
                flow CheckoutFlow {
                    @actor(Customer)
                    start CheckoutRequested {}

                    when CheckoutRequested do orchestrateCheckout {
                        service Checkout.OrchestrationService
                        call reserveStock
                        on Reserved call completeCheckout
                        @outcome(rejected) on Rejected emits response CheckoutRejected
                        async call notifyAudit
                    }

                    do reserveStock {
                        service Inventory.StockService
                        response Reserved
                        response Rejected
                    }

                    do completeCheckout {
                        service Checkout.OrchestrationService
                        emits CheckoutCompleted
                    }

                    end {
                        completed: CheckoutCompleted
                        rejected: CheckoutRejected
                    }
                }
            """.trimIndent(),
            "checkout.zfl",
        ).let { ZflSemanticAnalyzer().analyze(it) }

        assertTrue(semanticModel.diagnostics.isEmpty(), semanticModel.diagnostics.toString())
        val flow = semanticModel.flows.single()
        val orchestrate = flow.commands.single { it.name == "orchestrateCheckout" }
        val calls = orchestrate.steps.filterIsInstance<ZflCallStep>()
        assertEquals(listOf("reserveStock", "notifyAudit"), calls.map { it.action })
        assertTrue(calls.last().async)
        assertEquals("completeCheckout", calls.first().handlers.first().action)
        assertEquals(listOf("CheckoutRejected"), calls.first().handlers.last().signal?.events)
        assertEquals("rejected", calls.first().handlers.last().signal?.outcome)

        val graph = ZflToFlowGraphTransformer().transform(semanticModel)
        assertNotNull(graph.nodes.singleOrNull { it.id == "command:notifyAudit" })
        assertNotNull(graph.edges.singleOrNull {
            it.source == "command:orchestrateCheckout" &&
                it.target == "command:notifyAudit" &&
                it.label == "async"
        })
        assertNotNull(graph.edges.singleOrNull {
            it.source == "command:orchestrateCheckout" &&
                it.target == "command:completeCheckout"
        })

        val viewModel = ZflToFlowViewModelTransformer().transform(semanticModel)
            .let { FlowLayoutEngine().layout(it) }
        assertEquals(
            listOf("rejected"),
            viewModel.nodes.single { it.id == "event:CheckoutRejected" }.endOutcomeLabels,
        )
        assertTrue(viewModel.edges.any {
            it.target == "event:CheckoutRejected" && it.type == FlowEdgeType.OUTCOME_HANDLER
        })
        assertTrue(viewModel.toJsonString().contains("checkout.zfl"))
    }
}
