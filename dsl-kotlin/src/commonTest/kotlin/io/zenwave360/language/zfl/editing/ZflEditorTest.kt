package io.zenwave360.language.zfl.editing

import io.zenwave360.language.readTestFile
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.Severity
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.language.zfl.semantic.ZflSemanticModel
import kotlin.test.*

/**
 * ES-P1 tests for the ZFL visual edit prototype.
 *
 * Accepted results are compared with the exact expected text, so a change outside the intended range
 * fails. Fixtures read from files run on the JVM and Node only; the browser smoke test separately
 * holds the inline Unicode offset case it runs.
 *
 * Identifiers stay ASCII (ZVE-R5); Unicode appears in strings and comments, including characters
 * outside the BMP, which count as two UTF-16 code units in the edit offsets.
 *
 * spec:flows/zfl-visual-editing#zve-r3
 * spec:flows/zfl-visual-editing#zve-r4
 * spec:flows/zfl-visual-editing#zve-r5
 * spec:flows/zfl-visual-editing#zve-r8
 * spec:flows/zfl-visual-editing#zve-r13
 * spec:roadmap/event-storming-whiteboard#es-p1
 */
class ZflEditorTest {

    private val editor = ZflEditor()
    private val emoji = "😀"

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Asserts that [result] is accepted and the applied text has no syntax errors or error diagnostics. */
    private fun assertValidZfl(source: String, result: ZflEditResult) {
        assertTrue(result.accepted, "Expected edit to be accepted. Diagnostics: ${result.diagnostics}")
        val applied = applyEdits(source, result.edits)
        val pr = ZflParser().parse(applied)
        assertTrue(pr.syntaxProblems.isEmpty(), "Edit produced syntax errors: ${pr.syntaxProblems}")
        val errors = analyze(applied).diagnostics.filter { it.severity == Severity.ERROR }
        assertTrue(errors.isEmpty(), "Edit produced semantic errors: ${errors.map { it.message }}")
    }

    /** Asserts that the accepted [result] turns [source] into exactly [expected], and that the edits are well formed. */
    private fun assertExact(source: String, result: ZflEditResult, expected: String) {
        assertTrue(result.accepted, "Expected edit to be accepted. Diagnostics: ${result.diagnostics}")
        assertWellFormed(source, result.edits)
        assertEquals(expected, applyEdits(source, result.edits))
        assertValidZfl(source, result)
    }

    private fun assertWellFormed(source: String, edits: List<ZflTextEdit>) {
        val sorted = edits.sortedBy { it.start }
        sorted.forEach {
            assertTrue(it.start in 0..it.end && it.end <= source.length, "Edit out of range: $it")
        }
        sorted.zipWithNext().forEach { (a, b) -> assertTrue(a.end <= b.start, "Overlapping edits: $a $b") }
    }

    private fun assertRejected(result: ZflEditResult, vararg messageParts: String) {
        assertFalse(result.accepted, "Expected a rejection")
        assertTrue(result.edits.isEmpty(), "A rejection must carry no edits")
        val message = result.diagnostics.joinToString(" | ") { it.message }
        messageParts.forEach { assertTrue(message.contains(it), "Expected '$it' in: $message") }
    }

    private fun assertNoOp(result: ZflEditResult) {
        assertTrue(result.accepted, "Expected accepted. Diagnostics: ${result.diagnostics}")
        assertTrue(result.edits.isEmpty(), "A no-op must return no edits: ${result.edits}")
    }

    /** Replaces the only occurrence of [old] in this text; fails when it is absent or repeated. */
    private fun String.replaceOnce(old: String, new: String): String {
        val first = indexOf(old)
        assertTrue(first >= 0, "Anchor not found: $old")
        assertEquals(-1, indexOf(old, first + 1), "Anchor is not unique: $old")
        return substring(0, first) + new + substring(first + old.length)
    }

    /** [replaceOnce] restricted to the section of one flow, for names that other flows of the fixture repeat. */
    private fun String.replaceOnceIn(flowName: String, old: String, new: String): String {
        val start = indexOf("flow $flowName {")
        assertTrue(start >= 0, "Flow not found: $flowName")
        val next = indexOf("\nflow ", start)
        val end = if (next < 0) length else next
        return substring(0, start) + substring(start, end).replaceOnce(old, new) + substring(end)
    }

    private fun analyze(source: String): ZflSemanticModel = ZflSemanticAnalyzer().analyze(ZflParser().parseModel(source))

    private fun fixture(): String = readTestFile("flow/editing-fixtures.zfl").replace("\r\n", "\n")

    /** A one-flow document. [body] sits between the starts and the end block. */
    private fun flow(body: String, flowName: String = "F"): String =
        "systems { S { service Svc } }\nflow $flowName {\n    start A {}\n    start B {}\n$body\n    end { completed: E }\n}\n"

    private val whenX = "    when A do x {\n        service S.Svc\n        emits E\n    }"

    // =========================================================================
    // Operation 1: Add an emission
    // =========================================================================

    @Test
    fun addEmission_multiLineBody_newLineKeepsIndentAndBraceStays() {
        val source = flow(whenX)
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
        assertExact(source, result, flow(whenX.replace("emits E\n", "emits E\n        emits N\n")))
    }

    @Test
    fun addEmission_toDefinitionOccurrence_exactOutput() {
        val source = simpleFlow()
        val result = editor.addEmissionToOccurrence(source, "TestFlow", "createOrder@definition", "OrderAuditLogged")
        val lastBody = "    do createOrder {\n        service OrderSystem.OrderService\n        emits OrderCreated\n    }"
        assertExact(source, result, source.replaceOnce(lastBody, lastBody.replace("emits OrderCreated\n", "emits OrderCreated\n        emits OrderAuditLogged\n")))
    }

    @Test
    fun addEmission_toFixtureWhenBody_exactOutput() {
        val source = fixture()
        val result = editor.addEmissionToOccurrence(source, "orderFlow", "createOrder@when[OrderPlaced]", "OrderAuditLogged")
        assertExact(source, result, source.replaceOnce(
            "        emits OrderFailed\n    }", "        emits OrderFailed\n        emits OrderAuditLogged\n    }"
        ))
    }

    @Test
    fun addEmission_oneLineBody_staysOnOneLine() {
        val source = flow("    when A do x { service S.Svc emits E }")
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
        assertExact(source, result, flow("    when A do x { service S.Svc emits E emits N }"))
    }

    @Test
    fun addEmission_emptyBodies_oneLine() {
        // An empty occurrence inherits the other occurrence's body and cannot be changed in ES-P1.
        val spaced = flow("$whenX\n    do x { }")
        assertRejected(editor.addEmissionToOccurrence(spaced, "F", "x@definition", "N"), "inherited body")
        val tight = flow("$whenX\n    do x {}")
        assertRejected(editor.addEmissionToOccurrence(tight, "F", "x@definition", "N"), "inherited body")
    }

    @Test
    fun addEmission_emptyMultiLineBody_rejectsAnInheritedBody() {
        val source = flow("$whenX\n    do x {\n    }")
        assertRejected(editor.addEmissionToOccurrence(source, "F", "x@definition", "N"), "inherited body")
    }

    @Test
    fun addEmission_tabIndentedBody_usesTheLastStatementIndent() {
        val source = flow("    when A do x {\n\t\tservice S.Svc\n\t\temits E\n    }")
        assertExact(
            source, editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N"),
            flow("    when A do x {\n\t\tservice S.Svc\n\t\temits E\n\t\temits N\n    }")
        )
    }

    @Test
    fun addEmission_crlfSource_insertsCrlfAndNoBareLf() {
        val source = flow(whenX).replace("\n", "\r\n")
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
        val applied = applyEdits(source, result.edits)
        assertEquals(flow(whenX.replace("emits E\n", "emits E\n        emits N\n")).replace("\n", "\r\n"), applied)
        assertFalse(applied.replace("\r\n", "").contains('\n'), "No bare LF may remain in a CRLF file")
        assertEquals("\r\n", result.edits.single().replacement.takeLast(2))
    }

    @Test
    fun addEmission_crlfFixtureFile_exactOutput() {
        val source = readTestFile("flow/editing-crlf.zfl").replace("\r\n", "\n").replace("\n", "\r\n")
        val result = editor.addEmissionToOccurrence(source, "crlfFlow", "processOrder@when[CrlfStart]", "CrlfEventAdded")
        val anchor = "        emits OrderProcessed\r\n    }\r\n\r\n    do processOrder"
        assertExact(source, result, source.replaceOnce(anchor, anchor.replace("OrderProcessed\r\n", "OrderProcessed\r\n        emits CrlfEventAdded\r\n")))
    }

    @Test
    fun addEmission_noOp_whenUnconditionalEmissionPresent() {
        assertNoOp(editor.addEmissionToOccurrence(simpleFlow(), "TestFlow", "createOrder@definition", "OrderCreated"))
        // `emits response` also publishes the event
        val source = flow("    when A do x {\n        service S.Svc\n        emits response E\n    }")
        assertNoOp(editor.addEmissionToOccurrence(source, "F", "x@when[A]", "E"))
    }

    @Test
    fun addEmission_retryAfterAcceptance_returnsNoEdits() {
        val source = flow(whenX)
        val first = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
        val second = editor.addEmissionToOccurrence(applyEdits(source, first.edits), "F", "x@when[A]", "N")
        assertNoOp(second)
    }

    @Test
    fun addEmission_eventOnlyAResponse_isInserted() {
        val source = flow("    when A do x {\n        service S.Svc\n        response R\n        emits E\n    }")
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "R")
        assertExact(source, result, source.replaceOnce("emits E\n", "emits E\n        emits R\n"))
        assertTrue("R" in analyze(applyEdits(source, result.edits)).flows.single().events.map { it.name })
    }

    @Test
    fun addEmission_eventOnlyAConditionalHandlerEmission_isInserted() {
        val source = flow("    when A do x {\n        service S.Svc\n        emits E\n        call y\n        on Ok emits R\n    }\n    do y {\n        service S.Svc\n        response Ok\n    }")
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "R")
        assertExact(source, result, source.replaceOnce("on Ok emits R\n", "on Ok emits R\n        emits R\n"))
    }

    @Test
    fun addEmission_annotatedDirectEmissions_doNotCountAsAlreadyEmitted() {
        for (annotation in listOf("@failure", "@if(\"c\")")) {
            val source = flow("    when A do x {\n        service S.Svc\n        emits E\n        $annotation emits N\n    }")
            val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
            assertExact(source, result, source.replaceOnce("$annotation emits N", "$annotation emits N\n        emits N"))
        }
    }

    @Test
    fun addEmission_outcomeAnnotatedEmission_doesNotCountAsAlreadyEmitted() {
        val source = flow("    when A do x {\n        service S.Svc\n        emits E\n        @outcome(ok) emits N\n    }")
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
        assertExact(source, result, source.replaceOnce("@outcome(ok) emits N", "@outcome(ok) emits N\n        emits N"))
    }

    @Test
    fun addEmission_rejects_whenTheOccurrenceWouldStopInheritingItsBody() {
        val source = flow("    when A do x {}\n    do x {\n        service S.Svc\n        call y\n        emits E\n    }")
        assertRejected(editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N"), "inherited body")
    }

    @Test
    fun addEmission_toAnOccurrenceThatInheritsOnlyItsEmissions_dropsTheInheritedOnes() {
        val source = flow(
            "    when A do x {\n" +
                "        service S.Svc\n" +
                "    }\n" +
                "    do x {\n" +
                "        service S.Svc\n" +
                "        emits E\n" +
                "    }"
        )
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
        val expected = source.replaceOnce(
            "    when A do x {\n        service S.Svc\n    }",
            "    when A do x {\n        service S.Svc\n        emits N\n    }"
        )
        assertExact(source, result, expected)
        assertEquals(1, result.edits.size)
        assertTrue(result.diagnostics.isEmpty(), "diagnostics: ${result.diagnostics}")
        val command = analyze(expected).flows.single().commands.single { it.name == "x" }
        val edited = command.occurrences.single { it.key == "x@when[A]" }
        val definition = command.occurrences.single { it.key == "x@definition" }
        assertEquals(2, edited.steps.size)
        assertEquals(listOf("N"), edited.emissions.map { it.eventName })
        assertEquals(listOf("E"), definition.emissions.map { it.eventName })
    }

    @Test
    fun addEmission_mixingAnOutcomeAnnotatedEmission_returnsTheAnalyzerWarning() {
        val source = flow("    when A do x {\n        service S.Svc\n        @outcome(ok) emits E\n    }")
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
        assertTrue(result.accepted)
        val warnings = result.diagnostics.filter { it.severity == EditSeverity.WARNING }
        assertTrue(warnings.any { it.message.contains("mixes @outcome annotated and unannotated emits") }, "diagnostics: ${result.diagnostics}")
        assertNoOp(editor.addEmissionToOccurrence(applyEdits(source, result.edits), "F", "x@when[A]", "N"))
    }

    @Test
    fun addEmission_warningsAlreadyInTheSource_areNotReportedAsIntroduced() {
        val source = flow("    when A do x {\n        service S.Svc\n        @outcome(ok) emits E\n        emits M\n    }")
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
        assertTrue(result.accepted)
        assertTrue(result.diagnostics.isEmpty(), "diagnostics: ${result.diagnostics}")
    }

    @Test
    fun addEmission_rejects_eventNamesThatAreNotOneAsciiIdentifier() {
        val source = flow(whenX)
        val names = listOf(
            "Café", "Évent", "Foo#", "N1, N2", "response N", "N\n call x", "N // hi", "N /* c */",
            "", " N", "N ", "1N", "emits", "when", "N-M"
        )
        for (name in names) {
            val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", name)
            assertRejected(result, "not a valid event name")
        }
    }

    @Test
    fun addEmission_unicodeEventNameThatSplitsIntoAsciiFragments_isRejected() {
        val result = editor.addEmissionToOccurrence(simpleFlow(), "TestFlow", "createOrder@definition", "Unicode_is_fine_but_Ünïcödé_is_not")
        assertRejected(result, "not a valid event name")
    }

    @Test
    fun addEmission_rejects_whenFlowNotFound() {
        assertRejected(editor.addEmissionToOccurrence(simpleFlow(), "NonexistentFlow", "createOrder@definition", "Foo"), "Flow 'NonexistentFlow' not found")
    }

    @Test
    fun addEmission_rejects_whenOccurrenceNotFound() {
        assertRejected(editor.addEmissionToOccurrence(simpleFlow(), "TestFlow", "nonexistentCommand@definition", "Foo"), "not found")
    }

    @Test
    fun addEmission_rejects_occurrenceWithoutBody_withTheExplicitReason() {
        val source = flow("    when A do x\n    do x {\n        service S.Svc\n        emits E\n    }")
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
        assertRejected(result, "has no body", "unsupported until ES-P2")
    }

    @Test
    fun addEmission_bodylessFirstBlock_secondBlockIsAddressedByTheAnalyzerKey() {
        val source = flow("    when A do x\n    when A do x {\n        service S.Svc\n        emits E\n    }")
        assertEquals(listOf("x@when[A]", "x@when[A]#2"), editor.occurrenceKeys(source, "F"))
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]#2", "N")
        assertExact(source, result, source.replaceOnce("emits E\n", "emits E\n        emits N\n"))
        // the bare key is ambiguous, so it is a rejection rather than an edit of either block
        assertTrue(editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N").let { !it.accepted })
    }

    @Test
    fun addEmission_differentFlows_separateTargets() {
        val source = fixture()
        val r1 = editor.addEmissionToOccurrence(source, "orderFlow", "createOrder@when[OrderPlaced]", "FlowOneEvent")
        val r2 = editor.addEmissionToOccurrence(source, "orderFlow2", "createOrder@when[OrderPlaced]", "FlowTwoEvent")
        assertExact(source, r1, source.replaceOnce("        emits OrderFailed\n    }", "        emits OrderFailed\n        emits FlowOneEvent\n    }"))
        val flow2Start = source.indexOf("flow orderFlow2 {")
        assertTrue(r2.edits.single().start > flow2Start, "orderFlow2's edit must be inside orderFlow2")
        assertTrue(r1.edits.single().start < flow2Start, "orderFlow's edit must be inside orderFlow")
        assertValidZfl(source, r2)
    }

    @Test
    fun addEmission_responseFormUnchanged() {
        val source = fixture()
        val result = editor.addEmissionToOccurrence(source, "orderFlow", "reserveStock@when[OrderCreated]", "ReservationLogged")
        assertExact(source, result, source.replaceOnce(
            "        on OutOfStock response StockUnavailable\n", "        on OutOfStock response StockUnavailable\n        emits ReservationLogged\n"
        ))
    }

    @Test
    fun addEmission_actorStartsCommentsAndAnnotationsStayByteForByte() {
        val source = fixture()
        val result = editor.addEmissionToOccurrence(source, "orderFlow", "createOrder@when[OrderPlaced]", "OrderTracked")
        assertExact(source, result, source.replaceOnce("        emits OrderFailed\n    }", "        emits OrderFailed\n        emits OrderTracked\n    }"))
    }

    // ES-P1 Unicode: one emoji earlier in the file must not shift any edit (offsets are UTF-16 indexes)
    @Test
    fun addEmission_afterAstralCharacters_editsTheRightPlace() {
        val head = "// $emoji$emoji$emoji strings and comments may hold Unicode 日本\n"
        val source = head + flow("    // keep $emoji this explanation é intact\n    @if(\"over $emoji limit\")\n$whenX")
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
        assertExact(source, result, source.replaceOnce("emits E\n", "emits E\n        emits N\n"))
    }

    @Test
    fun addEmission_afterAstralCharactersInCrlfSource_editsTheRightPlace() {
        val source = ("// $emoji$emoji\n" + flow("    /* $emoji */\n$whenX")).replace("\n", "\r\n")
        val result = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N")
        assertExact(source, result, source.replaceOnce("emits E\r\n", "emits E\r\n        emits N\r\n"))
    }

    // =========================================================================
    // Targeting: keys and ambiguity (ZVE-R3, ZVE-R13)
    // =========================================================================

    @Test
    fun occurrenceKeys_matchTheAnalyzerKeys() {
        val sources = listOf(
            fixture() to listOf("orderFlow", "orderFlow2", "shippingFlow"),
            flow("    when A do x\n    when A do x {\n        service S.Svc\n        emits E\n    }\n    do x {\n        service S.Svc\n    }\n    do x {\n        service S.Svc\n    }") to listOf("F"),
            flow("    when A, A do x {\n        service S.Svc\n        emits E\n    }\n    when A do x {\n        service S.Svc\n    }") to listOf("F"),
            flow("    when B, A do x {\n        service S.Svc\n        emits E\n    }\n    when A | B do x {\n        service S.Svc\n    }") to listOf("F")
        )
        for ((source, flows) in sources) {
            val model = analyze(source)
            for (flowName in flows) {
                val analyzerKeys = model.flows.single { it.name == flowName }.commands
                    .flatMap { c -> c.occurrences.map { it.key to it.index } }
                    .sortedBy { it.second }.map { it.first }
                assertEquals(analyzerKeys, editor.occurrenceKeys(source, flowName), "keys of $flowName")
            }
        }
    }

    @Test
    fun ambiguousKey_isRejectedUntilTheOriginIsSelected() {
        val source = equalEndpointsFixture()
        val edit = { index: Int? -> editor.changePolicyTrigger(source, "F", "x@when[A]", listOf(TriggerGroup(listOf("B"))), index) }
        val emit = { index: Int? -> editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N", index) }
        assertRejected(edit(null), "ambiguous", "2 blocks", "disambiguationIndex")
        assertRejected(emit(null), "ambiguous", "2 blocks", "disambiguationIndex")
        assertRejected(edit(2), "out of range")
        assertRejected(emit(-1), "out of range")
    }

    @Test
    fun disambiguationIndex_selectsTheBlockItNames() {
        val source = equalEndpointsFixture()
        val trigger = listOf(TriggerGroup(listOf("B")))
        val cheap = "    @if(\"cheap\")\n    when A do x {"
        val costly = "    @if(\"costly\")\n    when A do x {"
        assertExact(source, editor.changePolicyTrigger(source, "F", "x@when[A]", trigger, 0),
            source.replaceOnce(cheap, cheap.replace("when A", "when B")))
        assertExact(source, editor.changePolicyTrigger(source, "F", "x@when[A]", trigger, 1),
            source.replaceOnce(costly, costly.replace("when A", "when B")))
        // the analyzer's own key for the second block selects the same one
        assertEquals(editor.changePolicyTrigger(source, "F", "x@when[A]", trigger, 1).edits,
            editor.changePolicyTrigger(source, "F", "x@when[A]#2", trigger).edits)

        val emitted = editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N", 1)
        val costlyBody = costly + "\n        service S.Svc\n        emits E\n"
        assertExact(source, emitted, source.replaceOnce(costlyBody, costlyBody + "        emits N\n"))
    }

    @Test
    fun explicitHashKey_rejectsADisambiguationIndexAndUnknownSuffixes() {
        val source = equalEndpointsFixture()
        val trigger = listOf(TriggerGroup(listOf("B")))
        assertRejected(editor.changePolicyTrigger(source, "F", "x@when[A]#2", trigger, 0), "already selects one block")
        assertRejected(editor.changePolicyTrigger(source, "F", "x@when[A]#3", trigger), "not found")
    }

    @Test
    fun equalEndpointsWithDifferentConditions_inTheFixture_areSeparatelyAddressable() {
        val source = fixture()
        val trigger = listOf(TriggerGroup(listOf("ParcelReturned")))
        val first = "    @if(\"peso > 10 kg 📦\")\n    when ParcelReady do chooseCarrier {"
        val second = "    @if(\"peso <= 10 kg — sin cargo\")\n    when ParcelReady do chooseCarrier {"
        assertRejected(editor.changePolicyTrigger(source, "shippingFlow", "chooseCarrier@when[ParcelReady]", trigger), "ambiguous")
        assertExact(source, editor.changePolicyTrigger(source, "shippingFlow", "chooseCarrier@when[ParcelReady]", trigger, 0),
            source.replaceOnce(first, first.replace("when ParcelReady", "when ParcelReturned")))
        assertExact(source, editor.changePolicyTrigger(source, "shippingFlow", "chooseCarrier@when[ParcelReady]", trigger, 1),
            source.replaceOnce(second, second.replace("when ParcelReady", "when ParcelReturned")))
    }

    @Test
    fun parallelEdges_toOneCommand_areDistinguishedByTheirPolicy() {
        val source = fixture()
        val trigger = listOf(TriggerGroup(listOf("ParcelReturned")))
        // ParcelReady reaches auditParcel alone and together with CarrierChosen
        assertExact(source, editor.changePolicyTrigger(source, "shippingFlow", "auditParcel@when[ParcelReady]", trigger),
            source.replaceOnce("when ParcelReady do auditParcel", "when ParcelReturned do auditParcel"))
        assertExact(source, editor.changePolicyTrigger(source, "shippingFlow", "auditParcel@when[CarrierChosen,ParcelReady]", trigger),
            source.replaceOnce("when ParcelReady and CarrierChosen do auditParcel", "when ParcelReturned do auditParcel"))
    }

    @Test
    fun twoPoliciesToOneCommand_withDifferentTriggers_changeIndependently() {
        val source = fixture()
        val result = editor.changePolicyTrigger(source, "orderFlow", "authorizePayment@when[OrderCreated]", listOf(TriggerGroup(listOf("OrderTimeout"))))
        assertExact(source, result, source.replaceOnceIn("orderFlow", "when OrderCreated do authorizePayment", "when OrderTimeout do authorizePayment"))
    }

    @Test
    fun addEmission_twoOccurrencesOfOneCommand_targetDifferentBlocks() {
        val source = fixture()
        val r1 = editor.addEmissionToOccurrence(source, "orderFlow", "authorizePayment@when[OrderCreated]", "AuthAuditLogged")
        val r2 = editor.addEmissionToOccurrence(source, "orderFlow", "authorizePayment@when[PaymentDeclined]", "RetryAuditLogged")
        assertValidZfl(source, r1)
        assertValidZfl(source, r2)
        assertTrue(r1.edits.single().start < r2.edits.single().start)
        assertTrue(applyEdits(source, r1.edits).contains("AuthAuditLogged"))
        assertFalse(applyEdits(source, r1.edits).contains("RetryAuditLogged"))
    }

    // =========================================================================
    // Operation 2: Change a policy trigger
    // =========================================================================

    @Test
    fun changePolicyTrigger_exactOutputOnFixture() {
        val source = fixture()
        val result = editor.changePolicyTrigger(source, "orderFlow", "createOrder@when[OrderPlaced]", listOf(TriggerGroup(listOf("OrderTimeout"))))
        assertExact(source, result, source.replaceOnceIn("orderFlow", "when OrderPlaced do createOrder", "when OrderTimeout do createOrder"))
    }

    @Test
    fun changePolicyTrigger_noOp_whenUnchanged() {
        val source = fixture()
        assertNoOp(editor.changePolicyTrigger(source, "orderFlow", "createOrder@when[OrderPlaced]", listOf(TriggerGroup(listOf("OrderPlaced")))))
        // an AND of two groups, spelled as it is
        assertNoOp(editor.changePolicyTrigger(source, "orderFlow", "fulfillOrder@when[PaymentAuthorized,StockReserved]",
            listOf(TriggerGroup(listOf("PaymentAuthorized")), TriggerGroup(listOf("StockReserved")))))
    }

    @Test
    fun changePolicyTrigger_unchangedOrGroup_isANoOpAndKeepsTheSpelling() {
        val source = fixture()
        val key = "notifyCustomer@when[CarrierChosen,ParcelReturned]"
        assertNoOp(editor.changePolicyTrigger(source, "shippingFlow", key,
            listOf(TriggerGroup(listOf("CarrierChosen", "ParcelReturned"), TriggerSeparator.OR))))
        // same events, different spelling of the group is a change
        val comma = editor.changePolicyTrigger(source, "shippingFlow", key,
            listOf(TriggerGroup(listOf("CarrierChosen", "ParcelReturned"), TriggerSeparator.COMMA)))
        assertExact(source, comma, source.replaceOnce("when CarrierChosen | ParcelReturned do notifyCustomer", "when CarrierChosen, ParcelReturned do notifyCustomer"))
    }

    @Test
    fun changePolicyTrigger_omittedSeparatorKeepsTheExistingSeparator() {
        val source = fixture()
        val result = editor.changePolicyTrigger(source, "shippingFlow", "notifyCustomer@when[CarrierChosen,ParcelReturned]",
            listOf(TriggerGroup(listOf("CarrierChosen", "ParcelReady"))))
        assertExact(source, result, source.replaceOnceIn("shippingFlow", "when CarrierChosen | ParcelReturned do notifyCustomer", "when CarrierChosen | ParcelReady do notifyCustomer"))
    }

    @Test
    fun changePolicyTrigger_rejectsStructuralMultiEventGroupWithoutASeparator() {
        val source = flow("    when A do x {\n        service S.Svc\n        emits E\n    }")
        assertRejected(editor.changePolicyTrigger(source, "F", "x@when[A]", listOf(TriggerGroup(listOf("A", "B")))), "explicit separator")
    }

    @Test
    fun changePolicyTrigger_nullSeparatorKeepsTrailingAndMixedSeparatorTokens() {
        val trailing = "systems { S { service Svc } }\n" +
            "flow F {\n" +
            "    start A {}\n    start B {}\n    start C {}\n" +
            "    when A, B, do x {\n        service S.Svc\n        emits E\n    }\n" +
            "    end { completed: E }\n}\n"
        val trailingChanged = editor.changePolicyTrigger(trailing, "F", "x@when[A,B]", listOf(TriggerGroup(listOf("A", "C"))))
        assertExact(trailing, trailingChanged, trailing.replaceOnce("when A, B, do x", "when A, C, do x"))

        val mixed = "systems { S { service Svc } }\n" +
            "flow F {\n" +
            "    start A {}\n    start B {}\n    start C {}\n    start D {}\n" +
            "    when A, B | C do x {\n        service S.Svc\n        emits E\n    }\n" +
            "    end { completed: E }\n}\n"
        val mixedChanged = editor.changePolicyTrigger(mixed, "F", "x@when[A,B,C]", listOf(TriggerGroup(listOf("A", "B", "D"))))
        assertExact(mixed, mixedChanged, mixed.replaceOnce("when A, B | C do x", "when A, B | D do x"))
    }

    @Test
    fun changePolicyTrigger_unchangedTrailingOrMixedGroup_isANoOp() {
        val trailing = flow("    when A, B, do x {\n        service S.Svc\n        emits E\n    }")
        assertNoOp(editor.changePolicyTrigger(trailing, "F", "x@when[A,B]", listOf(TriggerGroup(listOf("A", "B")))))

        val mixed = flow("    when A, B | E do x {\n        service S.Svc\n        emits E\n    }")
        assertNoOp(editor.changePolicyTrigger(mixed, "F", "x@when[A,B,E]", listOf(TriggerGroup(listOf("A", "B", "E")))))
    }

    @Test
    fun changePolicyTrigger_changingAnotherGroupKeepsNullSeparatorTokens() {
        for ((firstGroup, events) in listOf(
            "A, B | C" to listOf("A", "B", "C"),
            "A, B," to listOf("A", "B"),
            "A," to listOf("A")
        )) {
            val source = flow("    start C {}\n    start D {}\n    when $firstGroup and D, E do x {\n        service S.Svc\n        emits E\n    }")
            val key = "x@when[${(events + listOf("D", "E")).sorted().joinToString(",")}]"
            val result = editor.changePolicyTrigger(source, "F", key,
                listOf(TriggerGroup(events), TriggerGroup(listOf("D", "E"), TriggerSeparator.OR)))
            assertExact(source, result, source.replaceOnce("and D, E do x", "and D | E do x"))
        }
    }

    @Test
    fun changePolicyTrigger_unchangedSingleEventTrailingGroupWithExplicitSeparator_isANoOp() {
        for (expression in listOf("A,", "A |")) {
            val source = flow(whenX.replace("when A do x", "when $expression do x"))
            for (separator in TriggerSeparator.entries) {
                assertNoOp(editor.changePolicyTrigger(source, "F", "x@when[A]",
                    listOf(TriggerGroup(listOf("A"), separator))))
            }
        }
    }

    @Test
    fun changePolicyTrigger_orGroup_changesOnlyTheNamedEventAndKeepsTheBar() {
        val source = fixture()
        val result = editor.changePolicyTrigger(source, "shippingFlow", "notifyCustomer@when[CarrierChosen,ParcelReturned]",
            listOf(TriggerGroup(listOf("CarrierChosen", "ParcelReady"), TriggerSeparator.OR)))
        assertExact(source, result, source.replaceOnce("when CarrierChosen | ParcelReturned do notifyCustomer", "when CarrierChosen | ParcelReady do notifyCustomer"))
        assertEquals(1, result.edits.size)
    }

    @Test
    fun changePolicyTrigger_mixedGroups_keepOrWithinAndAndBetween() {
        val source = fixture()
        val key = "closeParcel@when[CarrierChosen,ParcelAudited,ParcelReturned]"
        val result = editor.changePolicyTrigger(source, "shippingFlow", key,
            listOf(TriggerGroup(listOf("CarrierChosen", "ParcelReady"), TriggerSeparator.OR), TriggerGroup(listOf("ParcelAudited"))))
        assertExact(source, result, source.replaceOnce("when CarrierChosen | ParcelReturned and ParcelAudited do closeParcel", "when CarrierChosen | ParcelReady and ParcelAudited do closeParcel"))
        // restructuring the groups rewrites the expression with the requested separators
        val restructured = editor.changePolicyTrigger(source, "shippingFlow", key,
            listOf(TriggerGroup(listOf("CarrierChosen")), TriggerGroup(listOf("ParcelReturned", "ParcelAudited"), TriggerSeparator.OR)))
        assertExact(source, restructured, source.replaceOnce("when CarrierChosen | ParcelReturned and ParcelAudited do closeParcel", "when CarrierChosen and ParcelReturned | ParcelAudited do closeParcel"))
    }

    @Test
    fun changePolicyTrigger_andGroupsOfTheFixture_exactOutput() {
        val source = fixture()
        val result = editor.changePolicyTrigger(source, "orderFlow", "fulfillOrder@when[PaymentAuthorized,StockReserved]",
            listOf(TriggerGroup(listOf("PaymentAuthorized")), TriggerGroup(listOf("OrderTimeout"))))
        assertExact(source, result, source.replaceOnce("when PaymentAuthorized and StockReserved do fulfillOrder", "when PaymentAuthorized and OrderTimeout do fulfillOrder"))
    }

    @Test
    fun changePolicyTrigger_sameStructure_keepsSpacingAndComments() {
        val source = flow("    when A /* first */ ,   B do x {\n        service S.Svc\n        emits E\n    }")
        val result = editor.changePolicyTrigger(source, "F", "x@when[A,B]", listOf(TriggerGroup(listOf("B", "A"))))
        assertExact(source, result, source.replaceOnce("when A /* first */ ,   B do x", "when B /* first */ ,   A do x"))
    }

    @Test
    fun changePolicyTrigger_structureChangeThroughComments_isRejected() {
        val source = flow("    when A /* first */ , B do x {\n        service S.Svc\n        emits E\n    }")
        val result = editor.changePolicyTrigger(source, "F", "x@when[A,B]", listOf(TriggerGroup(listOf("A"))))
        assertRejected(result, "holds comments", "unsupported until ES-P2")
    }

    @Test
    fun changePolicyTrigger_rejects_namesThatAreNotOneAsciiIdentifier() {
        val source = flow(whenX)
        val bad = listOf("Bé", "B do x { service S.Svc emits Z } when A", "", "B, C", "B | C", "response", "B\nC")
        for (name in bad) {
            assertRejected(editor.changePolicyTrigger(source, "F", "x@when[A]", listOf(TriggerGroup(listOf(name)))), "not a valid event name")
        }
    }

    @Test
    fun changePolicyTrigger_rejects_emptyAndDuplicateInput() {
        val source = flow(whenX)
        assertRejected(editor.changePolicyTrigger(source, "F", "x@when[A]", emptyList()), "at least one")
        assertRejected(editor.changePolicyTrigger(source, "F", "x@when[A]", listOf(TriggerGroup(emptyList()))), "at least one")
        assertRejected(editor.changePolicyTrigger(source, "F", "x@when[A]", listOf(TriggerGroup(listOf("A", "A")))), "Duplicate trigger event 'A'")
        assertRejected(editor.changePolicyTrigger(source, "F", "x@when[A]", listOf(TriggerGroup(listOf("B")), TriggerGroup(listOf("B")))), "Duplicate trigger event 'B'")
    }

    @Test
    fun changePolicyTrigger_afterAstralCharacters_editsTheRightPlace() {
        val source = "// $emoji$emoji$emoji\n" + flow("    // keep $emoji this explanation of the policy intact please\n    @if(\"over $emoji limit\")\n$whenX")
        val result = editor.changePolicyTrigger(source, "F", "x@when[A]", listOf(TriggerGroup(listOf("B"))))
        assertExact(source, result, source.replaceOnce("when A do x", "when B do x"))
    }

    @Test
    fun changePolicyTrigger_rejects_notFoundAndDefinitionKeys() {
        val source = simpleFlow()
        assertRejected(editor.changePolicyTrigger(source, "NoSuchFlow", "createOrder@when[OrderPlaced]", listOf(TriggerGroup(listOf("X")))), "not found")
        assertRejected(editor.changePolicyTrigger(source, "TestFlow", "createOrder@definition", listOf(TriggerGroup(listOf("X")))), "not found")
    }

    // =========================================================================
    // Operation 3: Rename a command
    // =========================================================================

    @Test
    fun renameCommand_updatesAllReferences_exactOutput() {
        val source = simpleFlow()
        val result = editor.renameCommand(source, "TestFlow", "createOrder", "placeOrder")
        assertExact(source, result, source.replace("createOrder", "placeOrder"))
        assertEquals(2, result.edits.size)
    }

    @Test
    fun renameCommand_updatesCallAndOnCallTargets() {
        val source = flow(
            "    when A do x {\n        service S.Svc\n        call y\n        on Ok call y\n        emits E\n    }\n" +
                "    do y {\n        service S.Svc\n        emits response Ok\n    }"
        )
        val result = editor.renameCommand(source, "F", "y", "renamed")
        assertExact(source, result, source.replace(" y\n", " renamed\n").replace("do y {", "do renamed {"))
        assertEquals(3, result.edits.size)
    }

    @Test
    fun renameCommand_fixtureCallReference_exactOutput() {
        val source = fixture()
        val result = editor.renameCommand(source, "orderFlow", "checkInventory", "verifyInventory")
        assertExact(source, result, source.replaceOnce("call checkInventory", "call verifyInventory").replaceOnce("do checkInventory {", "do verifyInventory {"))
    }

    @Test
    fun renameCommand_updatesTheCompensatesReference() {
        val source = flow("    when A do x {\n        service S.Svc\n        emits E\n    }\n    @compensates(x)\n    when E do y {\n        service S.Svc\n    }")
        val result = editor.renameCommand(source, "F", "x", "renamed")
        assertExact(source, result, source.replaceOnce("when A do x {", "when A do renamed {").replaceOnce("@compensates(x)", "@compensates(renamed)"))
    }

    @Test
    fun renameCommand_updatesEveryPlainValueInACompensatesList() {
        val source = flow("    @compensates(x, y)\n    do y {\n        service S.Svc\n    }\n    do x {\n        service S.Svc\n        emits E\n    }")
        val result = editor.renameCommand(source, "F", "x", "renamed")
        assertExact(source, result, source.replaceOnce("@compensates(x, y)", "@compensates(renamed, y)").replaceOnce("do x {", "do renamed {"))
    }

    @Test
    fun renameCommand_rejectsNonPlainCompensatesValuesThatNameTheCommand() {
        for (value in listOf("[x]", "target: x", "\"x\"")) {
            val source = flow("    @compensates($value)\n    when A do x {\n        service S.Svc\n        emits E\n    }")
            assertRejected(editor.renameCommand(source, "F", "x", "renamed"), "@compensates")
        }
    }

    @Test
    fun renameCommand_doesNotTouchCallsOrCompensatesInAnotherFlow() {
        val source = "systems { S { service Svc } }\n" +
            "flow F {\n    start A {}\n    when A do x {\n        service S.Svc\n        emits E\n    }\n    end { completed: E }\n}\n" +
            "flow G {\n    start A {}\n    when A do y {\n        service S.Svc\n        call x\n        on E call x\n        async call x\n        @compensates(x) emits N\n    }\n    do x {\n        service S.Svc\n        emits response E\n    }\n    end { completed: N }\n}\n"
        val result = editor.renameCommand(source, "F", "x", "renamed")
        assertExact(source, result, source.replaceOnceIn("F", "do x {", "do renamed {"))
    }

    @Test
    fun renameCommand_doesNotTouchOtherFlows() {
        val source = fixture()
        val result = editor.renameCommand(source, "orderFlow", "createOrder", "registerOrder")
        assertExact(source, result, source.replaceOnce("when OrderPlaced do createOrder {\n        service OrderSystem.OrderService\n        emits OrderCreated\n        emits OrderFailed",
            "when OrderPlaced do registerOrder {\n        service OrderSystem.OrderService\n        emits OrderCreated\n        emits OrderFailed"))
        assertTrue(applyEdits(source, result.edits).substringAfter("flow orderFlow2").contains("do createOrder"))
    }

    @Test
    fun renameCommand_ofARepeatedCommand_renamesEveryBlock() {
        val source = fixture()
        val result = editor.renameCommand(source, "shippingFlow", "chooseCarrier", "pickCarrier")
        assertExact(source, result, source.replace("do chooseCarrier {", "do pickCarrier {"))
        assertEquals(2, result.edits.size)
    }

    @Test
    fun renameCommand_afterAstralCharacters_editsTheRightPlace() {
        val source = "// $emoji$emoji$emoji$emoji$emoji\n" + flow("    // keep $emoji this explanation of the policy intact please\n    @if(\"over $emoji limit\")\n$whenX")
        val result = editor.renameCommand(source, "F", "x", "renamed")
        assertExact(source, result, source.replaceOnce("when A do x {", "when A do renamed {"))
        assertEquals(result.edits.single().start, source.indexOf("do x {") + 3)
    }

    @Test
    fun renameCommand_crlfSource_exactOutput() {
        val source = flow(whenX).replace("\n", "\r\n")
        val result = editor.renameCommand(source, "F", "x", "renamed")
        assertExact(source, result, source.replaceOnce("do x {", "do renamed {"))
    }

    @Test
    fun renameCommand_returnsTheIdentityMapping() {
        val result = editor.renameCommand(simpleFlow(), "TestFlow", "createOrder", "placeOrder")
        assertEquals(listOf(ZflIdentityMapping("TestFlow", "command", "createOrder", "placeOrder")), result.identityMappings)
    }

    // ES-P1 exit: identity reconciliation preserves rename continuity (ZVE-R3)
    private data class Selected(val flow: String, val kind: String, val name: String)

    private fun reconcile(selection: List<Selected>, mappings: List<ZflIdentityMapping>): List<Selected> =
        selection.map { s ->
            mappings.find { it.flowName == s.flow && it.kind == s.kind && it.oldName == s.name }
                ?.let { s.copy(name = it.newName) } ?: s
        }

    /** What a node of the projection is, apart from its name. */
    private fun commandShape(model: ZflSemanticModel, s: Selected): List<Any> {
        val command = model.flows.single { it.name == s.flow }.commands.single { it.name == s.name }
        return listOf(command.emits.map { it.eventName }, command.occurrences.map { it.triggers }, command.steps.size, command.service.orEmpty())
    }

    @Test
    fun renameCommand_reconciledSelectionFollowsTheSameNode() {
        val source = fixture()
        val before = analyze(source)
        val selection = listOf(
            Selected("orderFlow", "command", "authorizePayment"),
            Selected("orderFlow2", "command", "authorizePayment"), // equal name in another flow: must not follow
            Selected("orderFlow", "command", "reserveStock")
        )
        val result = editor.renameCommand(source, "orderFlow", "authorizePayment", "approvePayment")
        val after = analyze(applyEdits(source, result.edits))
        val reconciled = reconcile(selection, result.identityMappings)

        assertEquals(
            listOf(Selected("orderFlow", "command", "approvePayment"), Selected("orderFlow2", "command", "authorizePayment"), Selected("orderFlow", "command", "reserveStock")),
            reconciled
        )
        // every reconciled selection exists in the new projection and is the node that was selected before
        for ((old, now) in selection.zip(reconciled)) {
            assertEquals(commandShape(before, old), commandShape(after, now), "$old became a different node as $now")
        }
        // and the old name is gone from the renamed flow only
        assertTrue(after.flows.single { it.name == "orderFlow" }.commands.none { it.name == "authorizePayment" })
        assertTrue(after.flows.single { it.name == "orderFlow2" }.commands.any { it.name == "authorizePayment" })
    }

    @Test
    fun renameCommand_noOp_whenOldEqualsNew() {
        assertNoOp(editor.renameCommand(simpleFlow(), "TestFlow", "createOrder", "createOrder"))
    }

    @Test
    fun renameCommand_sameName_isStillCheckedAgainstTheSource() {
        assertRejected(editor.renameCommand("flow X { broken", "NoFlow", "a", "a"), "syntax errors")
        assertRejected(editor.renameCommand(simpleFlow(), "NoFlow", "a", "a"), "not found")
        assertRejected(editor.renameCommand(simpleFlow(), "TestFlow", "missing", "missing"), "not declared")
    }

    @Test
    fun renameCommand_rejects_whenNotDeclaredInFlow() {
        assertRejected(editor.renameCommand(simpleFlow(), "TestFlow", "nonexistentCommand", "newName"), "not declared")
        assertRejected(editor.renameCommand(simpleFlow(), "NoFlow", "createOrder", "newName"), "Flow 'NoFlow' not found")
    }

    @Test
    fun renameCommand_rejects_ontoAnExistingCommand() {
        val source = flow("$whenX\n    when E do y {\n        service S.Svc\n        emits E\n    }")
        assertRejected(editor.renameCommand(source, "F", "y", "x"), "already exists", "merge")
        // a command that is only called also exists
        val called = flow("    when A do x {\n        service S.Svc\n        call z\n        emits E\n    }\n    when E do y {\n        service S.Svc\n    }")
        assertRejected(editor.renameCommand(called, "F", "y", "z"), "already exists")
    }

    @Test
    fun renameCommand_ontoTheNameOfACommandInAnotherFlow_isAllowed() {
        val source = fixture()
        val result = editor.renameCommand(source, "orderFlow", "createOrder", "chooseCarrier")
        assertValidZfl(source, result)
    }

    @Test
    fun renameCommand_rejects_namesThatAreNotOneAsciiIdentifier() {
        val source = flow(whenX)
        for (name in listOf("café", "éx", "a b", "a;b", "a\n call x", "", "1a", "when", "emits", "a-b")) {
            assertRejected(editor.renameCommand(source, "F", "x", name), "not a valid command name")
        }
    }

    @Test
    fun renameCommand_rejects_whenTheSystemsBlockListsTheCommand() {
        val source = "systems { S { service Svc { commands: x } } }\nflow F {\n    start A {}\n$whenX\n    end { completed: E }\n}\n"
        assertRejected(editor.renameCommand(source, "F", "x", "renamed"), "commands:", "unsupported until ES-P2")
    }

    @Test
    fun renameCommand_rejects_whenAQuotedCompensatesNamesTheCommand() {
        val source = flow("$whenX\n    @compensates(\"x\")\n    when E do y {\n        service S.Svc\n    }")
        assertRejected(editor.renameCommand(source, "F", "x", "renamed"), "quoted or non-plain @compensates")
    }

    @Test
    fun renameCommand_rejectsCapturingDanglingCompensatesOrCommandsListReferences() {
        val dangling = flow("    @compensates(renamed)\n    when A do x {\n        service S.Svc\n        emits E\n    }")
        assertRejected(editor.renameCommand(dangling, "F", "x", "renamed"), "capture")
        val listed = "systems { S { service Svc { commands: renamed } } }\nflow F {\n    start A {}\n    when A do x {\n        service S.Svc\n        emits E\n    }\n    end { completed: E }\n}\n"
        assertRejected(editor.renameCommand(listed, "F", "x", "renamed"), "capture")
    }

    @Test
    fun renameCommand_doesNotReportAnExistingWarningAsIntroduced() {
        val source = flow("    when A do x {\n        service S.Svc\n        @outcome(ok) emits E\n        emits N\n    }")
        val result = editor.renameCommand(source, "F", "x", "renamed")
        assertTrue(result.accepted, "diagnostics: ${result.diagnostics}")
        assertTrue(result.diagnostics.isEmpty(), "diagnostics: ${result.diagnostics}")
    }

    @Test
    fun renameCommand_doesNotReportAWarningThatAlreadyExistsInAnotherFlow() {
        val command = "    when A do x {\n        service S.Svc\n        @outcome(ok) emits E\n        emits N\n    }"
        val source = flow(command, "F") + flow(command, "G").substringAfter("systems { S { service Svc } }\n")
        val result = editor.renameCommand(source, "F", "x", "renamed")
        assertTrue(result.accepted, "diagnostics: ${result.diagnostics}")
        assertTrue(result.diagnostics.isEmpty(), "diagnostics: ${result.diagnostics}")
        assertTrue(applyEdits(source, result.edits).substringAfter("flow G").contains("do x {"))
    }

    // =========================================================================
    // Common
    // =========================================================================

    @Test
    fun allOperations_rejectSyntaxErrors() {
        val broken = "flow X { syntax error here "
        assertRejected(editor.addEmissionToOccurrence(broken, "X", "foo@definition", "Bar"), "syntax errors")
        assertRejected(editor.changePolicyTrigger(broken, "X", "foo@when[Bar]", listOf(TriggerGroup(listOf("Baz")))), "syntax errors")
        assertRejected(editor.renameCommand(broken, "X", "foo", "bar"), "syntax errors")
    }

    @Test
    fun operations_rejectAnalyzerFailuresInsteadOfThrowingOrAccepting() {
        // These used to be rejected. The analyzer now reads scalar annotation values as text on both targets.
        val source = flow("    @if(true)\n$whenX")
        assertExact(source, editor.addEmissionToOccurrence(source, "F", "x@when[A]", "N"), source.replaceOnce("emits E\n", "emits E\n        emits N\n"))
        assertExact(source, editor.changePolicyTrigger(source, "F", "x@when[A]", listOf(TriggerGroup(listOf("B")))), source.replaceOnce("when A do x", "when B do x"))
        assertExact(source, editor.renameCommand(source, "F", "x", "renamed"), source.replaceOnce("when A do x {", "when A do renamed {"))

        val quoted = flow("    @if(\"true\")\n$whenX")
        assertExact(quoted, editor.addEmissionToOccurrence(quoted, "F", "x@when[A]", "N"), quoted.replaceOnce("emits E\n", "emits E\n        emits N\n"))
        assertExact(quoted, editor.changePolicyTrigger(quoted, "F", "x@when[A]", listOf(TriggerGroup(listOf("B")))), quoted.replaceOnce("when A do x", "when B do x"))
        assertExact(quoted, editor.renameCommand(quoted, "F", "x", "renamed"), quoted.replaceOnce("when A do x {", "when A do renamed {"))

        val maxlength = "systems { S { service Svc } }\n" +
            "flow F {\n" +
            "    start A {\n" +
            "        @maxlength(10) name String\n" +
            "    }\n" +
            "    when A do x {\n" +
            "        service S.Svc\n" +
            "        emits E\n" +
            "    }\n" +
            "    end { completed: E }\n" +
            "}\n"
        assertExact(maxlength, editor.addEmissionToOccurrence(maxlength, "F", "x@when[A]", "N"), maxlength.replaceOnce("emits E\n", "emits E\n        emits N\n"))
        assertExact(maxlength, editor.renameCommand(maxlength, "F", "x", "renamed"), maxlength.replaceOnce("when A do x {", "when A do renamed {"))

        val numbered = flow("    @foo(3)\n$whenX")
        assertExact(numbered, editor.changePolicyTrigger(numbered, "F", "x@when[A]", listOf(TriggerGroup(listOf("B")))), numbered.replaceOnce("when A do x", "when B do x"))
        assertExact(numbered, editor.addEmissionToOccurrence(numbered, "F", "x@when[A]", "N"), numbered.replaceOnce("emits E\n", "emits E\n        emits N\n"))
    }

    @Test
    fun operations_rejectPreExistingSemanticErrorsWithoutBlamingTheEdit() {
        val source = flow("    when A do x {\n        service S.Svc\n        emits E\n    }") +
            "flow G {\n    start A {}\n    end { completed: Missing }\n}\n"
        assertRejected(editor.renameCommand(source, "F", "x", "renamed"), "Source has semantic errors")
    }

    @Test
    fun changePolicyTrigger_rejectsWhenTheEditProducesASemanticError() {
        val source = flow(whenX)
        assertRejected(
            editor.changePolicyTrigger(source, "F", "x@when[A]", listOf(TriggerGroup(listOf("A2")))),
            "Edit produces semantic errors",
            "unknown trigger 'A2'"
        )
    }

    @Test
    fun changePolicyTrigger_rejectsWhenAnOverflowingAnnotationPreventsSemanticValidation() {
        val source = "@foo(9223372036854775808)\n" + flow(whenX)
        assertTrue(ZflParser().parse(source).syntaxProblems.isEmpty())
        assertRejected(editor.changePolicyTrigger(source, "F", "x@when[A]",
            listOf(TriggerGroup(listOf("Missing")))))
    }

    @Test
    fun anAmbiguousFlowName_isRejected() {
        val source = flow(whenX) + "flow F {\n    start A {}\n}\n"
        assertRejected(editor.renameCommand(source, "F", "x", "y"), "2 flows")
    }

    @Test
    fun applyEdits_rejectsRangesOutsideTheSource() {
        assertFailsWith<IllegalArgumentException> { applyEdits("abc", listOf(ZflTextEdit(2, 9, "x"))) }
        assertFailsWith<IllegalArgumentException> { applyEdits("abc", listOf(ZflTextEdit(0, 1, "x"), ZflTextEdit(0, 2, "y"))) }
    }

    @Test
    fun editsAreUtf16IndexesOfTheOriginalString() {
        val source = flow(whenX)
        val withEmoji = "// $emoji\n$source"
        val plain = editor.renameCommand(source, "F", "x", "renamed").edits.single()
        val shifted = editor.renameCommand(withEmoji, "F", "x", "renamed").edits.single()
        // the emoji is two UTF-16 units plus ' ', '/', '/' and a newline ahead of the same text
        assertEquals(plain.start + "// $emoji\n".length, shifted.start)
        assertEquals("x", withEmoji.substring(shifted.start, shifted.end))
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
        |
    """.trimMargin()

    /** Two `when` blocks with the same trigger and command and different conditions: one occurrence key. */
    private fun equalEndpointsFixture(): String = flow(
        "    @if(\"cheap\")\n    when A do x {\n        service S.Svc\n        emits E\n    }\n" +
            "    @if(\"costly\")\n    when A do x {\n        service S.Svc\n        emits E\n    }"
    )
}
