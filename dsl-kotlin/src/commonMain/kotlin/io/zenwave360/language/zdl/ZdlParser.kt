package io.zenwave360.language.zdl

import io.zenwave360.language.antlr.ZdlLexer
import io.zenwave360.language.zdl.internal.ZdlListenerImpl
import io.zenwave360.language.zdl.internal.ZdlModelPostProcessor
import io.zenwave360.language.zdl.internal.ZdlModelValidator
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.BaseErrorListener
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.RecognitionException
import org.antlr.v4.kotlinruntime.Recognizer
import org.antlr.v4.kotlinruntime.tree.ParseTreeWalker

data class ZdlParseResult(
    val tree: io.zenwave360.language.antlr.ZdlParser.ZdlContext,
    val tokens: CommonTokenStream,
    val syntaxProblems: List<Map<String, Any?>>
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
        val syntaxProblems = mutableListOf<Map<String, Any?>>()
        val errorListener = object : BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String,
                e: RecognitionException?
            ) {
                // One syntax diagnostic per document avoids misleading errors caused by ANTLR recovery.
                if (syntaxProblems.isEmpty()) {
                    syntaxProblems += mapOf(
                        "path" to "syntax",
                        "location" to intArrayOf(
                            0, 0, line, charPositionInLine, line, charPositionInLine + 1
                        ),
                        "value" to offendingSymbol?.toString(),
                        "message" to msg
                    )
                }
            }
        }
        lexer.removeErrorListeners()
        lexer.addErrorListener(errorListener)
        val tokens = CommonTokenStream(lexer)
        val parser = io.zenwave360.language.antlr.ZdlParser(tokens)
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        val zdlRoot = parser.zdl()
        return ZdlParseResult(
            tree = zdlRoot,
            tokens = tokens,
            syntaxProblems = syntaxProblems
        )
    }

    fun parseModel(model: String): ZdlModel {
        val parseResult = parse(model)
        val listener = ZdlListenerImpl()
        try {
            ParseTreeWalker.DEFAULT.walk(listener, parseResult.tree)
        } catch (e: Exception) {
            if (parseResult.syntaxProblems.isEmpty()) {
                listener.model.getProblems().add(
                    mutableMapOf(
                        "path" to "parser",
                        "location" to intArrayOf(0, 0, 1, 0, 1, 1),
                        "value" to null,
                        "message" to ("Parser failed while building ZDL model: ${e.message ?: e::class.simpleName}")
                    )
                )
            }
        }

        if (parseResult.syntaxProblems.isNotEmpty()) {
            listener.model.getProblems().clear()
            listener.model.getProblems().add(parseResult.syntaxProblems.first().toMutableMap())
            return listener.model
        }

        var zdlModel = listener.model
        zdlModel = ZdlModelPostProcessor.postProcess(zdlModel)
        try {
            zdlModel = ZdlModelValidator()
                .withStandardFieldTypes(standardFieldTypes)
                .withExtraFieldTypes(extraFieldTypes)
                .validate(zdlModel)
        } catch (e: Exception) {
            zdlModel.getProblems().add(
                mutableMapOf(
                    "path" to "parser",
                    "location" to intArrayOf(0, 0, 1, 0, 1, 1),
                    "value" to null,
                    "message" to ("ZDL validation failed: ${e.message ?: e::class.simpleName}")
                )
            )
        }
        return zdlModel
    }
}

