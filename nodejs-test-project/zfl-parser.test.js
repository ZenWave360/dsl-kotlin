import { describe, it, before } from 'node:test';
import assert from 'node:assert/strict';
import { parseZfl } from '@zenwave360/dsl';
import fs from 'fs';
import { jsonPath, mapSize, arraySize } from './utils.js';

describe('ZFL Parser - Subscriptions', () => {
    let model;

    before(() => {
        // Read and parse the subscriptions.zfl file
        const zflContent = fs.readFileSync('../src/commonTest/resources/flow/subscriptions.zfl', 'utf8');
        model = parseZfl(zflContent);
        // Uncomment to debug the model structure
        // console.log(JSON.stringify(model, null, 2));
    });

    describe('imports', () => {
        it('should have 2 imports', () => {
            assert.equal(arraySize(jsonPath(model, "$.imports")), 2);
        });

        it('should parse first import correctly', () => {
            assert.equal(jsonPath(model, "$.imports[0].key"), "subscriptions");
            assert.equal(jsonPath(model, "$.imports[0].value"), "http://localhost:8080/subscription/model.zdl");
        });

        it('should parse second import correctly', () => {
            assert.equal(jsonPath(model, "$.imports[1].key"), "payments");
            assert.equal(jsonPath(model, "$.imports[1].value"), "com.example.domain:payments:RELEASE");
        });
    });

    describe('flow', () => {
        it('should have 1 flow', () => {
            assert.equal(mapSize(jsonPath(model, "$.flows")), 1);
        });

        it('should have correct flow name and className', () => {
            assert.equal(jsonPath(model, "$.flows.PaymentsFlow.name"), "PaymentsFlow");
            assert.equal(jsonPath(model, "$.flows.PaymentsFlow.className"), "PaymentsFlow");
        });

        it('should have javadoc', () => {
            assert.notEqual(jsonPath(model, "$.flows.PaymentsFlow.javadoc"), null);
        });
    });

    describe('systems', () => {
        it('should have 3 systems', () => {
            assert.equal(mapSize(jsonPath(model, "$.flows.PaymentsFlow.systems")), 3);
        });

        describe('Subscription system', () => {
            it('should have correct name and zdl', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.systems.Subscription.name"), "Subscription");
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.systems.Subscription.options.zdl"), "subscription/model.zdl");
            });

            it('should have 1 service', () => {
                assert.equal(mapSize(jsonPath(model, "$.flows.PaymentsFlow.systems.Subscription.services")), 1);
            });

            it('should have SubscriptionService with correct commands', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.systems.Subscription.services.SubscriptionService.name"), "SubscriptionService");
                const commands = jsonPath(model, "$.flows.PaymentsFlow.systems.Subscription.services.SubscriptionService.commands");
                assert.equal(arraySize(commands), 3);
                assert.equal(commands[0], "renewSubscription");
                assert.equal(commands[1], "suspendSubscription");
                assert.equal(commands[2], "cancelRenewal");
            });
        });

        describe('Payments system', () => {
            it('should have correct name', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.systems.Payments.name"), "Payments");
            });

            it('should not have zdl', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.systems.Payments.zdl"), null);
            });
        });

        describe('Billing system', () => {
            it('should have correct name', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.systems.Billing.name"), "Billing");
            });
        });
    });

    describe('start events', () => {
        it('should have 3 start events', () => {
            assert.equal(mapSize(jsonPath(model, "$.flows.PaymentsFlow.starts")), 3);
        });

        describe('CustomerRequestsSubscriptionRenewal', () => {
            it('should have correct name', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.name"), "CustomerRequestsSubscriptionRenewal");
            });

            it('should have actor option', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.options.actor"), "Customer");
            });

            it('should have 3 fields', () => {
                assert.equal(mapSize(jsonPath(model, "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.fields")), 3);
            });

            it('should have correct field types', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.fields.subscriptionId.type"), "String");
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.fields.customerId.type"), "String");
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.starts.CustomerRequestsSubscriptionRenewal.fields.paymentMethodId.type"), "String");
            });
        });

        describe('BillingCycleEnded', () => {
            it('should have correct name', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.starts.BillingCycleEnded.name"), "BillingCycleEnded");
            });

            it('should have time option', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.starts.BillingCycleEnded.options.time"), "end of month");
            });

            it('should have 1 field', () => {
                assert.equal(mapSize(jsonPath(model, "$.flows.PaymentsFlow.starts.BillingCycleEnded.fields")), 1);
            });
        });

        describe('PaymentTimeout', () => {
            it('should have correct name', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.starts.PaymentTimeout.name"), "PaymentTimeout");
            });

            it('should have time option', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.starts.PaymentTimeout.options.time"), "5 minutes after SubscriptionRenewed and not PaymentSucceeded or PaymentFailed");
            });
        });
    });

    describe('when blocks', () => {
        it('should have 6 when blocks', () => {
            const whens = jsonPath(model, "$.flows.PaymentsFlow.whens");
            assert.equal(arraySize(whens), 6);
        });

        describe('first when block', () => {
            it('should have correct triggers', () => {
                const triggers = jsonPath(model, "$.flows.PaymentsFlow.whens[0].triggers");
                assert.equal(arraySize(triggers), 1);
                assert.equal(triggers[0], "CustomerRequestsSubscriptionRenewal");
            });

            it('should have correct command', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.whens[0].command"), "renewSubscription");
            });

            it('should have correct events', () => {
                const events = jsonPath(model, "$.flows.PaymentsFlow.whens[0].events");
                assert.equal(arraySize(events), 1);
                assert.equal(events[0], "SubscriptionRenewed");
            });
        });

        describe('second when block', () => {
            it('should have correct triggers', () => {
                const triggers = jsonPath(model, "$.flows.PaymentsFlow.whens[1].triggers");
                assert.equal(arraySize(triggers), 1);
                assert.equal(triggers[0], "SubscriptionRenewed");
            });

            it('should have correct command', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.whens[1].command"), "chargePayment");
            });

            it('should have correct events', () => {
                const events = jsonPath(model, "$.flows.PaymentsFlow.whens[1].events");
                assert.equal(arraySize(events), 2);
                assert.equal(events[0], "PaymentSucceeded");
                assert.equal(events[1], "PaymentFailed");
            });
        });

        describe('third when block with if condition', () => {
            it('should have correct triggers', () => {
                const triggers = jsonPath(model, "$.flows.PaymentsFlow.whens[2].triggers");
                assert.equal(arraySize(triggers), 1);
                assert.equal(triggers[0], "PaymentFailed");
            });

            it('should have correct if condition', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.whens[2].options.if"), "less than 3 attempts");
            });

            it('should have correct command', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.whens[2].command"), "retryPayment");
            });

            it('should have correct events', () => {
                const events = jsonPath(model, "$.flows.PaymentsFlow.whens[2].events");
                assert.equal(arraySize(events), 1);
                assert.equal(events[0], "PaymentRetryScheduled");
            });
        });

        describe('fourth when block with if condition (else case)', () => {
            it('should have correct triggers', () => {
                const triggers = jsonPath(model, "$.flows.PaymentsFlow.whens[3].triggers");
                assert.equal(arraySize(triggers), 1);
                assert.equal(triggers[0], "PaymentFailed");
            });

            it('should have correct if condition', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.whens[3].options.if"), "3 or more attempts");
            });

            it('should have correct command', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.whens[3].command"), "suspendSubscription");
            });

            it('should have correct events', () => {
                const events = jsonPath(model, "$.flows.PaymentsFlow.whens[3].events");
                assert.equal(arraySize(events), 1);
                assert.equal(events[0], "SubscriptionSuspended");
            });
        });

        describe('fifth when block with AND trigger', () => {
            it('should have 2 triggers (AND condition)', () => {
                const triggers = jsonPath(model, "$.flows.PaymentsFlow.whens[4].triggers");
                assert.equal(arraySize(triggers), 2);
                assert.equal(triggers[0], "PaymentSucceeded");
                assert.equal(triggers[1], "BillingCycleEnded");
            });

            it('should have correct command', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.whens[4].command"), "recordPayment");
            });

            it('should have correct events', () => {
                const events = jsonPath(model, "$.flows.PaymentsFlow.whens[4].events");
                assert.equal(arraySize(events), 1);
                assert.equal(events[0], "PaymentRecorded");
            });
        });

        describe('sixth when block', () => {
            it('should have correct triggers', () => {
                const triggers = jsonPath(model, "$.flows.PaymentsFlow.whens[5].triggers");
                assert.equal(arraySize(triggers), 1);
                assert.equal(triggers[0], "PaymentTimeout");
            });

            it('should have correct command', () => {
                assert.equal(jsonPath(model, "$.flows.PaymentsFlow.whens[5].command"), "cancelRenewal");
            });

            it('should have correct events', () => {
                const events = jsonPath(model, "$.flows.PaymentsFlow.whens[5].events");
                assert.equal(arraySize(events), 1);
                assert.equal(events[0], "RenewalCancelled");
            });
        });
    });

    describe('end block', () => {
        it('should have outcomes', () => {
            const outcomes = jsonPath(model, "$.flows.PaymentsFlow.end.outcomes");
            assert.notEqual(outcomes, null);
        });

        it('should have 3 outcome types', () => {
            const outcomes = jsonPath(model, "$.flows.PaymentsFlow.end.outcomes");
            assert.equal(mapSize(outcomes), 3);
        });

        it('should have completed outcome', () => {
            const completed = jsonPath(model, "$.flows.PaymentsFlow.end.outcomes.completed");
            assert.equal(arraySize(completed), 1);
            assert.equal(completed[0], "PaymentRecorded");
        });

        it('should have suspended outcome', () => {
            const suspended = jsonPath(model, "$.flows.PaymentsFlow.end.outcomes.suspended");
            assert.equal(arraySize(suspended), 1);
            assert.equal(suspended[0], "SubscriptionSuspended");
        });

        it('should have cancelled outcome', () => {
            const cancelled = jsonPath(model, "$.flows.PaymentsFlow.end.outcomes.cancelled");
            assert.equal(arraySize(cancelled), 1);
            assert.equal(cancelled[0], "RenewalCancelled");
        });
    });
});

