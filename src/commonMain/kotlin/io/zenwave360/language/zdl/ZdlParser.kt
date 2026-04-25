package io.zenwave360.language.zdl

import io.zenwave360.language.antlr.ZdlLexer
import io.zenwave360.language.zdl.internal.ZdlListenerImpl
import io.zenwave360.language.zdl.internal.ZdlModelPostProcessor
import io.zenwave360.language.zdl.internal.ZdlModelValidator
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.tree.ParseTreeWalker

data class ZdlParseResult(
    val tree: io.zenwave360.language.antlr.ZdlParser.ZdlContext,
    val tokens: CommonTokenStream
)

class ZdlParser {

    companion object {
        val STANDARD_FIELD_TYPES = listOf(
            "String", "Integer", "Long", "int", "long", "BigDecimal", "Float", "float",
            "Double", "double", "Enum", "Boolean", "boolean", "Map", "LocalDate",
            "LocalDateTime", "ZonedDate", "ZonedDateTime", "Instant", "Duration", "UUID",
            "byte", "byte[]", "Blob", "AnyBlob", "ImageBlob", "TextBlob"
        )
        val STANDARD_VALIDATIONS = listOf("required", "unique", "min", "max", "minlength", "maxlength", "pattern")
    }

    private var standardFieldTypes: List<String> = STANDARD_FIELD_TYPES
    private var extraFieldTypes: List<String> = emptyList()

    fun withStandardFieldTypes(standardFieldTypes: List<String>): ZdlParser {
        this.standardFieldTypes = standardFieldTypes
        return this
    }

    fun withExtraFieldTypes(extraFieldTypes: List<String>): ZdlParser {
        this.extraFieldTypes = extraFieldTypes
        return this
    }

    fun parse(input: String): ZdlParseResult {
        val zdl = CharStreams.fromString(input)
        val lexer = ZdlLexer(zdl)
        val tokens = CommonTokenStream(lexer)
        val parser = io.zenwave360.language.antlr.ZdlParser(tokens)
        val zdlRoot = parser.zdl()
        return ZdlParseResult(
            tree = zdlRoot,
            tokens = tokens
        )
    }

    fun parseModel(model: String): ZdlModel {
        val parseResult = parse(model)
        val listener = ZdlListenerImpl()
        ParseTreeWalker.DEFAULT.walk(listener, parseResult.tree)

        var zdlModel = listener.model
        zdlModel = ZdlModelPostProcessor.postProcess(zdlModel)
        try {
            zdlModel = ZdlModelValidator()
                .withStandardFieldTypes(standardFieldTypes)
                .withExtraFieldTypes(extraFieldTypes)
                .validate(zdlModel)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return zdlModel
    }
}

