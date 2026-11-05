package io.zenwave360.language.eventflow.view

import io.zenwave360.language.readViewsTestFile
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.language.zfl.semantic.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZflToMermaidDiagramsTransformerTest {

    @Test
    fun testTransform_FlowchartUsesEventFlowStylesAndResponseBeforeEvent() {
        val diagrams = ZflToMermaidDiagramsTransformer().transform(
            ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
                """
                    flow ResponseFlow {
                        @actor(Customer)
                        start Requested {}
                        when Requested do fetchStatus {
                            service Status.StatusService
                            emits response StatusReady
                        }
                        when StatusReady do persistStatus {
                            service Status.StatusService
                            emits StatusPersisted
                        }
                        end { completed: StatusPersisted }
                    }
                """.trimIndent()
            ))
        )

        val flowchart = diagrams.flowchart
        assertTrue(flowchart.contains("start_Requested([\"Customer<br/>Requested\"])"), flowchart)
        assertTrue(flowchart.contains("action_fetchStatus[\"fetchStatus\"]"), flowchart)
        assertTrue(flowchart.contains("readModel_StatusReady[\"StatusReady\"]"), flowchart)
        assertTrue(flowchart.contains("event_StatusReady[/StatusReady/]"), flowchart)
        assertTrue(flowchart.contains("policy_when_StatusReady_do_persistStatus{{\"when StatusReady do persistStatus\"}}"), flowchart)
        assertTrue(flowchart.contains("terminal_completed((\"completed\"))"), flowchart)
        assertTrue(flowchart.contains("action_fetchStatus --> readModel_StatusReady"), flowchart)
        assertTrue(flowchart.contains("readModel_StatusReady --> event_StatusReady"), flowchart)
        assertTrue(!flowchart.contains("action_fetchStatus --> event_StatusReady"), flowchart)
        assertTrue(flowchart.contains("event_StatusReady --> policy_when_StatusReady_do_persistStatus"), flowchart)
        assertTrue(flowchart.contains("classDef start fill:#475569,stroke:#1e293b,color:#ffffff"), flowchart)
        assertTrue(flowchart.contains("classDef command fill:#3b82f6,stroke:#1d4ed8,color:#ffffff"), flowchart)
        assertTrue(flowchart.contains("classDef readModel fill:#bbf7d0,stroke:#15803d,color:#0f172a"), flowchart)
        assertTrue(flowchart.contains("classDef event fill:#f97316,stroke:#c2410c,color:#ffffff"), flowchart)
        assertTrue(flowchart.contains("classDef policy fill:#a855f7,stroke:#7e22ce,color:#ffffff"), flowchart)
        assertTrue(flowchart.contains("classDef endOutcome fill:#ef4444,stroke:#b91c1c,color:#ffffff"), flowchart)
        assertHasUniqueNodeDefinitionsAndEdges(flowchart)
    }

    @Test
    fun testTransform_DefaultModeUsesAltBlocksCompatibility() {
        val zflContent = """
            flow CheckoutFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do createOrder {
                    service Orders.OrderService
                    emits OrderCreated
                }

                when OrderCreated do authorizePayment {
                    service Payments.PaymentService
                    emits PaymentAuthorized
                    emits PaymentDeclined
                }

                when PaymentAuthorized do notifySuccess {
                    service Notifications.NotificationService
                    emits ConfirmationSent
                }

                when PaymentDeclined do notifyFailure {
                    service Notifications.NotificationService
                    emits CancellationSent
                }

                end {
                    completed: ConfirmationSent
                    cancelled: CancellationSent
                }
            }
        """.trimIndent()

        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent))
        assertTrue(semanticModel.diagnostics.none { it.severity == Severity.ERROR }, semanticModel.diagnostics.toString())
        val diagrams = ZflToMermaidDiagramsTransformer().transform(semanticModel)

        assertEquals("zfl.mermaid.view@1", diagrams.schema)
        assertEquals("CheckoutFlow", diagrams.flowName)
        assertTrue(diagrams.flowchart.contains("flowchart TD"))
        assertTrue(diagrams.flowchart.contains("createOrder"))
        assertTrue(diagrams.flowchart.contains("ConfirmationSent"))
        assertHasUniqueNodeDefinitionsAndEdges(diagrams.flowchart)
        assertEquals(
            MermaidSequenceRenderMode.ALT_BLOCKS,
            diagrams.sequenceRenderMode
        )

        val completed = diagrams.sequences.single { it.endOutcome == "completed" }
        assertTrue(completed.mermaid.contains("sequenceDiagram"))
        assertTrue(completed.mermaid.contains("actor Customer"))
        assertTrue(completed.mermaid.contains("Customer->>OrderService: createOrder"))
        assertTrue(completed.mermaid.contains("PaymentService-->>NotificationService: PaymentAuthorized"))
        assertTrue(completed.mermaid.contains("Note over NotificationService: end completed"))

        val cancelled = diagrams.sequences.single { it.endOutcome == "cancelled" }
        assertTrue(cancelled.mermaid.contains("PaymentDeclined"))
        assertTrue(cancelled.mermaid.contains("end cancelled"))
    }

    @Test
    fun testTransform_AltBlocksModeMergesVariantsAtForkPoint() {
        val zflContent = """
            flow CheckoutFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do createOrder {
                    service Orders.OrderService
                    emits OrderCreated
                }

                when OrderCreated do authorizePayment {
                    service Payments.PaymentService
                    emits PaymentAuthorized
                    emits PaymentDeclined
                }

                when PaymentAuthorized do notifySuccess {
                    service Notifications.NotificationService
                    emits ConfirmationSent
                }

                when PaymentDeclined do notifyFailure {
                    service Notifications.NotificationService
                    emits CancellationSent
                }

                end {
                    done: ConfirmationSent, CancellationSent
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(
                ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)),
                MermaidSequenceRenderMode.ALT_BLOCKS
            )

        assertEquals(MermaidSequenceRenderMode.ALT_BLOCKS, diagrams.sequenceRenderMode)
        assertHasUniqueNodeDefinitionsAndEdges(diagrams.flowchart)

        val done = diagrams.sequences.single { it.endOutcome == "done" }
        assertEquals("from CheckoutStarted to done", done.title)
        assertEquals("CheckoutStarted", done.startLabel)
        assertEquals(listOf("PaymentAuthorized", "PaymentDeclined"), done.branchLabels)
        assertTrue(done.mermaid.contains("alt PaymentAuthorized"))
        assertTrue(done.mermaid.contains("else PaymentDeclined"))
        assertTrue(done.mermaid.contains("Customer->>OrderService: createOrder"))
        assertTrue(done.mermaid.contains("PaymentService-->>NotificationService: PaymentAuthorized"))
        assertTrue(done.mermaid.contains("PaymentService-->>NotificationService: PaymentDeclined"))
    }

    @Test
    fun testTransform_OutcomeAnnotatedFlowchartSynthesizesOutcomeNodes() {
        val zflContent = """
            flow CheckoutFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do authorizePayment {
                    service Payments.PaymentService
                    @outcome("authorized") emits PaymentAuthorized, OrderUpdated
                    @outcome("declined") emits PaymentDeclined
                }

                end {
                    done: PaymentAuthorized, OrderUpdated, PaymentDeclined
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        assertTrue(
            diagrams.flowchart.contains("action_authorizePayment --> outcome_authorizePayment_authorized"),
            diagrams.flowchart
        )
        assertTrue(
            diagrams.flowchart.contains("outcome_authorizePayment_authorized([\"authorized\"])"),
            diagrams.flowchart
        )
        assertTrue(diagrams.flowchart.contains("outcome_authorizePayment_authorized --> event_PaymentAuthorized"))
        assertTrue(diagrams.flowchart.contains("outcome_authorizePayment_authorized --> event_OrderUpdated"))
        assertTrue(diagrams.flowchart.contains("outcome_authorizePayment_declined([\"declined\"])"))
        assertTrue(diagrams.flowchart.contains("classDef outcomeNode"))
        assertTrue(diagrams.flowchart.contains("event_PaymentAuthorized[/PaymentAuthorized/]"))
        assertTrue(!diagrams.flowchart.contains("outcome_PaymentAuthorized[/PaymentAuthorized/]"))
        assertHasUniqueNodeDefinitionsAndEdges(diagrams.flowchart)
    }

    @Test
    fun testTransform_OutcomeAnnotatedSequencesUseOutcomeBranchLabels() {
        val zflContent = """
            flow CheckoutFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do authorizePayment {
                    service Payments.PaymentService
                    @outcome("authorized") emits PaymentAuthorized, OrderUpdated
                    @outcome("declined") emits PaymentDeclined
                }

                end {
                    done: PaymentAuthorized, OrderUpdated, PaymentDeclined
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(
                ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)),
                MermaidSequenceRenderMode.ALT_BLOCKS
            )

        val done = diagrams.sequences.single { it.endOutcome == "done" }
        assertHasUniqueNodeDefinitionsAndEdges(diagrams.flowchart)
        assertEquals(listOf("authorized", "declined"), done.branchLabels)
        assertTrue(done.mermaid.contains("alt authorized"), done.mermaid)
        assertTrue(done.mermaid.contains("else declined"), done.mermaid)
        assertTrue(done.mermaid.contains("PaymentService-->>PaymentService: PaymentAuthorized"), done.mermaid)
        assertTrue(done.mermaid.contains("PaymentService-->>PaymentService: OrderUpdated"), done.mermaid)
        assertTrue(done.mermaid.contains("PaymentService-->>PaymentService: PaymentDeclined"), done.mermaid)
    }

    @Test
    fun testTransform_OnEmitsOutcomesStayInSequencesWithoutFlowchartRoutingEdges() {
        val zflContent = """
            flow CheckoutFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do startOrderCheckout {
                    service OrdersCheckout.OrdersCheckoutService
                    call reserveStock
                    @outcome("created") on StockReserved emits OrderCreated, EventB
                    on StockConfirmed emits OrderConfirmed
                }

                do reserveStock {
                    service CatalogProducts.CatalogProductsService
                    emits StockReserved
                    emits StockConfirmed
                }

                end {
                    done: OrderCreated, EventB, OrderConfirmed
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(
                ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)),
                MermaidSequenceRenderMode.ALT_BLOCKS
            )

        assertTrue(!diagrams.flowchart.contains("|on StockReserved|"), diagrams.flowchart)
        assertTrue(!diagrams.flowchart.contains("|on StockConfirmed|"), diagrams.flowchart)

        val done = diagrams.sequences.single { it.endOutcome == "done" }
        assertTrue(done.branchLabels.contains("created"), done.branchLabels.toString())
        assertTrue(done.branchLabels.contains("StockConfirmed"), done.branchLabels.toString())
        assertTrue(done.mermaid.contains("alt created") || done.mermaid.contains("else created"), done.mermaid)
        assertTrue(done.mermaid.contains("OrderCreated"), done.mermaid)
        assertTrue(done.mermaid.contains("EventB"), done.mermaid)
        assertHasUniqueNodeDefinitionsAndEdges(diagrams.flowchart)
    }

    @Test
    fun testTransform_BookSlotFlowchartRoutesStartsThroughPoliciesWithoutDuplicateNodesOrEdges() {
        val zflContent = readViewsTestFile("flow/book-slot-flow.zfl")

        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent))
        assertTrue(semanticModel.diagnostics.none { it.severity == Severity.ERROR }, semanticModel.diagnostics.toString())
        val diagrams = ZflToMermaidDiagramsTransformer().transform(semanticModel)

        val flowchart = diagrams.flowchart
        assertTrue(flowchart.contains("start_RequestBooking([\"Citizen<br/>RequestBooking\"])"), flowchart)
        assertTrue(flowchart.contains("start_RequestAssistedBooking([\"FacilityStaff<br/>RequestAssistedBooking\"])"), flowchart)
        assertTrue(
            flowchart.contains(
                "policy_when_RequestBooking_RequestAssistedBooking_do_bookSlot{{\"when RequestBooking, RequestAssistedBooking do bookSlot\"}}"
            ),
            flowchart
        )
        assertTrue(
            flowchart.contains(
                "start_RequestBooking --> policy_when_RequestBooking_RequestAssistedBooking_do_bookSlot"
            ),
            flowchart
        )
        assertTrue(
            flowchart.contains(
                "start_RequestAssistedBooking --> policy_when_RequestBooking_RequestAssistedBooking_do_bookSlot"
            ),
            flowchart
        )
        assertTrue(
            flowchart.contains(
                "policy_when_RequestBooking_RequestAssistedBooking_do_bookSlot --> action_bookSlot"
            ),
            flowchart
        )
        assertTrue(!flowchart.contains("start_RequestBooking --> action_bookSlot"), flowchart)
        assertTrue(!flowchart.contains("start_RequestAssistedBooking --> action_bookSlot"), flowchart)
        assertTrue(!flowchart.contains("action_bookSlot --> event_BookingConfirmed"), flowchart)
        assertTrue(!flowchart.contains("action_sendBookingConfirmation --> event_BookingConfirmationSent"), flowchart)
        assertTrue(!flowchart.contains("action_sendBookingConfirmation --> event_BookingConfirmationSkipped"), flowchart)
        assertTrue(!flowchart.contains("action_sendBookingConfirmation --> event_BookingConfirmationFailed"), flowchart)
        assertTrue(flowchart.contains("action_bookSlot --> outcome_bookSlot_confirmed"), flowchart)
        assertTrue(flowchart.contains("outcome_bookSlot_confirmed --> readModel_BookingConfirmed"), flowchart)
        assertTrue(flowchart.contains("readModel_BookingConfirmed --> event_BookingConfirmed"), flowchart)
        assertTrue(flowchart.contains("action_bookSlot --> outcome_bookSlot_policy_violated"), flowchart)
        assertTrue(flowchart.contains("outcome_bookSlot_policy_violated --> readModel_BookingRejected"), flowchart)
        assertTrue(flowchart.contains("action_bookSlot --> outcome_bookSlot_slot_full"), flowchart)
        assertTrue(flowchart.contains("outcome_bookSlot_slot_full --> readModel_SlotFull"), flowchart)
        assertTrue(!flowchart.contains("|on SlotNotBookable|"), flowchart)
        assertTrue(!flowchart.contains("|on HolderNotEligible|"), flowchart)
        val slotNotBookable = diagrams.sequences.filter { it.endOutcome == "slotNotBookable" }
        assertTrue(slotNotBookable.isNotEmpty())
        assertTrue(slotNotBookable.none { it.mermaid.contains("checkBookingHolder") }, slotNotBookable.toString())
        assertHasUniqueNodeDefinitionsAndEdges(flowchart)
    }

    @Test
    fun testTransform_StartHandlingPolicyConnectsToItsCommand() {
        val zflContent = readViewsTestFile("flow/booking-reminder-flow.zfl")
        val semanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent))
        assertTrue(semanticModel.diagnostics.none { it.severity == Severity.ERROR }, semanticModel.diagnostics.toString())

        val flowchart = ZflToMermaidDiagramsTransformer().transform(semanticModel).flowchart

        assertTrue(
            flowchart.contains("policy_when_BookingReminderDue_do_sendBookingReminder --> action_sendBookingReminder"),
            flowchart
        )
        assertHasUniqueNodeDefinitionsAndEdges(flowchart)
    }

    @Test
    fun testTransform_PlaceOrderFlow_DefaultModeSplitsScenarioFamiliesBeforeMerging() {
        val zflContent = readViewsTestFile("flow/place-order-flow.zfl")

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        val orderCancelled = diagrams.sequences.filter { it.endOutcome == "orderCancelled" }
        assertHasUniqueNodeDefinitionsAndEdges(diagrams.flowchart)
        assertTrue(
            orderCancelled.size >= 2,
            orderCancelled.joinToString("\n---\n") { "${it.title}\n${it.mermaid}" }
        )
        assertTrue(
            orderCancelled.any { it.mermaid.contains("alt declined") || it.mermaid.contains("else declined") },
            orderCancelled.joinToString("\n---\n") { "${it.title}\n${it.mermaid}" }
        )
        assertTrue(orderCancelled.any { it.title == "from ReservationExpired to orderCancelled" })
    }

    @Test
    fun testTransform_AutoModeUsesAltBlocksForMultipleVariants() {
        val zflContent = readViewsTestFile("flow/place-order-flow.zfl")

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(
                ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)),
                MermaidSequenceRenderMode.AUTO
            )

        val orderCancelled = diagrams.sequences.filter { it.endOutcome == "orderCancelled" }
        assertHasUniqueNodeDefinitionsAndEdges(diagrams.flowchart)
        assertTrue(
            orderCancelled.any { it.mermaid.contains("alt declined") || it.mermaid.contains("else declined") },
            orderCancelled.joinToString("\n---\n") { "${it.title}\n${it.mermaid}" }
        )
    }

    @Test
    fun testTransform_SeparateVariantsModeUsesForkLabelsInMetadata() {
        val zflContent = """
            flow CheckoutFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do createOrder {
                    service Orders.OrderService
                    emits OrderCreated
                }

                when OrderCreated do authorizePayment {
                    service Payments.PaymentService
                    emits PaymentAuthorized
                    emits PaymentDeclined
                }

                end {
                    done: PaymentAuthorized, PaymentDeclined
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(
                ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)),
                MermaidSequenceRenderMode.SEPARATE_VARIANTS
            )

        val done = diagrams.sequences.filter { it.endOutcome == "done" }
        assertHasUniqueNodeDefinitionsAndEdges(diagrams.flowchart)
        assertEquals(2, done.size)
        assertTrue(done.any { it.title == "from CheckoutStarted → PaymentAuthorized to done" })
        assertTrue(done.any { it.title == "from CheckoutStarted → PaymentDeclined to done" })
        assertTrue(done.any { it.branchLabels == listOf("PaymentAuthorized") })
        assertTrue(done.any { it.branchLabels == listOf("PaymentDeclined") })
    }

    @Test
    fun testTransform_AsyncCallRendersCommandAndEmittedContinuation() {
        val zflContent = """
            flow CheckoutFlow {
                @actor(Customer)
                start CheckoutStarted {
                }

                when CheckoutStarted do createOrder {
                    service Orders.OrderService
                    emits OrderCreated
                }

                when OrderCreated do authorizePayment {
                    service Payments.PaymentService
                    async call authorizePayment
                    emits PaymentAuthorized
                }

                when PaymentAuthorized do confirmOrder {
                    service Orders.OrderService
                    emits OrderConfirmed
                }

                end {
                    completed: OrderConfirmed
                }
            }
        """.trimIndent()

        val diagrams = ZflToMermaidDiagramsTransformer()
            .transform(ZflSemanticAnalyzer().analyze(ZflParser().parseModel(zflContent)))

        val completed = diagrams.sequences.single { it.endOutcome == "completed" }
        assertHasUniqueNodeDefinitionsAndEdges(diagrams.flowchart)
        assertTrue(completed.mermaid.contains("PaymentService->>PaymentService: async call authorizePayment"), completed.mermaid)
        assertTrue(completed.mermaid.contains("PaymentService-->>OrderService: PaymentAuthorized"), completed.mermaid)
        assertTrue(completed.mermaid.contains("OrderService->>OrderService: confirmOrder"), completed.mermaid)
    }

    @Test
    fun testTransform_CallResponseConditionsDoNotDrawFlowchartRoutingEdges() {
        val diagrams = ZflToMermaidDiagramsTransformer().transform(
            ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
                """
                    flow TestFlow {
                        @actor(User) start Start {}
                        when Start do main { service Test.Main call first @outcome(early) on X, Happy response Rejected }
                        do first { service Test.First response X response Happy }
                        end { completed: Rejected }
                    }
                """.trimIndent()
            ))
        )

        assertTrue(diagrams.flowchart.contains("action_main --> action_first"), diagrams.flowchart)
        assertTrue(!diagrams.flowchart.contains("|on X|"), diagrams.flowchart)
        assertTrue(!diagrams.flowchart.contains("|on Happy|"), diagrams.flowchart)
        assertHasUniqueNodeDefinitionsAndEdges(diagrams.flowchart)
    }

    @Test
    fun testTransform_ChainedCallHandlersStayOnTheChainedResponsePath() {
        val model = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
            """
                flow TestFlow {
                    @actor(User) start Start {}
                    when Start do main { service Test.Main call first on X call second on W response Rejected emits Completed }
                    do first { service Test.First response X response Happy }
                    do second { service Test.Second response W response Other }
                    end { completed: Completed rejected: Rejected }
                }
            """.trimIndent()
        ))
        val paths = ZflSequencePaths(model.flows.single()).paths()

        assertTrue(paths.any { path ->
            path.endOutcome == "rejected" && path.messages.any { it.label == "X" } &&
                path.messages.any { it.label == "W" }
        }, paths.toString())
        assertTrue(
            paths.filter { path -> path.messages.any { it.label == "Happy" } }
                .none { path -> path.messages.any { it.label == "second" } },
            paths.toString()
        )
        val flowchart = ZflToMermaidDiagramsTransformer().transform(model).flowchart
        assertTrue(!flowchart.contains("|on W|"), flowchart)
        assertTrue(!flowchart.contains("outcome_main_W"), flowchart)
    }

    @Test
    fun testTransform_TerminalHandlersDoNotContinueToFollowingCalls() {
        listOf("response", "emits", "emits response").forEach { signal ->
            val model = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
                """
                    flow TestFlow {
                        @actor(User) start Start {}
                        when Start do main {
                            service Test.Main
                            call first
                            @outcome(early) on X, Happy $signal Rejected
                            call second
                            emits Completed
                        }
                        do first { service Test.First response X response Happy }
                        do second { service Test.Second response W }
                        end { completed: Completed rejected: Rejected }
                    }
                """.trimIndent()
            ))
            val paths = ZflSequencePaths(model.flows.single()).paths()

            assertTrue(paths.isNotEmpty(), signal)
            assertTrue(paths.none { path -> path.messages.any { it.label == "second" } }, "$signal: $paths")
        }
    }

    private fun assertHasUniqueNodeDefinitionsAndEdges(flowchart: String) {
        val lines = flowchart.lines().map { it.trim() }
        val nodeIds = lines.mapNotNull { line ->
            Regex("^([A-Za-z][A-Za-z0-9_]*)((?:\\[|\\{|\\())").find(line)?.groupValues?.get(1)
        }
        assertEquals(nodeIds.size, nodeIds.distinct().size, flowchart)

        val edges = lines.filter { it.contains("-->") || it.contains("-.->") }
        assertEquals(edges.size, edges.distinct().size, flowchart)
    }

    @Test
    fun testFlowchart_TerminalLinksFromTheReadModelWhenOnlyAResponseExists() {
        val flowchart = ZflToMermaidDiagramsTransformer().transform(
            ZflSemanticAnalyzer().analyze(ZflParser().parseModel(
                """
                    flow ResponseFlow {
                        @actor(Customer)
                        start Requested {}
                        when Requested do fetchStatus { response Ready }
                        end { completed: Ready }
                    }
                """.trimIndent()
            ))
        ).flowchart

        assertTrue(flowchart.contains("readModel_Ready --> terminal_completed"), flowchart)
        assertTrue(!flowchart.contains("event_Ready"), flowchart)
    }
}
