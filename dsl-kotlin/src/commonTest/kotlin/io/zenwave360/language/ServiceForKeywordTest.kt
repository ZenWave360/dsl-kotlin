package io.zenwave360.language

import io.zenwave360.language.utils.JSONPath
import io.zenwave360.language.zdl.ZdlParser
import io.zenwave360.language.zdl.formatter.ZdlFormatter
import io.zenwave360.language.zfl.ZflParser
import io.zenwave360.language.zfl.formatter.ZflFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The `for` keyword before a service's aggregates is optional; the aggregates are not (in ZDL). */
class ServiceForKeywordTest {

    private fun zdl(header: String) = "@aggregate\nentity Order {}\n\n@aggregate\nentity Payment {}\n\n$header {\n    createOrder(Order) Order\n}\n"

    private fun zfl(header: String) = "systems {\n    Orders {\n        $header {\n            commands: createOrder\n        }\n    }\n}\n"

    @Test
    fun zdl_parses_services_with_and_without_for() {
        listOf("service OrdersService for (Order, Payment)", "service OrdersService (Order, Payment)", "service OrdersService for(Order, Payment)", "service OrdersService(Order, Payment)").forEach { header ->
            val model = ZdlParser().parseModel(zdl(header))
            assertTrue(model.getProblems().isEmpty(), "$header: ${model.getProblems()}")
            assertEquals(listOf("Order", "Payment"), JSONPath.get(model, "$.services.OrdersService.aggregates"), header)
            assertEquals(1, (JSONPath.get(model, "$.services.OrdersService.methods") as Map<*, *>?)?.size, header)
        }
    }

    @Test
    fun zdl_aggregates_are_still_required() {
        val model = ZdlParser().parseModel(zdl("service OrdersService for"))
        assertTrue(model.getProblems().isNotEmpty())
    }

    @Test
    fun zdl_formatter_keeps_the_for_keyword_as_written_and_spaces_it() {
        assertEquals(zdl("service OrdersService for (Order)"), ZdlFormatter().format(zdl("service OrdersService for(Order)")))
        assertEquals(zdl("service OrdersService (Order)"), ZdlFormatter().format(zdl("service OrdersService(Order)")))
        assertEquals(zdl("service OrdersService (Order)"), ZdlFormatter().format(zdl("service OrdersService (Order)")))
        val formatted = ZdlFormatter().format(zdl("service OrdersService   for   (Order)"))
        assertEquals(zdl("service OrdersService for (Order)"), formatted)
        assertEquals(formatted, ZdlFormatter().format(formatted))
    }

    @Test
    fun zfl_parses_system_services_with_and_without_for() {
        listOf("service OrdersService for (Order, Payment)", "service OrdersService (Order, Payment)", "service OrdersService(Order, Payment)").forEach { header ->
            val model = ZflParser().parseModel(zfl(header))
            assertTrue(model.getProblems().isEmpty(), "$header: ${model.getProblems()}")
            assertEquals(listOf("Order", "Payment"), JSONPath.get(model, "$.systems.Orders.services.OrdersService.aggregates"), header)
        }
        val without = ZflParser().parseModel("systems {\n    Orders {\n        service OrdersService {\n            commands: createOrder\n        }\n    }\n}\n")
        assertTrue(without.getProblems().isEmpty(), without.getProblems().toString())
    }

    @Test
    fun zfl_formatter_keeps_the_for_keyword_as_written_and_spaces_it() {
        assertEquals(zfl("service OrdersService for (Order)"), ZflFormatter().format(zfl("service OrdersService for(Order)")))
        assertEquals(zfl("service OrdersService (Order)"), ZflFormatter().format(zfl("service OrdersService(Order)")))
        assertEquals(zfl("service OrdersService (Order)"), ZflFormatter().format(zfl("service OrdersService (Order)")))
    }
}
