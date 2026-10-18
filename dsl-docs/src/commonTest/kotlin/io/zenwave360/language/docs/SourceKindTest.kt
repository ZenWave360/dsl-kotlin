package io.zenwave360.language.docs

import kotlin.test.Test
import kotlin.test.assertEquals

class SourceKindTest {

    @Test
    fun detectsOpenApiAndSwaggerFromTheTopLevelKey() {
        assertEquals(SourceKind.OPENAPI, SourceKind.ofApi("openapi: 3.0.1\ninfo: { title: Orders }"))
        assertEquals(SourceKind.OPENAPI, SourceKind.ofApi("swagger: '2.0'\ninfo: { title: Orders }"))
        assertEquals(SourceKind.OPENAPI, SourceKind.ofApi("""{ "openapi": "3.1.0", "info": { "title": "Orders" } }"""))
    }

    @Test
    fun detectsAsyncApi() {
        assertEquals(SourceKind.ASYNCAPI, SourceKind.ofApi("asyncapi: 3.0.0\ninfo: { title: Events }"))
        assertEquals(SourceKind.ASYNCAPI, SourceKind.ofApi("asyncapi: '2.6.0'"))
    }

    @Test
    fun returnsNullForAnythingElse() {
        assertEquals(null, SourceKind.ofApi(""))
        assertEquals(null, SourceKind.ofApi("info: { title: Orders }"))
        assertEquals(null, SourceKind.ofApi("components:\n  schemas:\n    openapi: {}"))
        assertEquals(null, SourceKind.ofApi("openapi: [unclosed"))
    }
}
