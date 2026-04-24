package io.zenwave360.zdl.internal

import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zdl.ZdlModel
import kotlin.test.*

class ZdlListenerKotlinTest {

    @Test
    fun parseZdl_SuffixJavadoc() {
        val model = parseZdl("suffix_javadoc.zdl")
        assertEquals(JSONPath.get(model, "$.entities.A.fields.name.javadoc"), "name javadoc")
        assertEquals(JSONPath.get(model, "$.entities.A.fields.count.javadoc"), "count javadoc")
//         println(model)
    }

    @Test
    fun parseZdl_Composed() {
        val model = parseZdl("composed.zdl")
        // println(model)
    }

    @Test
    fun getFindLocation() {
        val model = parseZdl("complete.zdl")
        var location: String?

        location = model.getLocation(86, 12)
        assertEquals("entities.Customer.fields.customerId.name", location)

        location = model.getLocation(86, 20)
        assertEquals("entities.Customer.fields.customerId.type", location)

        location = model.getLocation(86, 30)
        assertEquals("entities.Customer.fields.customerId.validations.required", location)

        location = model.getLocation(86, 25)
        assertEquals("entities.Customer.fields.customerId.validations.required", location)

        location = model.getLocation(86, 33)
        assertEquals("entities.Customer.fields.customerId.validations.required", location)

        location = model.getLocation(86, 34)
        assertEquals("entities.Customer.body", location)
    }

    @Test
    fun parseZdl_CompleteZdl() {
        val model = parseZdl("complete.zdl")

        assertEquals("ZenWave Online Food Delivery - Orders Module.", JSONPath.get(model, "$.javadoc"))
        assertEquals("com.example:artifact:RELEASE", JSONPath.get(model, "$.imports[0].value"))

        // CONFIG
        assertEquals("io.zenwave360.example.orders", JSONPath.get(model, "$.config.basePackage"))
        assertEquals("mongodb", JSONPath.get(model, "$.config.persistence"))

        // APIS
        assertEquals(3, (JSONPath.get(model, "$.apis") as? Map<*, *>)?.size ?: 0)
        assertEquals("asyncapi", JSONPath.get(model, "$.apis.default.type"))
        assertEquals("provider", JSONPath.get(model, "$.apis.default.role"))
        assertEquals("orders/src/main/resources/apis/asyncapi.yml", JSONPath.get(model, "$.apis.default.config.uri"))
        assertEquals("asyncapi", JSONPath.get(model, "$.apis.RestaurantsAsyncAPI.type"))
        assertEquals("client", JSONPath.get(model, "$.apis.RestaurantsAsyncAPI.role"))
        assertEquals("restaurants/src/main/resources/apis/asyncapi.yml", JSONPath.get(model, "$.apis.RestaurantsAsyncAPI.config.uri"))

        // PLUGINS
        assertEquals(5, (JSONPath.get(model, "$.plugins") as? Map<*, *>)?.size ?: 0)
        assertEquals(3, (JSONPath.get(model, "$.plugins.ZDLToAsyncAPIPlugin.config") as? Map<*, *>)?.size ?: 0)

        // ENUMS
        assertFalse(JSONPath.get(model, "$.enums.OrderStatus.hasValue", false) as Boolean)
        assertTrue(JSONPath.get(model, "$.enums.EnumWithValue.hasValue", false) as Boolean)

        // ENTITIES
        assertEquals(6, (JSONPath.get(model, "$.entities") as? Map<*, *>)?.size ?: 0)
        assertEquals("CustomerOrder", JSONPath.get(model, "$.entities.CustomerOrder.name"))
        assertEquals("customer_order", JSONPath.get(model, "$.entities.CustomerOrder.tableName"))
        assertEquals("customer-orders", JSONPath.get(model, "$.entities.CustomerOrder.kebabCasePlural"))
        assertEquals(true, JSONPath.get(model, "$.entities.CustomerOrder.options.aggregate"))
        assertNull(JSONPath.get(model, "$.entities.CustomerOrder.javadoc"))

        assertEquals(5, (JSONPath.get(model, "$.entities.CustomerOrder.fields") as? Map<*, *>)?.size ?: 0)
        assertEquals("Instant", JSONPath.get(model, "$.entities.CustomerOrder.fields.orderTime.type"))
        assertEquals("Instant.now()", JSONPath.get(model, "$.entities.CustomerOrder.fields.orderTime.initialValue"))
        assertNotNull(JSONPath.get(model, "$.entities.CustomerOrder.fields.orderTime.validations.required"))
        assertEquals("orderTime javadoc", JSONPath.get(model, "$.entities.CustomerOrder.fields.orderTime.javadoc"))
        assertEquals("orderTime javadoc", JSONPath.get(model, "$.entities.CustomerOrder.fields.orderTime.comment"))
        assertEquals(false, JSONPath.get(model, "$.entities.CustomerOrder.fields.orderTime.isEnum"))
        assertEquals(false, JSONPath.get(model, "$.entities.CustomerOrder.fields.orderTime.isEntity"))
        assertEquals(false, JSONPath.get(model, "$.entities.CustomerOrder.fields.orderTime.isArray"))
        assertEquals(false, JSONPath.get(model, "$.entities.CustomerOrder.fields.orderTime.isComplexType"))

        assertEquals("OrderStatus", JSONPath.get(model, "$.entities.CustomerOrder.fields.status.type"))
        assertEquals("OrderStatus.RECEIVED", JSONPath.get(model, "$.entities.CustomerOrder.fields.status.initialValue"))
        assertNotNull(JSONPath.get(model, "$.entities.CustomerOrder.fields.status.validations.required"))
        assertEquals(true, JSONPath.get(model, "$.entities.CustomerOrder.fields.status.isEnum"))
        assertEquals(false, JSONPath.get(model, "$.entities.CustomerOrder.fields.status.isEntity"))
        assertEquals(false, JSONPath.get(model, "$.entities.CustomerOrder.fields.status.isArray"))
        assertEquals(true, JSONPath.get(model, "$.entities.CustomerOrder.fields.status.isComplexType"))

        assertEquals("Customer", JSONPath.get(model, "$.entities.CustomerOrder.fields.customerDetails.type"))
        assertNull(JSONPath.get(model, "$.entities.CustomerOrder.fields.customerDetails.initialValue"))
        assertNull(JSONPath.get(model, "$.entities.CustomerOrder.fields.customerDetails.validations.required"))
        assertEquals(false, JSONPath.get(model, "$.entities.CustomerOrder.fields.customerDetails.isEnum"))
        assertEquals(true, JSONPath.get(model, "$.entities.CustomerOrder.fields.customerDetails.isEntity"))
        assertEquals(false, JSONPath.get(model, "$.entities.CustomerOrder.fields.customerDetails.isArray"))
        assertEquals(true, JSONPath.get(model, "$.entities.CustomerOrder.fields.customerDetails.isComplexType"))

        // RELATIONSHIPS
        assertEquals("Customer", JSONPath.get(model, "$.relationships.OneToOne.OneToOne_Customer{address}_Address{customer}.from"))
        assertEquals(true, JSONPath.get(model, "$.relationships.OneToOne.OneToOne_Customer{address}_Address{customer}.toOptions.Id"))

        // SERVICES
        assertEquals(2, (JSONPath.get(model, "$.services") as? Map<*, *>)?.size ?: 0)
        assertEquals(listOf("CustomerOrder"), JSONPath.get(model, "$.services.OrdersService.aggregates"))
        assertEquals(listOf("CustomerOrder", "Aggregate2"), JSONPath.get(model, "$.services.OrdersService2.aggregates"))
        assertEquals(7, (JSONPath.get(model, "$.services.OrdersService.methods") as? Map<*, *>)?.size ?: 0)

        assertEquals(listOf("OrderEvent", "OrderStatusUpdated"), JSONPath.get(model, "$.services.OrdersService.methods.updateKitchenStatus.withEvents"))
        assertEquals("RestaurantsAsyncAPI", JSONPath.get(model, "$.services.OrdersService.methods.updateKitchenStatus.options.asyncapi.api"))
        assertEquals("KitchenOrdersStatusChannel", JSONPath.get(model, "$.services.OrdersService.methods.updateKitchenStatus.options.asyncapi.channel"))
        assertEquals(1, (JSONPath.get(model, "$.services.OrdersService.methods.updateKitchenStatus.optionsList") as? List<*>)?.size ?: 0)

        assertEquals(2, (JSONPath.get(model, "$.services.OrdersService.methods.cancelOrder.options") as? Map<*, *>)?.size ?: 0)
        assertEquals(2, (JSONPath.get(model, "$.services.OrdersService.methods.cancelOrder.optionsList") as? List<*>)?.size ?: 0)

        assertEquals("/search", JSONPath.get(model, "$.services.OrdersService.methods.searchOrders.options.post.path"))
        assertEquals("String", JSONPath.get(model, "$.services.OrdersService.methods.searchOrders.options.post.params.param1"))


        // Test parameterIsOptional for different service methods
        assertEquals(false, JSONPath.get(model, "$.services.OrdersService.methods.createOrder.parameterIsOptional", false))
        assertEquals(false, JSONPath.get(model, "$.services.OrdersService.methods.updateOrder.parameterIsOptional", false))
        assertEquals(true, JSONPath.get(model, "$.services.OrdersService.methods.searchOrders.parameterIsOptional", false))
        assertEquals(false, JSONPath.get(model, "$.services.OrdersService.methods.getCustomerOrder.parameterIsOptional", false))

        // ANNOTATIONS
        assertEquals("item1", (JSONPath.get(model, "$.inputs.CustomerOrderInput.options.array_annotation[0]")))
        assertEquals("item1", (JSONPath.get(model, "$.inputs.CustomerOrderInput.options.array2_annotation[0]")))
        assertEquals("value1", JSONPath.get(model, "$.inputs.CustomerOrderInput.options.object_annotation.item1"))
        assertEquals("value1", JSONPath.get(model, "$.inputs.CustomerOrderInput.options.object_annotation_pairs.item1"))
        assertEquals("value2", JSONPath.get(model, "$.inputs.CustomerOrderInput.options.object_annotation_nested_array.item3[1]"))
    }

    @Test
    fun parseZdl_Apis_OldAndNewSyntaxProduceSameModel() {
        val oldSyntax = """
            apis {
                asyncapi(provider) OrdersAPI {
                    uri "orders/src/main/resources/apis/asyncapi.yml"
                }
                asyncapi(client) RestaurantsAsyncAPI {
                    uri "restaurants/src/main/resources/apis/asyncapi.yml"
                }
                openapi(provider) OrdersOpenAPI {
                    uri "orders/src/main/resources/apis/openapi.yml"
                }
            }
        """.trimIndent()

        val newSyntax = """
            apis {
                asyncapi provider OrdersAPI "orders/src/main/resources/apis/asyncapi.yml"
                asyncapi client RestaurantsAsyncAPI "restaurants/src/main/resources/apis/asyncapi.yml"
                openapi provider OrdersOpenAPI "orders/src/main/resources/apis/openapi.yml"
                zdl SharedKernel "shared-kernel/src/main/resources/models/shared-kernel.zdl"
            }
        """.trimIndent()

        val oldModel = ZdlParser().parseModel(oldSyntax)
        val newModel = ZdlParser().parseModel(newSyntax)

        assertEquals(0, (JSONPath.get(oldModel, "$.problems", emptyList<Any>()) as? List<*>)?.size ?: 0)
        assertEquals(0, (JSONPath.get(newModel, "$.problems", emptyList<Any>()) as? List<*>)?.size ?: 0)

        assertEquals(JSONPath.get<String>(oldModel, "$.apis.OrdersAPI.uri"), JSONPath.get(newModel, "$.apis.OrdersAPI.uri"))
        assertEquals(JSONPath.get<String>(oldModel, "$.apis.RestaurantsAsyncAPI.uri"), JSONPath.get(newModel, "$.apis.RestaurantsAsyncAPI.uri"))
        assertEquals(JSONPath.get<String>(oldModel, "$.apis.OrdersOpenAPI.uri"), JSONPath.get(newModel, "$.apis.OrdersOpenAPI.uri"))

        assertEquals("asyncapi", JSONPath.get(newModel, "$.apis.OrdersAPI.type"))
        assertEquals("provider", JSONPath.get(newModel, "$.apis.OrdersAPI.role"))
        assertEquals("orders/src/main/resources/apis/asyncapi.yml", JSONPath.get(newModel, "$.apis.OrdersAPI.config.uri"))
        assertEquals("openapi", JSONPath.get(newModel, "$.apis.OrdersOpenAPI.type"))
        assertEquals("orders/src/main/resources/apis/openapi.yml", JSONPath.get(newModel, "$.apis.OrdersOpenAPI.config.uri"))
        assertEquals("zdl", JSONPath.get(newModel, "$.apis.SharedKernel.type"))
        assertEquals("client", JSONPath.get(newModel, "$.apis.SharedKernel.role"))
        assertEquals("shared-kernel/src/main/resources/models/shared-kernel.zdl", JSONPath.get(newModel, "$.apis.SharedKernel.config.uri"))
    }

    @Test
    fun parseZdl_Legacy() {
        val model = parseZdl("legacy.jdl")
        assertEquals(1, (JSONPath.get(model, "$.services") as? Map<*, *>)?.size ?: 0)
        assertEquals(listOf("Customer", "Address"), JSONPath.get(model, "$.services.CustomerService.aggregates"))
        assertEquals(10, (JSONPath.get(model, "$.services.CustomerService.methods") as? Map<*, *>)?.size ?: 0)
    }

    @Test
    fun parseZdl_Problems() {
        val model = parseZdl("problems.zdl")
        val problems = JSONPath.get(model, "$.problems", emptyList<Any>()) as? List<*> ?: emptyList<Any>()
        assertEquals(14, problems.size)
    }

    @Test
    fun parseZdl_Problems_ExtraTypes() {
        val model = ZdlParser().withExtraFieldTypes(listOf("OrderStatusX")).parseModel(readFileContent("problems.zdl"))
        val problems = JSONPath.get(model, "$.problems", emptyList<Any>()) as? List<*> ?: emptyList<Any>()
        assertEquals(12, problems.size)
    }

    @Test
    fun parseZdl_Policies() {
        val model = parseZdl("policies.zdl")
        // println(model)
    }

    @Test
    fun parseZdl_NestedFields() {
        val model = parseZdl("nested-fields.zdl")
        // println(model)
    }

    @Test
    fun parseZdl_NestedId_Inputs_Outputs() {
        val model = parseZdl("nested-input-output-model.zdl")
        // println(model)
    }

    @Test
    fun parseZdl_UnrecognizedTokens() {
        val model = parseZdl("unrecognized-tokens.zdl")
        // println(model)
    }

    @Test
    fun parseZdl_ServiceLifecycle() {
        val model = parseZdl("service-lifecycle.zdl")
        val problems = JSONPath.get(model, "$.problems", emptyList<Any>()) as? List<*> ?: emptyList<Any>()
        assertEquals(0, problems.size, "expected no validation problems but got: $problems")

        // ── Entity @lifecycle annotation stored in options ────────────────────
        assertEquals("status",  JSONPath.get(model, "$.entities.Order.options.lifecycle.field"))
        assertEquals("DRAFT",   JSONPath.get(model, "$.entities.Order.options.lifecycle.initial"))
        assertEquals("status",  JSONPath.get(model, "$.entities.Invoice.options.lifecycle.field"))
        assertEquals("OPEN",    JSONPath.get(model, "$.entities.Invoice.options.lifecycle.initial"))
        // entity without @lifecycle must have no lifecycle option
        assertNull(JSONPath.get(model, "$.entities.Item.options.lifecycle"))

        // ── Single-aggregate service: OrderService ────────────────────────────
        // placeOrder: single from state
        assertEquals(listOf("DRAFT"), JSONPath.get(model, "$.services.OrderService.methods.placeOrder.transition.from"))
        assertEquals("PLACED",        JSONPath.get(model, "$.services.OrderService.methods.placeOrder.transition.to"))
        assertEquals(listOf("OrderPlaced"), JSONPath.get(model, "$.services.OrderService.methods.placeOrder.withEvents"))
        // @transition is also stored as a normal annotation in options.transition
        assertEquals("DRAFT",  JSONPath.get(model, "$.services.OrderService.methods.placeOrder.options.transition.from"))
        assertEquals("PLACED", JSONPath.get(model, "$.services.OrderService.methods.placeOrder.options.transition.to"))
        // addNote has no @transition — options.transition must be absent
        assertNull(JSONPath.get(model, "$.services.OrderService.methods.addNote.options.transition"))

        // cancelOrder: multiple from states
        assertEquals(listOf("DRAFT", "PLACED"), JSONPath.get(model, "$.services.OrderService.methods.cancelOrder.transition.from"))
        assertEquals("CANCELLED",               JSONPath.get(model, "$.services.OrderService.methods.cancelOrder.transition.to"))

        // addNote: no transition — from and to must be null
        assertNull(JSONPath.get(model, "$.services.OrderService.methods.addNote.transition.from"))
        assertNull(JSONPath.get(model, "$.services.OrderService.methods.addNote.transition.to"))

        // createOrder: no id, no transition — must parse cleanly
        assertNull(JSONPath.get(model, "$.services.OrderService.methods.createOrder.transition.from"))
        assertNull(JSONPath.get(model, "$.services.OrderService.methods.createOrder.transition.to"))

        // ── Multi-aggregate service: OrderInvoiceService ─────────────────────
        // placeOrderMixed returnType=Order → resolved as target
        assertEquals(listOf("DRAFT"), JSONPath.get(model, "$.services.OrderInvoiceService.methods.placeOrderMixed.transition.from"))
        assertEquals("PLACED",        JSONPath.get(model, "$.services.OrderInvoiceService.methods.placeOrderMixed.transition.to"))

        // payInvoice returnType=Invoice → resolved as target
        assertEquals(listOf("OPEN"), JSONPath.get(model, "$.services.OrderInvoiceService.methods.payInvoice.transition.from"))
        assertEquals("PAID",         JSONPath.get(model, "$.services.OrderInvoiceService.methods.payInvoice.transition.to"))

        // ── ItemService: no lifecycle, no transitions — must parse cleanly ────
        assertNull(JSONPath.get(model, "$.services.ItemService.methods.createItem.transition.from"))
        assertNull(JSONPath.get(model, "$.services.ItemService.methods.createItem.transition.to"))
    }

    @Test
    fun parseZdl_StateMachine() {
        val model = parseZdl("state-machine.zdl")
        val problems = JSONPath.get(model, "$.problems", emptyList<Any>()) as? List<*> ?: emptyList<Any>()
        assertEquals(0, problems.size, "expected no validation problems but got: $problems")

        // Aggregate with lifecycle
        assertEquals("OrderAggregate", JSONPath.get(model, "$.aggregates.OrderAggregate.name"))

        // @lifecycle is declared on the entity — visible in entity options
        assertEquals("status", JSONPath.get(model, "$.entities.Order.options.lifecycle.field"))
        assertEquals("DRAFT",  JSONPath.get(model, "$.entities.Order.options.lifecycle.initial"))

        // Post-processor copies entity lifecycle to the aggregate for backward compatibility
        assertEquals("status", JSONPath.get(model, "$.aggregates.OrderAggregate.lifecycle.field"))
        assertEquals("DRAFT",  JSONPath.get(model, "$.aggregates.OrderAggregate.lifecycle.initial"))

        // Command: single from-state transition
        assertEquals(listOf("DRAFT"), JSONPath.get(model, "$.aggregates.OrderAggregate.commands.placeOrder.transition.from"))
        assertEquals("PLACED", JSONPath.get(model, "$.aggregates.OrderAggregate.commands.placeOrder.transition.to"))
        assertEquals(listOf("OrderPlaced"), JSONPath.get(model, "$.aggregates.OrderAggregate.commands.placeOrder.withEvents"))
        // @transition is also stored as a normal annotation in options.transition
        assertEquals("DRAFT", JSONPath.get(model, "$.aggregates.OrderAggregate.commands.placeOrder.options.transition.from"))
        assertEquals("PLACED", JSONPath.get(model, "$.aggregates.OrderAggregate.commands.placeOrder.options.transition.to"))

        assertEquals(listOf("PLACED"), JSONPath.get(model, "$.aggregates.OrderAggregate.commands.confirmPayment.transition.from"))
        assertEquals("PAID", JSONPath.get(model, "$.aggregates.OrderAggregate.commands.confirmPayment.transition.to"))

        assertEquals(listOf("PAID"), JSONPath.get(model, "$.aggregates.OrderAggregate.commands.shipOrder.transition.from"))
        assertEquals("SHIPPED", JSONPath.get(model, "$.aggregates.OrderAggregate.commands.shipOrder.transition.to"))

        // Command: multiple from-states
        assertEquals(listOf("DRAFT", "PLACED"), JSONPath.get(model, "$.aggregates.OrderAggregate.commands.cancelOrder.transition.from"))
        assertEquals("CANCELLED", JSONPath.get(model, "$.aggregates.OrderAggregate.commands.cancelOrder.transition.to"))

        // Command without transition — from and to must be null
        assertNull(JSONPath.get(model, "$.aggregates.OrderAggregate.commands.addNote.transition.from"))
        assertNull(JSONPath.get(model, "$.aggregates.OrderAggregate.commands.addNote.transition.to"))
        assertEquals(listOf("NoteAdded"), JSONPath.get(model, "$.aggregates.OrderAggregate.commands.addNote.withEvents"))

        // Aggregate without lifecycle — lifecycle must be null, commands still parse normally
        assertNull(JSONPath.get(model, "$.aggregates.SimpleAggregate.lifecycle"))
        assertEquals(listOf("ItemCreated"), JSONPath.get(model, "$.aggregates.SimpleAggregate.commands.createItem.withEvents"))
        assertNull(JSONPath.get(model, "$.aggregates.SimpleAggregate.commands.createItem.transition.from"))
        assertNull(JSONPath.get(model, "$.aggregates.SimpleAggregate.commands.createItem.transition.to"))
    }

    private fun parseZdl(fileName: String): ZdlModel {
        val content = readFileContent(fileName)
        return ZdlParser().parseModel(content)
    }

    private fun readFileContent(fileName: String): String {
        return readTestFile(fileName)
    }

    fun printMapAsJson(map: Map<String, Any?>, indent: String = ""): String {
        return buildString {
            append("{\n")
            map.entries.forEachIndexed { index, (key, value) ->
                append("$indent  \"$key\": ")
                when (value) {
                    is IntArray -> append(value.contentToString())
                    is Map<*, *> -> append(printMapAsJson(value as Map<String, Any?>, "$indent  "))
                    is List<*> -> append(printListAsJson(value, "$indent  "))
                    is String -> append("\"$value\"")
                    null -> append("null")
                    else -> append("\"$value\"")
                }
                if (index < map.size - 1) append(",")
                append("\n")
            }
            append("$indent}")
        }
    }

    fun printListAsJson(list: List<*>, indent: String = ""): String {
        return buildString {
            append("[\n")
            list.forEachIndexed { i, item ->
                append("$indent  ")
                when (item) {
                    is Map<*, *> -> append(printMapAsJson(item as Map<String, Any?>, "$indent  "))
                    is List<*> -> append(printListAsJson(item, "$indent  "))
                    is String -> append("\"$item\"")
                    null -> append("null")
                    else -> append("\"$item\"")
                }
                if (i < list.size - 1) append(",")
                append("\n")
            }
            append("$indent]")
        }
    }

    fun printAsJson(obj: Any?, indent: String = ""): String {
        return when (obj) {
            is Map<*, *> -> printMapAsJson(obj as Map<String, Any?>, indent)
            is List<*> -> printListAsJson(obj, indent)
            is String -> "\"$obj\""
            null -> "null"
            else -> "\"$obj\""
        }
    }

}

