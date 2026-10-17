package io.zenwave360.language

// Fixtures are shared with dsl-kotlin: dsl-kotlin/src/commonTest/resources.
expect fun readTestFile(fileName: String): String

// Fixtures owned by dsl-views: dsl-views/src/commonTest/resources.
expect fun readViewsTestFile(fileName: String): String
