package io.zenwave360.zdl.internal

import io.zenwave360.language.utils.JSONPath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class JSONPathTest {

    private val json = readTestFile("complete.json")

    private val jsonMap = Json.parseToJsonElement(json).toMap()

    private fun JsonElement.toMap(): Any? = when (this) {
        is JsonObject -> this.mapValues { it.value.toMap() }
        is JsonArray -> {
            val list = this.map { it.toMap() }
            // Convert to IntArray if all elements are integers
            if (list.isNotEmpty() && list.all { it is Int }) {
                list.filterIsInstance<Int>().toIntArray()
            } else {
                list
            }
        }
        is JsonPrimitive -> when {
            this.isString -> this.content
            this.content == "true" -> true
            this.content == "false" -> false
            this.content == "null" -> null
            this.content.toIntOrNull() != null -> this.content.toInt()
            this.content.toDoubleOrNull() != null -> this.content.toDouble()
            else -> this.content
        }
    }
    
    @Test
    fun testValidatorExpressions() {
        // Test all JSONPath expressions used in validators and source code
        val relationships = JSONPath.get(jsonMap, "$.relationships[*][*]", listOf<Map<String, Any?>>())
        assertTrue(relationships.isNotEmpty())
        
        val allFields = JSONPath.get(jsonMap, "$..fields[*]", listOf<Map<String, Any?>>())
        assertTrue(allFields.size > 20)
        
        // Test accessing properties from relationship objects
        if (relationships.isNotEmpty()) {
            val firstRelationship = relationships.first()
            assertNotNull(JSONPath.get(firstRelationship, "$.type"))
            assertNotNull(JSONPath.get(firstRelationship, "$.name"))
            assertNotNull(JSONPath.get(firstRelationship, "$.from"))
            assertNotNull(JSONPath.get(firstRelationship, "$.to"))
        }
        
        // Test service method access patterns
        val serviceMethods = JSONPath.get(jsonMap, "$.services.OrdersService.methods[*]", listOf<Map<String, Any?>>())
        assertTrue(serviceMethods.isNotEmpty())
        
        // Test location access with special bracket notation
        val location = JSONPath.get<IntArray>(jsonMap, "$.locations.['entities.Customer.body']")
        assertNotNull(location)
    }

    @Test
    fun testBasicPropertyAccess() {
        assertEquals("ZenWave Online Food Delivery - Orders Module.", JSONPath.get(jsonMap, "$.javadoc"))
        assertEquals("io.zenwave360.example.orders", JSONPath.get(jsonMap, "$.config.basePackage"))
        assertEquals("mongodb", JSONPath.get(jsonMap, "$.config.persistence"))
    }

    @Test
    fun testArrayAccess() {
        assertEquals("com.example:artifact:RELEASE", JSONPath.get(jsonMap, "$.imports[0]"))
        assertEquals(listOf("com.example:artifact:RELEASE"), JSONPath.get(jsonMap, "$.imports"))
    }

    @Test
    fun testNestedMapAccess() {
        assertEquals("asyncapi", JSONPath.get(jsonMap, "$.apis.default.type"))
        assertEquals("provider", JSONPath.get(jsonMap, "$.apis.default.role"))
        assertEquals("orders/src/main/resources/apis/asyncapi.yml", JSONPath.get(jsonMap, "$.apis.default.config.uri"))
        assertEquals("client", JSONPath.get(jsonMap, "$.apis.RestaurantsAsyncAPI.role"))
    }

    @Test
    fun testMapSizeAccess() {
        assertEquals(3, JSONPath.get<Map<*, *>>(jsonMap, "$.apis")?.size ?: 0)
        assertEquals(6, JSONPath.get<Map<*, *>>(jsonMap, "$.entities")?.size ?: 0)
        assertEquals(5, JSONPath.get<Map<*, *>>(jsonMap, "$.entities.CustomerOrder.fields")?.size ?: 0)
    }

    @Test
    fun testBooleanValues() {
        assertFalse(JSONPath.get(jsonMap, "$.enums.OrderStatus.hasValue", false))
        assertTrue(JSONPath.get(jsonMap, "$.enums.EnumWithValue.hasValue", false))
        assertTrue(JSONPath.get(jsonMap, "$.entities.CustomerOrder.options.aggregate", false))
        assertFalse(JSONPath.get(jsonMap, "$.entities.CustomerOrder.fields.orderTime.isEnum", true))
    }

    @Test
    fun testDefaultValues() {
        assertEquals("default", JSONPath.get(jsonMap, "$.nonexistent", "default"))
        assertEquals(emptyList<String>(), JSONPath.get(jsonMap, "$.nonexistent", emptyList<String>()))
        assertEquals(emptyMap<String, Any>(), JSONPath.get(jsonMap, "$.nonexistent", emptyMap<String, Any>()))
    }

    @Test
    fun testNullValues() {
        assertNull(JSONPath.get(jsonMap, "$.entities.CustomerOrder.javadoc"))
        assertNull(JSONPath.get(jsonMap, "$.nonexistent.path"))
    }

    @Test
    fun testComplexNestedAccess() {
        assertEquals("Instant", JSONPath.get(jsonMap, "$.entities.CustomerOrder.fields.orderTime.type"))
        assertEquals("Instant.now()", JSONPath.get(jsonMap, "$.entities.CustomerOrder.fields.orderTime.initialValue"))
        assertNotNull(JSONPath.get<Any>(jsonMap, "$.entities.CustomerOrder.fields.orderTime.validations.required"))
        assertEquals("orderTime javadoc", JSONPath.get(jsonMap, "$.entities.CustomerOrder.fields.orderTime.javadoc"))
    }

    @Test
    fun testServiceMethods() {
        assertEquals(listOf("CustomerOrder"), JSONPath.get(jsonMap, "$.services.OrdersService.aggregates"))
        assertEquals(
            listOf("CustomerOrder", "Aggregate2"),
            JSONPath.get(jsonMap, "$.services.OrdersService2.aggregates")
        )
        assertEquals(
            2,
            JSONPath.get<Map<*, *>>(jsonMap, "$.services.OrdersService.methods.cancelOrder.options")?.size ?: 0
        )
        assertEquals(
            2,
            JSONPath.get<List<*>>(jsonMap, "$.services.OrdersService.methods.cancelOrder.optionsList")?.size ?: 0
        )
        assertEquals(
            "/search",
            JSONPath.get(jsonMap, "$.services.OrdersService.methods.searchOrders.options.post.path")
        )
        assertEquals(
            "String",
            JSONPath.get(jsonMap, "$.services.OrdersService.methods.searchOrders.options.post.params.param1")
        )
    }

    @Test
    fun testRelationships() {
        assertEquals(
            "Customer",
            JSONPath.get(jsonMap, "$.relationships.OneToOne.OneToOne_Customer{address}_Address{customer}.from")
        )
        assertEquals(
            true,
            JSONPath.get(jsonMap, "$.relationships.OneToOne.OneToOne_Customer{address}_Address{customer}.toOptions.Id")
        )
    }

    @Test
    fun testProblemsArray() {
        val problems = JSONPath.get(jsonMap, "$.problems", listOf<Any>())
        assertEquals(0, problems.size)
    }

    @Test
    fun testLocationsWithSpecialKeys() {
        val location = JSONPath.get<IntArray>(jsonMap, "$.locations.['entities.Customer.body']")
        assertEquals(
            intArrayOf(
                2967,
                3283,
                85,
                45,
                98,
                4
            ).toList(), location?.toList()
        )
    }

    @Test
    fun testWildcardAccess() {
        // Test [*] patterns used in validators
        val apisValues = JSONPath.get<List<*>>(jsonMap, "$.apis[*]")
        assertEquals(3, apisValues?.size ?: 0)

        val servicesValues = JSONPath.get<List<*>>(jsonMap, "$.services[*]")
        assertEquals(2, servicesValues?.size ?: 0)
    }

    @Test
    fun testDeepWildcardPatterns() {
        assertEquals(6, JSONPath.get(jsonMap, "relationships[*][*]", listOf<Map<*, *>>()).size)
        assertEquals(25, JSONPath.get(jsonMap, "entities[*].fields[*]", listOf<Map<*, *>>()).size)
    }

    @Test
    fun testEdgeCases() {
        // Empty path
        assertEquals(jsonMap, JSONPath.get(jsonMap, "$"))
        assertEquals(jsonMap, JSONPath.get(jsonMap, ""))

        // Null source
        assertNull(JSONPath.get(null, "$.any.path"))
        assertEquals("default", JSONPath.get(null, "$.any.path", "default"))

        // Invalid paths
        assertNull(JSONPath.get(jsonMap, "$.invalid[999]"))
        assertNull(JSONPath.get(jsonMap, "$.invalid.deeply.nested.path"))
    }

    @Test
    fun testTypeCoercion() {
        // Test that the generic type system works correctly
        val stringValue: String? = JSONPath.get(jsonMap, "$.javadoc")
        assertEquals("ZenWave Online Food Delivery - Orders Module.", stringValue)

        val listValue: List<String>? = JSONPath.get(jsonMap, "$.imports")
        assertEquals(listOf("com.example:artifact:RELEASE"), listValue)

        val mapValue: Map<String, Any>? = JSONPath.get(jsonMap, "$.config")
        assertEquals("io.zenwave360.example.orders", mapValue?.get("basePackage"))
    }

    @Test
    fun testRecursiveDescentPatterns() {
        // Test recursive descent with wildcard
        val allFields = JSONPath.get(jsonMap, "$..fields[*]", listOf<Map<String, Any?>>())
        assertEquals(109, allFields.size)
        
        // Test recursive descent to specific property
        val allNames = JSONPath.get(jsonMap, "$..name", listOf<String>())
        assertTrue(allNames.size > 10)
        
        // Test recursive descent with array access
        val allTypes = JSONPath.get(jsonMap, "$..fields[*].type", listOf<String>())
        assertEquals(109, allTypes.size)
    }
}
