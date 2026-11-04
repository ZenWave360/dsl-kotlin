package io.zenwave360.language.eventflow.e2e

import io.zenwave360.language.eventflow.view.FlowEdgeType
import io.zenwave360.language.eventflow.view.FlowLayoutEngine
import io.zenwave360.language.eventflow.view.ZflToFlowGraphTransformer
import io.zenwave360.language.eventflow.view.ZflToFlowViewModelTransformer
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflCallStep
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.language.zfl.semantic.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Verifies architecture-graph call and outcome semantics through the complete public pipeline. */
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
                        emits response Rejected
                    }

                    end {
                        completed: CheckoutCompleted
                        rejected: CheckoutRejected
                    }
                }
            """.trimIndent(),
            "checkout.zfl",
        ).let { ZflSemanticAnalyzer().analyze(it) }

        assertTrue(semanticModel.diagnostics.none { it.severity == Severity.ERROR }, semanticModel.diagnostics.toString())
        val flow = semanticModel.flows.single()
        val orchestrate = flow.commands.single { it.name == "orchestrateCheckout" }
        val calls = orchestrate.steps.filterIsInstance<ZflCallStep>()
        assertEquals(listOf("reserveStock", "notifyAudit"), calls.map { it.action })
        assertTrue(calls.last().async)
        assertEquals("completeCheckout", calls.first().handlers.first().action)
        val completeCheckout = assertNotNull(calls.first().handlers.first().chainedCall)
        assertEquals(listOf("CheckoutRejected"), completeCheckout.handlers.single().signal?.events)
        assertEquals("rejected", completeCheckout.handlers.single().signal?.outcome)

        val graph = ZflToFlowGraphTransformer().transform(semanticModel)
        assertNotNull(graph.nodes.singleOrNull { it.id == "command:notifyAudit" })
        assertNotNull(graph.edges.singleOrNull {
            it.source == "command:orchestrateCheckout" &&
                it.target == "command:notifyAudit" &&
                it.label == "async"
        })
        val viewModel = ZflToFlowViewModelTransformer().transform(semanticModel)
            .let { FlowLayoutEngine().layout(it) }
        assertEquals(
            listOf("rejected"),
            viewModel.nodes.single { it.id == "event:CheckoutRejected" }.endOutcomeLabels,
        )
        // EF-R19: a call's responses are not connected to the caller's outcomes
        assertTrue(viewModel.edges.none {
            it.target == "event:CheckoutRejected" && it.type == FlowEdgeType.OUTCOME_HANDLER
        })
        assertTrue(viewModel.toJsonString().contains("checkout.zfl"))
    }
}
