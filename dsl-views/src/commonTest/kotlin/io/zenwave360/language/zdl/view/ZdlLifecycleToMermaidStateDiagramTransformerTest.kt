package io.zenwave360.language.zdl.view

import io.zenwave360.language.readViewsTestFile
import io.zenwave360.language.zdl.ZdlParser
import kotlin.test.Test
import kotlin.test.assertEquals

class ZdlLifecycleToMermaidStateDiagramTransformerTest {

    private fun transform(zdl: String) = ZdlLifecycleToMermaidStateDiagramTransformer().transform(ZdlParser().parseModel(zdl))

    @Test
    fun creationToListFromListMergedLabelsSelfLoopAndTerminals() {
        val zdl = """
            @aggregate
            @lifecycle(field: status, initial: DRAFT)
            entity Doc {
                status DocStatus
            }
            enum DocStatus {
                DRAFT, IMPORTED, REVIEW, APPROVED, REJECTED
            }
            service DocService for (Doc) {
                @transition(to: [DRAFT, IMPORTED])
                create(CreateInput) Doc withEvents DocCreated
                @transition(from: DRAFT, to: REVIEW)
                submit(id) Doc withEvents DocSubmitted
                @transition(from: [REVIEW, DRAFT], to: [APPROVED, REJECTED])
                decide(id, DecideInput) Doc withEvents DocDecided
                @transition(from: REVIEW, to: APPROVED)
                fastApprove(id) Doc withEvents DocDecided
                @transition(from: REJECTED, to: REJECTED)
                retryReject(id) Doc withEvents DocDecided
                getDoc(id) Doc?
            }
        """.trimIndent()

        val expected = """
            stateDiagram-v2
                [*] --> DRAFT : create
                [*] --> IMPORTED : create
                DRAFT --> REVIEW : submit
                REVIEW --> APPROVED : decide, fastApprove
                REVIEW --> REJECTED : decide
                DRAFT --> APPROVED : decide
                DRAFT --> REJECTED : decide
                REJECTED --> REJECTED : retryReject
                IMPORTED --> [*]
                APPROVED --> [*]

        """.trimIndent()

        val lifecycle = transform(zdl).single()
        assertEquals(expected, lifecycle.mermaid)
        assertEquals("Doc", lifecycle.aggregate)
        assertEquals("Field `status`, initial `DRAFT`.", lifecycle.caption)
    }

    @Test
    fun initialStateWithoutCreationEdgeGetsOne() {
        val zdl = """
            @aggregate
            @lifecycle(field: state, initial: OPEN)
            entity Ticket {
                state String
            }
            service TicketService for (Ticket) {
                @transition(from: OPEN, to: CLOSED)
                close(id) Ticket withEvents TicketClosed
            }
        """.trimIndent()

        val expected = """
            stateDiagram-v2
                [*] --> OPEN
                OPEN --> CLOSED : close
                CLOSED --> [*]

        """.trimIndent()
        assertEquals(expected, transform(zdl).single().mermaid)
    }

    @Test
    fun noDiagramWithoutLifecycleOrWithoutTransitions() {
        val zdl = """
            @aggregate
            @lifecycle(field: status, initial: NEW)
            entity A {
                status String
            }
            @aggregate
            entity B {
                status String
            }
            service SB for (B) {
                @transition(to: NEW)
                make(id) B withEvents Made
            }
            service SA for (A) {
                other(id) A withEvents Done
            }
        """.trimIndent()
        assertEquals(emptyList(), transform(zdl))
    }

    @Test
    fun aggregateBlockCommandsContributeTransitions() {
        val zdl = """
            @lifecycle(field: status, initial: NEW)
            entity Job {
                status String
            }
            aggregate JobAggregate(Job) {
                @transition(from: NEW, to: DONE)
                finish(FinishInput) withEvents JobFinished
            }
        """.trimIndent()
        assertEquals(
            "stateDiagram-v2\n    [*] --> NEW\n    NEW --> DONE : finish\n    DONE --> [*]\n",
            transform(zdl).single().mermaid,
        )
    }

    @Test
    fun modulithPaymentsProcessing() {
        val expected = """
            stateDiagram-v2
                [*] --> AUTHORIZATION_REQUESTED : authorizePayment
                FAILED --> AUTHORIZATION_REQUESTED : retryPayment
                AUTHORIZED --> CAPTURED : capturePayment
                AUTHORIZED --> VOIDED : voidPayment
                AUTHORIZATION_REQUESTED --> VOIDED : voidPayment
                FAILED --> VOIDED : voidPayment
                CAPTURED --> [*]
                VOIDED --> [*]

        """.trimIndent()
        val lifecycle = transform(readViewsTestFile("arcadia-editions-modulith/payments-processing-domain-model.zdl")).single()
        assertEquals(expected, lifecycle.mermaid)
        assertEquals("Field `status`, initial `AUTHORIZATION_REQUESTED`.", lifecycle.caption)
    }
}
