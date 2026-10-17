package io.zenwave360.language.docs

/** The input of the Arcadia Editions orders-checkout service: every kind of source dsl-docs reads. */
fun arcadiaOrdersCheckout(): List<DocsSource> = listOf(
    DocsSource(SourceKind.ZDL, "domain-model.zdl", readTestFile("arcadia-editions/orders-checkout-api/domain-model.zdl")),
    DocsSource(SourceKind.ZFL, "../business-flows/place-order-flow.zfl", readTestFile("arcadia-editions/business-flows/place-order-flow.zfl")),
    DocsSource(SourceKind.OPENAPI, "openapi.yml", readTestFile("arcadia-editions/orders-checkout-api/openapi.yml")),
    DocsSource(SourceKind.ASYNCAPI, "asyncapi.yml", readTestFile("arcadia-editions/orders-checkout-api/asyncapi.yml")),
    DocsSource(SourceKind.ASYNCAPI, "asyncapi-client.yml", readTestFile("arcadia-editions/orders-checkout-api/asyncapi-client.yml")),
)

/** The six modules of the Arcadia Editions modulith: one ZDL file per module. */
val arcadiaModulithFiles = listOf(
    "catalog-inventory-domain-model.zdl",
    "catalog-products-domain-model.zdl",
    "fulfillment-shipping-domain-model.zdl",
    "notifications-consumer-domain-model.zdl",
    "orders-checkout-domain-model.zdl",
    "payments-processing-domain-model.zdl",
)

fun arcadiaModulith(): List<DocsSource> =
    arcadiaModulithFiles.map { DocsSource(SourceKind.ZDL, it, readTestFile("arcadia-editions-modulith/$it")) }

/** Reads the files the orders-checkout specs refer to (Avro schemas), relative to the service folder, from the test resources. */
val arcadiaFileReader = DocsFileReader { path ->
    try {
        readTestFile("arcadia-editions/orders-checkout-api/$path")
    } catch (e: Throwable) {
        null
    }
}
