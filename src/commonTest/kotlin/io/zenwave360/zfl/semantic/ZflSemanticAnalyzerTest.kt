package io.zenwave360.zfl.semantic

import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.semantic.ZflSemanticAnalyzer
import io.zenwave360.language.zfl.semantic.toJsonString
import io.zenwave360.zdl.internal.readTestFile
import kotlin.test.*

class ZflSemanticAnalyzerTest {

    @Test
    fun testAnalyze_SubscriptionsFlow() {
        // Parse the ZFL file
        val content = readTestFile("flow/subscriptions.zfl")
        val model = ZflParser().parseModel(content)
        
        // Analyze the model
        val analyzer = ZflSemanticAnalyzer()
        val semanticModel = analyzer.analyze(model)
        println(semanticModel.toJsonString())
        
        // Verify flows
        assertEquals(1, semanticModel.flows.size, "Should have 1 flow")
        val flow = semanticModel.flows.first()
        assertEquals("PaymentsFlow", flow.name)
        
        // Verify systems
        assertEquals(3, semanticModel.systems.size, "Should have 3 systems")
        assertTrue(semanticModel.systems.containsKey("Subscription"))
        assertTrue(semanticModel.systems.containsKey("Payments"))
        assertTrue(semanticModel.systems.containsKey("Billing"))
        
        // Verify commands
        assertEquals(7, flow.commands.size, "Should have 7 commands total")
        val commandNames = flow.commands.map { it.name }.toSet()
        assertTrue(commandNames.contains("renewSubscription"))
        assertTrue(commandNames.contains("suspendSubscription"))
        assertTrue(commandNames.contains("cancelRenewal"))
        assertTrue(commandNames.contains("chargePayment"))
        assertTrue(commandNames.contains("retryPayment"))
        assertTrue(commandNames.contains("recordPayment"))
        assertTrue(commandNames.contains("generateInvoice"))
        
        // Verify command-system mapping
        val renewCommand = flow.commands.find { it.name == "renewSubscription" }
        assertNotNull(renewCommand)
        assertEquals("Subscription", renewCommand.system)
        
        val chargeCommand = flow.commands.find { it.name == "chargePayment" }
        assertNotNull(chargeCommand)
        assertEquals("Payments", chargeCommand.system)
        
        val generateCommand = flow.commands.find { it.name == "generateInvoice" }
        assertNotNull(generateCommand)
        assertEquals("Billing", generateCommand.system)
        
        // Verify actors
        assertEquals(1, semanticModel.actors.size, "Should have 1 actor")
        assertTrue(semanticModel.actors.containsKey("Customer"))
        
        // Verify events
        assertEquals(7, flow.events.size, "Should have 7 unique events")
        val eventNames = flow.events.map { it.name }.toSet()
        assertTrue(eventNames.contains("SubscriptionRenewed"))
        assertTrue(eventNames.contains("PaymentSucceeded"))
        assertTrue(eventNames.contains("PaymentFailed"))
        assertTrue(eventNames.contains("PaymentRetryScheduled"))
        assertTrue(eventNames.contains("SubscriptionSuspended"))
        assertTrue(eventNames.contains("PaymentRecorded"))
        assertTrue(eventNames.contains("RenewalCancelled"))
        

        // Verify policies whens
        assertEquals(6, flow.policies.size, "Should have 6 policies (when)")
        
        val policy1 = flow.policies.find { it.condition == "less than 3 attempts" }
        assertNotNull(policy1)
        assertEquals("PaymentFailed", policy1.triggers.first())
        assertEquals("retryPayment", policy1.command)
        
        val policy2 = flow.policies.find { it.condition == "3 or more attempts" }
        assertNotNull(policy2)
        assertEquals("PaymentFailed", policy2.triggers.first())
        assertEquals("suspendSubscription", policy2.command)
    }
    
    @Test
    fun testAnalyze_EmptyModel() {
        val model = ZflParser().parseModel("")
        val analyzer = ZflSemanticAnalyzer()
        val semanticModel = analyzer.analyze(model)

        assertEquals(0, semanticModel.flows.size)
        assertEquals(0, semanticModel.systems.size)
        assertEquals(0, semanticModel.actors.size)
    }

    @Test
    fun testAnalyze_EventSystemMapping() {
        // Verify that events are correctly mapped to the system of their command
        val content = readTestFile("flow/subscriptions.zfl")
        val model = ZflParser().parseModel(content)
        val analyzer = ZflSemanticAnalyzer()
        val semanticModel = analyzer.analyze(model)

        val flow = semanticModel.flows.first()

        // SubscriptionRenewed should be from Subscription system (renewSubscription command)
        val subscriptionRenewed = flow.events.find { it.name == "SubscriptionRenewed" }
        assertNotNull(subscriptionRenewed)
        assertEquals("Subscription", subscriptionRenewed.system)

        // PaymentSucceeded should be from Payments system (chargePayment command)
        val paymentSucceeded = flow.events.find { it.name == "PaymentSucceeded" }
        assertNotNull(paymentSucceeded)
        assertEquals("Payments", paymentSucceeded.system)

        // PaymentRecorded should be from Payments system (recordPayment command)
        val paymentRecorded = flow.events.find { it.name == "PaymentRecorded" }
        assertNotNull(paymentRecorded)
        assertEquals("Payments", paymentRecorded.system)
    }

    @Test
    fun testAnalyze_MultipleActors() {
        // Test with a flow that has multiple actors
        val zflContent = """
            flow TestFlow {
                systems {
                    TestSystem {
                        service TestService {
                            commands: testCommand
                        }
                    }
                }

                @actor(User)
                start UserAction {
                }

                @actor(Admin)
                start AdminAction {
                }

                @actor(System)
                start SystemAction {
                }

                when UserAction {
                    command testCommand
                    event TestEvent
                }
            }
        """.trimIndent()

        val model = ZflParser().parseModel(zflContent)
        val analyzer = ZflSemanticAnalyzer()
        val semanticModel = analyzer.analyze(model)

        assertEquals(3, semanticModel.actors.size, "Should have 3 actors")
        assertTrue(semanticModel.actors.containsKey("User"))
        assertTrue(semanticModel.actors.containsKey("Admin"))
        assertTrue(semanticModel.actors.containsKey("System"))
    }

    @Test
    fun testAnalyze_SourceRefGeneration() {
        // Verify that source references are generated for all elements
        val content = readTestFile("flow/subscriptions.zfl")
        val model = ZflParser().parseModel(content)
        val analyzer = ZflSemanticAnalyzer()
        val semanticModel = analyzer.analyze(model)

        val flow = semanticModel.flows.first()

        // All commands should have source refs
        flow.commands.forEach { command ->
            assertNotNull(command.sourceRef, "Command ${command.name} should have a source ref")
            assertEquals("<zfl>", command.sourceRef.file)
        }

        // All events should have source refs
        flow.events.forEach { event ->
            assertNotNull(event.sourceRef, "Event ${event.name} should have a source ref")
            assertEquals("<zfl>", event.sourceRef.file)
        }

        // All policies should have source refs
        flow.policies.forEach { policy ->
            assertNotNull(policy.sourceRef, "Policy ${policy.triggers.joinToString("_and_")} should have a source ref")
            assertEquals("<zfl>", policy.sourceRef.file)
        }

        // All actors should have source refs
        semanticModel.actors.values.forEach { actor ->
            assertNotNull(actor.sourceRef, "Actor ${actor.name} should have a source ref")
            assertEquals("<zfl>", actor.sourceRef.file)
        }
    }
}

