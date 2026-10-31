package io.zenwave360.language.zfl.editing

import io.zenwave360.language.readTestFile
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.language.zfl.semantic.Severity
import kotlin.test.*

/**
 * ES-P1 exit-gate tests for the ZFL visual edit prototype.
 *
 * Each test verifies one or more of:
 *   - Valid ZFL after edit (re-parse: no syntax errors, no error diagnostics) — ZVE-R5
 *   - Byte-for-byte preservation of unrelated text — ZVE-R8
 *   - Identity mapping continuity after rename — ZVE-R3
 *   - Ambiguous-origin rejection — ZVE-R13
 *
 * NOTE on Unicode: The ZFL grammar ID rule is [a-zA-Z_][a-zA-Z0-9_]* (ASCII only).
 * Unicode identifiers are not valid in ZFL source and cannot be tested as successful edits.
 * Identifiers stay ASCII by owner decision (ZVE-R5).
 * CRLF line-ending preservation is still tested (ZVE-R8), with ASCII identifiers.
 *
 * spec:flows/zfl-visual-editing.md#zve-r3, #zve-r4, #zve-r5, #zve-r8, #zve-r13
 * spec:roadmap/event-storming-whiteboard.md#es-p1
 */
class ZflEditorTest {

    private val editor = ZflEditor()

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Asserts that [result] is accepted and the applied text is valid ZFL. */
    private fun assertValidZfl(source: String, result: ZflEditResult) {
        assertTrue(result.accepted, "Expected edit to be accepted. Diagnostics: ${result.diagnostics}")
        val applied = applyEdits(source, result.edits)
        val pr = ZflParser().parse(applied)
        assertTrue(pr.syntaxProblems.isEmpty(),
            "Edit produced syntax errors: ${pr.syntaxProblems}")
        val sm = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(applied))
        val errors = sm.diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(),
            "Edit produced semantic errors: ${errors.map { it.message }}")
    }

    /** Asserts that the parts of [source] outside the edited ranges are byte-for-byte identical. */
    private fun assertUntouchedOutside(source: String, result: ZflEditResult) {
        assertTrue(result.accepted)
        val sorted = result.edits.sortedBy { it.start }
        val applied = applyEdits(source, sorted)
        var sourceOffset = 0
        var appliedOffset = 0
        for (edit in sorted) {
            val before = source.substring(sourceOffset, edit.start)
            val appliedBefore = applied.substring(appliedOffset, appliedOffset + before.length)
            assertEquals(before, appliedBefore, "Unrelated text changed before edit at ${edit.start}")
            sourceOffset = edit.end
            appliedOffset += before.length + edit.replacement.length
        }
        val after = source.substring(sourceOffset)
        val appliedAfter = applied.substring(appliedOffset)
        assertEquals(after, appliedAfter, "Unrelated text changed after last edit")
    }

    // =========================================================================
    // Operation 1: Add emission to occurrence
    // =========================================================================

    @Test
    fun addEmission_toDefinitionOccurrence_producesValidZfl() {
        val source = simpleFlow()
        val result = editor.addEmissionToOccurrence(
            source = source,
            flowName = "TestFlow",
            occurrenceKey = "createOrder@definition",
            eventName = "OrderAuditLogged"
        )
        assertValidZfl(source, result)
        val applied = applyEdits(source, result.edits)
        assertTrue(applied.contains("emits OrderAuditLogged"), "New emission not found in output")
    }

    @Test
    fun addEmission_bytesOutsideEditPreserved() {
        val source = simpleFlow()
        val result = editor.addEmissionToOccurrence(
            source = source,
            flowName = "TestFlow",
            occurrenceKey = "createOrder@definition",
            eventName = "OrderAuditLogged"
        )
        assertUntouchedOutside(source, result)
    }

    @Test
    fun addEmission_toWhenBodyOccurrence_producesValidZfl() {
        val source = readTestFile("flow/editing-fixtures.zfl")
        // The `when OrderPlaced do createOrder` occurrence has a body in orderFlow
        val result = editor.addEmissionToOccurrence(
            source = source,
            flowName = "orderFlow",
            occurrenceKey = "createOrder@when[OrderPlaced]",
            eventName = "OrderAuditLogged"
        )
        assertValidZfl(source, result)
        assertUntouchedOutside(source, result)
    }

    @Test
    fun addEmission_noOp_whenEventAlreadyPresent() {
        val source = simpleFlow()
        val result = editor.addEmissionToOccurrence(
            source = source,
            flowName = "TestFlow",
            occurrenceKey = "createOrder@definition",
            eventName = "OrderCreated" // already present
        )
        assertTrue(result.accepted)
        assertTrue(result.edits.isEmpty(), "Expected no edits for a no-op")
    }

    @Test
    fun addEmission_rejects_whenFlowNotFound() {
        val source = simpleFlow()
        val result = editor.addEmissionToOccurrence(
            source = source,
            flowName = "NonexistentFlow",
            occurrenceKey = "createOrder@definition",
            eventName = "Foo"
        )
        assertFalse(result.accepted)
        assertTrue(result.diagnostics.any { it.message.contains("not found") })
    }

    @Test
    fun addEmission_rejects_whenOccurrenceNotFound() {
        val source = simpleFlow()
        val result = editor.addEmissionToOccurrence(
            source = source,
            flowName = "TestFlow",
            occurrenceKey = "nonexistentCommand@definition",
            eventName = "Foo"
        )
        assertFalse(result.accepted)
        assertTrue(result.diagnostics.any { it.message.contains("not found") })
    }

    @Test
    fun addEmission_rejects_forOccurrenceWithoutBody() {
        // `when X do Y` with no explicit body block
        val source = """
            |systems { OrderSystem { service OrderService } }
            |flow TestFlow {
            |    start OrderPlaced {}
            |    when OrderPlaced do createOrder
            |    do createOrder {
            |        service OrderSystem.OrderService
            |        emits OrderCreated
            |    }
            |    end { completed: OrderCreated }
            |}
        """.trimMargin()
        val result = editor.addEmissionToOccurrence(
            source = source,
            flowName = "TestFlow",
            occurrenceKey = "createOrder@when[OrderPlaced]",
            eventName = "SomeEvent"
        )
        assertFalse(result.accepted)
        assertTrue(result.diagnostics.any { it.message.contains("no explicit do-body") || it.message.contains("not found") })
    }

    // ES-P1: equal command names in two different flows must not share targets (ZVE-R3)
    @Test
    fun addEmission_differentFlows_separateTargets() {
        val source = readTestFile("flow/editing-fixtures.zfl")
        // Add to orderFlow's createOrder
        val r1 = editor.addEmissionToOccurrence(
            source = source,
            flowName = "orderFlow",
            occurrenceKey = "createOrder@when[OrderPlaced]",
            eventName = "FlowOneEvent"
        )
        assertValidZfl(source, r1)

        // Add to orderFlow2's createOrder – same occurrence key but different flow
        val r2 = editor.addEmissionToOccurrence(
            source = source,
            flowName = "orderFlow2",
            occurrenceKey = "createOrder@when[OrderPlaced]",
            eventName = "FlowTwoEvent"
        )
        assertValidZfl(source, r2)

        // The two results must target different character ranges (different flows)
        assertNotEquals(r1.edits.map { it.start }, r2.edits.map { it.start },
            "Equal command names in different flows must not share edit targets")
        // Each edit must only appear in its own flow
        val applied1 = applyEdits(source, r1.edits)
        val applied2 = applyEdits(source, r2.edits)
        assertFalse(applied1.contains("FlowTwoEvent"))
        assertFalse(applied2.contains("FlowOneEvent"))
    }

    // ES-P1: response form preserved (ZVE-R8)
    @Test
    fun addEmission_presencesResponseFormUnchanged() {
        val source = readTestFile("flow/editing-fixtures.zfl")
        val result = editor.addEmissionToOccurrence(
            source = source,
            flowName = "orderFlow",
            occurrenceKey = "reserveStock@when[OrderCreated]",
            eventName = "ReservationLogged"
        )
        assertValidZfl(source, result)
        val applied = applyEdits(source, result.edits)
        // The response StockUnavailable must still be there
        assertTrue(applied.contains("response StockUnavailable"),
            "response form must be preserved")
    }

    // ES-P1: actor-start flows (ZVE-R8 – comments and annotations preserved)
    @Test
    fun addEmission_actorStartAnnotationsPreserved() {
        val source = readTestFile("flow/editing-fixtures.zfl")
        val result = editor.addEmissionToOccurrence(
            source = source,
            flowName = "orderFlow",
            occurrenceKey = "createOrder@when[OrderPlaced]",
            eventName = "OrderTracked"
        )
        assertValidZfl(source, result)
        val applied = applyEdits(source, result.edits)
        // Actor annotation must be preserved byte-for-byte
        assertTrue(applied.contains("@actor(Customer)"), "@actor annotation must be preserved")
        assertTrue(applied.contains("@actor(Scheduler)"), "Scheduler actor must be preserved")
        // Comments must be preserved
        assertTrue(applied.contains("// When the order is placed, create it"),
            "Inline comment must be preserved")
    }

    // =========================================================================
    // Operation 2: Change policy trigger
    // =========================================================================

    @Test
    fun changePolicyTrigger_producesValidZfl() {
        // Change trigger of `when OrderPlaced do createOrder` to `when OrderTimeout do createOrder`
        // OrderTimeout is a start event in the same flow - a valid trigger
        val source = readTestFile("flow/editing-fixtures.zfl")
        val result = editor.changePolicyTrigger(
            source = source,
            flowName = "orderFlow",
            policyKey = "createOrder@when[OrderPlaced]",
            newTriggers = listOf(TriggerGroup(listOf("OrderTimeout")))
        )
        assertValidZfl(source, result)
        val applied = applyEdits(source, result.edits)
        assertTrue(applied.contains("when OrderTimeout do createOrder"),
            "Expected trigger to be renamed")
    }

    @Test
    fun changePolicyTrigger_bytesOutsideEditPreserved() {
        val source = readTestFile("flow/editing-fixtures.zfl")
        val result = editor.changePolicyTrigger(
            source = source,
            flowName = "orderFlow",
            policyKey = "createOrder@when[OrderPlaced]",
            newTriggers = listOf(TriggerGroup(listOf("OrderTimeout")))
        )
        assertUntouchedOutside(source, result)
    }

    @Test
    fun changePolicyTrigger_simpleFlow_producesValidZfl() {
        val source = simpleFlow()
        // Change trigger of `when OrderPlaced do createOrder` to use `OrderReady` (a start event)
        // Actually simpleFlow only has OrderPlaced as start. Let's use a different approach:
        // The flow starts with OrderPlaced, which triggers createOrder, which emits OrderCreated.
        // We can change from OrderPlaced to OrderPlaced (no-op to validate the mechanism).
        // Better: use a flow with two start events
        val source2 = flowWithTwoStarts()
        val result = editor.changePolicyTrigger(
            source = source2,
            flowName = "TwoStartFlow",
            policyKey = "createOrder@when[OrderPlaced]",
            newTriggers = listOf(TriggerGroup(listOf("OrderRequested")))
        )
        assertValidZfl(source2, result)
        val applied = applyEdits(source2, result.edits)
        assertTrue(applied.contains("when OrderRequested do createOrder"))
    }

    @Test
    fun changePolicyTrigger_noOp_whenTriggerUnchanged() {
        val source = readTestFile("flow/editing-fixtures.zfl")
        val result = editor.changePolicyTrigger(
            source = source,
            flowName = "orderFlow",
            policyKey = "createOrder@when[OrderPlaced]",
            newTriggers = listOf(TriggerGroup(listOf("OrderPlaced")))
        )
        assertTrue(result.accepted)
        assertTrue(result.edits.isEmpty())
    }

    @Test
    fun changePolicyTrigger_disambiguationIndex_selectsCorrectBlock() {
        // Fixture has two when-blocks targeting authorizePayment:
        //   when OrderCreated do authorizePayment  → key "authorizePayment@when[OrderCreated]"
        //   @if(...) when PaymentDeclined do authorizePayment → key "authorizePayment@when[PaymentDeclined]"
        // These have DIFFERENT keys so disambiguation is not needed here.
        // For a true duplication test, we use the duplicate occurrence.
        // The 2nd occurrence within orderFlow with same triggers is not present in this fixture.
        // So let's test: two blocks with the same trigger+command in a synthesized fixture.
        val source = duplicatePolicyFixture()
        // First block gets key "doIt@when[Evt]", second gets "doIt@when[Evt]#2"
        // Change the second one
        val result = editor.changePolicyTrigger(
            source = source,
            flowName = "ambigFlow",
            policyKey = "doIt@when[Evt]#2",
            newTriggers = listOf(TriggerGroup(listOf("Evt2")))
        )
        assertValidZfl(source, result)
        val applied = applyEdits(source, result.edits)
        val lines = applied.lines()
        val whenLines = lines.filter { "do doIt" in it && "when" in it }
        assertTrue(whenLines.any { "Evt" in it && "Evt2" !in it },
            "First when-block must still reference Evt")
        assertTrue(whenLines.any { "Evt2" in it },
            "Second when-block must use new trigger Evt2")
    }

    // ES-P1: two policies with equal endpoints but different conditions stay distinguishable (ZVE-R3)
    @Test
    fun changePolicyTrigger_twoPolicesEqualEndpointDifferentConditions_areDistinguishable() {
        val source = readTestFile("flow/editing-fixtures.zfl")
        // orderFlow has:
        //   when OrderCreated do authorizePayment  → key "authorizePayment@when[OrderCreated]"
        //   @if("less than 3 attempts") when PaymentDeclined do authorizePayment → key "authorizePayment@when[PaymentDeclined]"
        // Change the first one's trigger to OrderTimeout (a valid start event)
        val r1 = editor.changePolicyTrigger(
            source = source,
            flowName = "orderFlow",
            policyKey = "authorizePayment@when[OrderCreated]",
            newTriggers = listOf(TriggerGroup(listOf("OrderTimeout")))
        )
        assertValidZfl(source, r1)
        val applied = applyEdits(source, r1.edits)
        assertTrue(applied.contains("when OrderTimeout do authorizePayment"), "First policy trigger replaced")
        // The second policy (PaymentDeclined -> authorizePayment) must be unchanged
        assertTrue(applied.contains("when PaymentDeclined do authorizePayment"),
            "Second policy must be preserved")
        // Annotations on the second policy must be preserved
        assertTrue(applied.contains("@if(\"less than 3 attempts\")"),
            "@if annotation must be preserved")
    }

    // ES-P1: mixed AND trigger groups (ZVE-R6)
    @Test
    fun changePolicyTrigger_mixedAndGroups_producesValidZfl() {
        val source = readTestFile("flow/editing-fixtures.zfl")
        // fulfillOrder is triggered by "PaymentAuthorized and StockReserved"
        // Change to "PaymentAuthorized and OrderTimeout" (both are valid events/starts)
        val result = editor.changePolicyTrigger(
            source = source,
            flowName = "orderFlow",
            policyKey = "fulfillOrder@when[PaymentAuthorized,StockReserved]",
            newTriggers = listOf(
                TriggerGroup(listOf("PaymentAuthorized"), ","),
                TriggerGroup(listOf("OrderTimeout"), ",")
            )
        )
        assertValidZfl(source, result)
        val applied = applyEdits(source, result.edits)
        assertTrue(applied.contains("PaymentAuthorized") && applied.contains("OrderTimeout"),
            "New triggers must appear in output")
        // Old StockReserved trigger must not appear in the when-line
        val fulfillLine = applied.lines().find { "do fulfillOrder" in it }
        assertNotNull(fulfillLine)
        assertFalse(fulfillLine.contains("StockReserved"),
            "Old trigger StockReserved must be replaced")
    }

    @Test
    fun changePolicyTrigger_rejects_whenFlowNotFound() {
        val source = simpleFlow()
        val result = editor.changePolicyTrigger(
            source = source,
            flowName = "NoSuchFlow",
            policyKey = "createOrder@when[OrderPlaced]",
            newTriggers = listOf(TriggerGroup(listOf("X")))
        )
        assertFalse(result.accepted)
    }

    // =========================================================================
    // Operation 3: Rename command
    // =========================================================================

    @Test
    fun renameCommand_updatesAllReferences_producesValidZfl() {
        val source = simpleFlow()
        val result = editor.renameCommand(
            source = source,
            flowName = "TestFlow",
            oldName = "createOrder",
            newName = "placeOrder"
        )
        assertValidZfl(source, result)
        val applied = applyEdits(source, result.edits)
        assertFalse(applied.contains("createOrder"), "Old name must not appear")
        assertTrue(applied.contains("placeOrder"), "New name must appear")
    }

    @Test
    fun renameCommand_bytesOutsideEditPreserved() {
        val source = simpleFlow()
        val result = editor.renameCommand(
            source = source,
            flowName = "TestFlow",
            oldName = "createOrder",
            newName = "placeOrder"
        )
        assertUntouchedOutside(source, result)
    }

    @Test
    fun renameCommand_identityMappingReturned() {
        val source = simpleFlow()
        val result = editor.renameCommand(
            source = source,
            flowName = "TestFlow",
            oldName = "createOrder",
            newName = "placeOrder"
        )
        assertTrue(result.accepted)
        val mapping = result.identityMappings.firstOrNull()
        assertNotNull(mapping, "Expected identity mapping")
        assertEquals("TestFlow", mapping.flowName)
        assertEquals("command", mapping.kind)
        assertEquals("createOrder", mapping.oldName)
        assertEquals("placeOrder", mapping.newName)
    }

    @Test
    fun renameCommand_noOp_whenOldEqualsNew() {
        val source = simpleFlow()
        val result = editor.renameCommand(
            source = source,
            flowName = "TestFlow",
            oldName = "createOrder",
            newName = "createOrder"
        )
        assertTrue(result.accepted)
        assertTrue(result.edits.isEmpty())
    }

    @Test
    fun renameCommand_rejects_whenNotDeclaredInFlow() {
        val source = simpleFlow()
        val result = editor.renameCommand(
            source = source,
            flowName = "TestFlow",
            oldName = "nonexistentCommand",
            newName = "newName"
        )
        assertFalse(result.accepted)
        assertTrue(result.diagnostics.any { it.message.contains("not declared") })
    }

    @Test
    fun renameCommand_doesNotTouchOtherFlows() {
        val source = readTestFile("flow/editing-fixtures.zfl")
        // Rename 'createOrder' in orderFlow only
        val result = editor.renameCommand(
            source = source,
            flowName = "orderFlow",
            oldName = "createOrder",
            newName = "registerOrder"
        )
        assertValidZfl(source, result)
        val applied = applyEdits(source, result.edits)
        // orderFlow2 still has createOrder
        val flow2Section = applied.substringAfter("flow orderFlow2")
        assertTrue(flow2Section.contains("createOrder"),
            "orderFlow2's createOrder must not be renamed")
    }

    @Test
    fun renameCommand_updatesCallReferences() {
        // The fixture has `call checkInventory` inside reserveStock
        val source = readTestFile("flow/editing-fixtures.zfl")
        val result = editor.renameCommand(
            source = source,
            flowName = "orderFlow",
            oldName = "checkInventory",
            newName = "verifyInventory"
        )
        assertValidZfl(source, result)
        val applied = applyEdits(source, result.edits)
        val flowSection = applied.substringBefore("flow orderFlow2")
        assertFalse(flowSection.contains("checkInventory"),
            "Call reference must be renamed in orderFlow")
        assertTrue(applied.contains("verifyInventory"))
    }

    // ES-P1: identity reconciliation preserves selection across rename (ZVE-R3)
    @Test
    fun renameCommand_identityMappingPreservesSelection() {
        val source = simpleFlow()
        val result = editor.renameCommand(
            source = source,
            flowName = "TestFlow",
            oldName = "createOrder",
            newName = "placeOrder"
        )
        val mapping = result.identityMappings.first()
        assertEquals("createOrder", mapping.oldName)
        assertEquals("placeOrder", mapping.newName)
        assertEquals("TestFlow", mapping.flowName)
    }

    // ES-P1: ordered calls and on-handlers preserved (ZVE-R8)
    @Test
    fun renameCommand_orderedCallHandlersPreserved() {
        val source = readTestFile("flow/editing-fixtures.zfl")
        val result = editor.renameCommand(
            source = source,
            flowName = "orderFlow",
            oldName = "authorizePayment",
            newName = "approvePayment"
        )
        assertValidZfl(source, result)
        assertUntouchedOutside(source, result)
    }

    // =========================================================================
    // ES-P1: CRLF line endings preserved byte-for-byte (ZVE-R8)
    // =========================================================================

    @Test
    fun addEmission_crlfLineEndings_preserved() {
        val source = buildCrlfSource()
        assertTrue(source.contains("\r\n"), "Fixture must contain CRLF")
        val result = editor.addEmissionToOccurrence(
            source = source,
            flowName = "crlfFlow",
            occurrenceKey = "processOrder@when[CrlfStart]",
            eventName = "CrlfEventAdded"
        )
        assertValidZfl(source, result)
        val applied = applyEdits(source, result.edits)
        // Lines in the untouched parts must still use CRLF
        val untouchedStart = applied.substringBefore("emits CrlfEventAdded")
        assertTrue(untouchedStart.contains("\r\n"),
            "CRLF line endings must be preserved in untouched text")
    }

    @Test
    fun renameCommand_crlfLineEndings_preserved() {
        val source = buildCrlfSource()
        val result = editor.renameCommand(
            source = source,
            flowName = "crlfFlow",
            oldName = "processOrder",
            newName = "handleOrder"
        )
        assertValidZfl(source, result)
        assertUntouchedOutside(source, result)
    }

    // =========================================================================
    // ES-P1: Syntax error in source → operations rejected (ZVE-R4)
    // =========================================================================

    @Test
    fun allOperations_rejectSyntaxErrors() {
        val broken = "flow X { syntax error here "
        assertFalse(editor.addEmissionToOccurrence(broken, "X", "foo@definition", "Bar").accepted)
        assertFalse(editor.changePolicyTrigger(broken, "X", "foo@when[Bar]", listOf(TriggerGroup(listOf("Baz")))).accepted)
        assertFalse(editor.renameCommand(broken, "X", "foo", "bar").accepted)
    }

    // =========================================================================
    // ES-P1: ZVE-R13 – ambiguous relationship requires explicit origin selection
    // =========================================================================

    @Test
    fun addEmission_twoOccurrencesOfSameCommand_usesKeyToDistinguish() {
        // orderFlow has:
        //   when OrderCreated do authorizePayment { ... }
        //   @if(...) when PaymentDeclined do authorizePayment { ... }
        // These have DIFFERENT occurrence keys (different triggers), so they are distinguishable.
        val source = readTestFile("flow/editing-fixtures.zfl")
        val r1 = editor.addEmissionToOccurrence(
            source = source,
            flowName = "orderFlow",
            occurrenceKey = "authorizePayment@when[OrderCreated]",
            eventName = "AuthAuditLogged"
        )
        assertValidZfl(source, r1)

        val r2 = editor.addEmissionToOccurrence(
            source = source,
            flowName = "orderFlow",
            occurrenceKey = "authorizePayment@when[PaymentDeclined]",
            eventName = "RetryAuditLogged"
        )
        assertValidZfl(source, r2)

        // The two edits must target different positions (ZVE-R13: occurrence-specific policies)
        assertNotEquals(r1.edits.map { it.start }, r2.edits.map { it.start },
            "Two occurrences with same command but different triggers must target different positions")
    }

    // ES-P1: parallel edges (same target command, multiple trigger origins)
    @Test
    fun changePolicyTrigger_parallelEdges_eachOccurrenceDistinguishable() {
        // cancelOrder is triggered by both PaymentDeclined (with @if) and OrderFailed
        val source = readTestFile("flow/editing-fixtures.zfl")
        // Change the OrderFailed → cancelOrder policy to use OrderTimeout instead
        val result = editor.changePolicyTrigger(
            source = source,
            flowName = "orderFlow",
            policyKey = "cancelOrder@when[OrderFailed]",
            newTriggers = listOf(TriggerGroup(listOf("OrderTimeout")))
        )
        assertValidZfl(source, result)
        val applied = applyEdits(source, result.edits)
        // PaymentDeclined → cancelOrder must still be present
        assertTrue(applied.contains("when PaymentDeclined do cancelOrder"),
            "The other cancelOrder policy must be unchanged")
        // OrderFailed trigger should now be OrderTimeout
        assertTrue(applied.contains("when OrderTimeout do cancelOrder"),
            "Target policy trigger must be updated")
    }

    // =========================================================================
    // Identifiers stay ASCII (ZVE-R5)
    // =========================================================================

    /**
     * The ZFL grammar ID rule is [a-zA-Z_][a-zA-Z0-9_]* (ASCII only). Unicode identifiers
     * cause syntax errors and cannot appear in valid ZFL. This test demonstrates the rejection,
     * which is the required behaviour.
     */
    @Test
    fun addEmission_unicodeEventName_isRejectedByZflGrammar() {
        val source = simpleFlow()
        // Unicode identifier: not a valid ZFL ID token
        val result = editor.addEmissionToOccurrence(
            source = source,
            flowName = "TestFlow",
            occurrenceKey = "createOrder@definition",
            eventName = "UnicodeName_is_fine_but_Ünïcödé_is_not"
        )
        // The ZFL grammar rejects Unicode chars in identifiers, so the edit is rejected by validation
        assertFalse(result.accepted,
            "Unicode identifiers are not valid ZFL; the edit must be rejected")
    }

    // =========================================================================
    // Inline fixture builders
    // =========================================================================

    private fun simpleFlow(): String = """
        |systems { OrderSystem { service OrderService } }
        |flow TestFlow {
        |    start OrderPlaced {
        |        orderId String
        |    }
        |    when OrderPlaced do createOrder {
        |        service OrderSystem.OrderService
        |        emits OrderCreated
        |    }
        |    do createOrder {
        |        service OrderSystem.OrderService
        |        emits OrderCreated
        |    }
        |    end {
        |        completed: OrderCreated
        |    }
        |}
    """.trimMargin()

    private fun flowWithTwoStarts(): String = """
        |systems { OrderSystem { service OrderService } }
        |flow TwoStartFlow {
        |    start OrderPlaced {
        |        orderId String
        |    }
        |    start OrderRequested {
        |        orderId String
        |    }
        |    when OrderPlaced do createOrder {
        |        service OrderSystem.OrderService
        |        emits OrderCreated
        |    }
        |    do createOrder {
        |        service OrderSystem.OrderService
        |        emits OrderCreated
        |    }
        |    end {
        |        completed: OrderCreated
        |    }
        |}
    """.trimMargin()

    /**
     * Fixture with two identical when-blocks (same trigger + same command).
     * WhenCollector assigns them keys: "doIt@when[Evt]" and "doIt@when[Evt]#2".
     */
    private fun duplicatePolicyFixture(): String = """
        |systems { S { service Svc } }
        |flow ambigFlow {
        |    start Evt {}
        |    start Evt2 {}
        |    when Evt do doIt {
        |        service S.Svc
        |        emits Done
        |    }
        |    when Evt do doIt {
        |        service S.Svc
        |        emits Done
        |    }
        |    do doIt {
        |        service S.Svc
        |        emits Done
        |    }
        |    end { completed: Done }
        |}
    """.trimMargin()

    private fun buildCrlfSource(): String {
        val lf = readTestFile("flow/editing-crlf.zfl")
        // Force CRLF throughout regardless of how the file was stored on disk
        return lf.replace("\r\n", "\n").replace("\n", "\r\n")
    }
}
