package io.zenwave360.language.docs

/** The input of the Arcadia Editions orders-checkout service: every kind of source dsl-docs reads. */
fun arcadiaOrdersCheckout(): List<DocsSource> = listOf(
    DocsSource(SourceKind.ZDL, "domain-model.zdl", readTestFile("arcadia-editions/orders-checkout-api/domain-model.zdl")),
    DocsSource(SourceKind.ZFL, "../business-flows/place-order-flow.zfl", readTestFile("arcadia-editions/business-flows/place-order-flow.zfl")),
    DocsSource(SourceKind.OPENAPI, "openapi.yml", readTestFile("arcadia-editions/orders-checkout-api/openapi.yml")),
    DocsSource(SourceKind.ASYNCAPI, "asyncapi.yml", readTestFile("arcadia-editions/orders-checkout-api/asyncapi.yml")),
    DocsSource(SourceKind.ASYNCAPI, "asyncapi-client.yml", readTestFile("arcadia-editions/orders-checkout-api/asyncapi-client.yml")),
)
