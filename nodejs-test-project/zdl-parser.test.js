import { describe, it, before } from 'node:test';
import assert from 'node:assert/strict';
import { parseZdl } from '@zenwave360/zdl';
import fs from 'fs';
import { jsonPath, mapSize, arraySize } from './utils.js';

describe('ZDL Parser - Complete', () => {
    let model;

    before(() => {
        // Read and parse the complete.zdl file
        const zdlContent = fs.readFileSync('../src/commonTest/resources/complete.zdl', 'utf8');
        model = parseZdl(zdlContent);
    });

    describe('basic properties', () => {
        it('should parse javadoc correctly', () => {
            assert.equal(jsonPath(model, "$.javadoc"), "ZenWave Online Food Delivery - Orders Module.");
        });

        it('should parse imports correctly', () => {
            assert.equal(jsonPath(model, "$.imports[0].value"), "com.example:artifact:RELEASE");
        });
    });

    describe('config', () => {
        it('should have correct basePackage', () => {
            assert.equal(jsonPath(model, "$.config.basePackage"), "io.zenwave360.example.orders");
        });

        it('should have correct persistence', () => {
            assert.equal(jsonPath(model, "$.config.persistence"), "mongodb");
        });
    });

    describe('apis', () => {
        it('should have 3 apis', () => {
            assert.equal(mapSize(jsonPath(model, "$.apis")), 3);
        });

        it('should have correct default api configuration', () => {
            assert.equal(jsonPath(model, "$.apis.default.type"), "asyncapi");
            assert.equal(jsonPath(model, "$.apis.default.role"), "provider");
            assert.equal(jsonPath(model, "$.apis.default.config.uri"), "orders/src/main/resources/apis/asyncapi.yml");
        });

        it('should have correct RestaurantsAsyncAPI configuration', () => {
            assert.equal(jsonPath(model, "$.apis.RestaurantsAsyncAPI.type"), "asyncapi");
            assert.equal(jsonPath(model, "$.apis.RestaurantsAsyncAPI.role"), "client");
            assert.equal(jsonPath(model, "$.apis.RestaurantsAsyncAPI.config.uri"), "restaurants/src/main/resources/apis/asyncapi.yml");
        });
    });

    describe('plugins', () => {
        it('should have 5 plugins', () => {
            assert.equal(mapSize(jsonPath(model, "$.plugins")), 5);
        });

        it('should have correct ZDLToAsyncAPIPlugin config', () => {
            assert.equal(mapSize(jsonPath(model, "$.plugins.ZDLToAsyncAPIPlugin.config")), 3);
        });
    });

    describe('enums', () => {
        it('should have OrderStatus without value', () => {
            assert.equal(jsonPath(model, "$.enums.OrderStatus.hasValue", false), false);
        });

        it('should have EnumWithValue with value', () => {
            assert.equal(jsonPath(model, "$.enums.EnumWithValue.hasValue", false), true);
        });
    });

    describe('entities', () => {
        it('should have 6 entities', () => {
            assert.equal(mapSize(jsonPath(model, "$.entities")), 6);
        });

        describe('CustomerOrder', () => {
            it('should have correct basic properties', () => {
                assert.equal(jsonPath(model, "$.entities.CustomerOrder.name"), "CustomerOrder");
                assert.equal(jsonPath(model, "$.entities.CustomerOrder.tableName"), "customer_order");
                assert.equal(jsonPath(model, "$.entities.CustomerOrder.kebabCasePlural"), "customer-orders");
                assert.equal(jsonPath(model, "$.entities.CustomerOrder.options.aggregate"), true);
                assert.equal(jsonPath(model, "$.entities.CustomerOrder.javadoc"), null);
            });

            it('should have 5 fields', () => {
                assert.equal(mapSize(jsonPath(model, "$.entities.CustomerOrder.fields")), 5);
            });

            describe('orderTime field', () => {
                it('should have correct type and initial value', () => {
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.orderTime.type"), "Instant");
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.orderTime.initialValue"), "Instant.now()");
                });

                it('should have required validation', () => {
                    assert.notEqual(jsonPath(model, "$.entities.CustomerOrder.fields.orderTime.validations.required"), null);
                });

                it('should have correct javadoc', () => {
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.orderTime.javadoc"), "orderTime javadoc");
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.orderTime.comment"), "orderTime javadoc");
                });



                it('should have correct type flags', () => {
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.orderTime.isEnum"), false);
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.orderTime.isEntity"), false);
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.orderTime.isArray"), false);
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.orderTime.isComplexType"), false);
                });
            });

            describe('status field', () => {
                it('should have correct type and initial value', () => {
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.status.type"), "OrderStatus");
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.status.initialValue"), "OrderStatus.RECEIVED");
                });

                it('should have required validation', () => {
                    assert.notEqual(jsonPath(model, "$.entities.CustomerOrder.fields.status.validations.required"), null);
                });

                it('should have correct type flags', () => {
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.status.isEnum"), true);
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.status.isEntity"), false);
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.status.isArray"), false);
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.status.isComplexType"), true);
                });
            });

            describe('customerDetails field', () => {
                it('should have correct type', () => {
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.customerDetails.type"), "Customer");
                });

                it('should not have initial value or required validation', () => {
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.customerDetails.initialValue"), null);
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.customerDetails.validations.required"), null);
                });

                it('should have correct type flags', () => {
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.customerDetails.isEnum"), false);
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.customerDetails.isEntity"), true);
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.customerDetails.isArray"), false);
                    assert.equal(jsonPath(model, "$.entities.CustomerOrder.fields.customerDetails.isComplexType"), true);
                });
            });
        });
    });

    describe('relationships', () => {
        it('should have correct OneToOne relationship', () => {
            assert.equal(jsonPath(model, "$.relationships.OneToOne.OneToOne_Customer{address}_Address{customer}.from"), "Customer");
            assert.equal(jsonPath(model, "$.relationships.OneToOne.OneToOne_Customer{address}_Address{customer}.toOptions.Id"), true);
        });
    });

    describe('services', () => {
        it('should have 2 services', () => {
            assert.equal(mapSize(jsonPath(model, "$.services")), 2);
        });

        describe('OrdersService', () => {
            it('should have correct aggregates', () => {
                const ordersServiceAggregates = jsonPath(model, "$.services.OrdersService.aggregates");
                assert.equal(arraySize(ordersServiceAggregates), 1);
                assert.equal(ordersServiceAggregates[0], "CustomerOrder");
            });

            it('should have 7 methods', () => {
                assert.equal(mapSize(jsonPath(model, "$.services.OrdersService.methods")), 7);
            });

            describe('updateKitchenStatus method', () => {
                it('should have correct withEvents', () => {
                    const updateKitchenStatusEvents = jsonPath(model, "$.services.OrdersService.methods.updateKitchenStatus.withEvents");
                    assert.equal(arraySize(updateKitchenStatusEvents), 2);
                    assert.equal(updateKitchenStatusEvents[0], "OrderEvent");
                    assert.equal(updateKitchenStatusEvents[1], "OrderStatusUpdated");
                });

                it('should have correct asyncapi options', () => {
                    assert.equal(jsonPath(model, "$.services.OrdersService.methods.updateKitchenStatus.options.asyncapi.api"), "RestaurantsAsyncAPI");
                    assert.equal(jsonPath(model, "$.services.OrdersService.methods.updateKitchenStatus.options.asyncapi.channel"), "KitchenOrdersStatusChannel");
                    assert.equal(arraySize(jsonPath(model, "$.services.OrdersService.methods.updateKitchenStatus.optionsList")), 1);
                });
            });

            describe('cancelOrder method', () => {
                it('should have correct options', () => {
                    assert.equal(mapSize(jsonPath(model, "$.services.OrdersService.methods.cancelOrder.options")), 2);
                    assert.equal(arraySize(jsonPath(model, "$.services.OrdersService.methods.cancelOrder.optionsList")), 2);
                });
            });

            describe('searchOrders method', () => {
                it('should have correct post options', () => {
                    assert.equal(jsonPath(model, "$.services.OrdersService.methods.searchOrders.options.post.path"), "/search");
                    assert.equal(jsonPath(model, "$.services.OrdersService.methods.searchOrders.options.post.params.param1"), "String");
                });
            });

            describe('parameterIsOptional', () => {
                it('should have correct values for different methods', () => {
                    assert.equal(jsonPath(model, "$.services.OrdersService.methods.createOrder.parameterIsOptional", false), false);
                    assert.equal(jsonPath(model, "$.services.OrdersService.methods.updateOrder.parameterIsOptional", false), false);
                    assert.equal(jsonPath(model, "$.services.OrdersService.methods.searchOrders.parameterIsOptional", false), true);
                    assert.equal(jsonPath(model, "$.services.OrdersService.methods.getCustomerOrder.parameterIsOptional", false), false);
                });
            });
        });

        describe('OrdersService2', () => {
            it('should have correct aggregates', () => {
                const ordersService2Aggregates = jsonPath(model, "$.services.OrdersService2.aggregates");
                assert.equal(arraySize(ordersService2Aggregates), 2);
                assert.equal(ordersService2Aggregates[0], "CustomerOrder");
                assert.equal(ordersService2Aggregates[1], "Aggregate2");
            });
        });
    });

    describe('annotations', () => {
        it('should parse array annotations correctly', () => {
            assert.equal(jsonPath(model, "$.inputs.CustomerOrderInput.options.array_annotation[0]"), "item1");
            assert.equal(jsonPath(model, "$.inputs.CustomerOrderInput.options.array2_annotation[0]"), "item1");
        });

        it('should parse object annotations correctly', () => {
            assert.equal(jsonPath(model, "$.inputs.CustomerOrderInput.options.object_annotation.item1"), "value1");
            assert.equal(jsonPath(model, "$.inputs.CustomerOrderInput.options.object_annotation_pairs.item1"), "value1");
        });

        it('should parse nested object annotations correctly', () => {
            assert.equal(jsonPath(model, "$.inputs.CustomerOrderInput.options.object_annotation_nested_array.item3[1]"), "value2");
        });
    });
});
