package io.zenwave360.language.zfl

import io.zenwave360.language.antlr.ZflLexer
import io.zenwave360.language.zfl.internal.ZflListenerImpl
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.RecognitionException
import org.antlr.v4.kotlinruntime.Recognizer
import org.antlr.v4.kotlinruntime.tree.ParseTreeWalker

data class ZflParseResult(
    val tree: io.zenwave360.language.antlr.ZflParser.ZflContext,
    val tokens: CommonTokenStream,
    val syntaxProblems: List<Map<String, Any?>>
)

class ZflParser {

    fun parse(input: String): ZflParseResult {
        val zfl = CharStreams.fromString(input)
        val lexer = ZflLexer(zfl)
        val tokens = CommonTokenStream(lexer)
        val parser = io.zenwave360.language.antlr.ZflParser(tokens)
        val syntaxProblems = mutableListOf<Map<String, Any?>>()
        parser.addErrorListener(object : org.antlr.v4.kotlinruntime.BaseErrorListener() {
            override fun syntaxError(
                recognizer: Recognizer<*, *>,
                offendingSymbol: Any?,
                line: Int,
                charPositionInLine: Int,
                msg: String,
                e: RecognitionException?
            ) {
                syntaxProblems += mapOf(
                    "path" to "syntax",
                    "location" to intArrayOf(0, 0, line, charPositionInLine, line, charPositionInLine),
                    "value" to (offendingSymbol?.toString()),
                    "message" to msg
                )
            }
        })
        val zflRoot = parser.zfl()
        return ZflParseResult(
            tree = zflRoot,
            tokens = tokens,
            syntaxProblems = syntaxProblems
        )
    }

    fun parseModel(model: String): ZflModel {
        val parseResult = parse(model)
        val listener = ZflListenerImpl()
        ParseTreeWalker.DEFAULT.walk(listener, parseResult.tree)
        parseResult.syntaxProblems.forEach { listener.model.getProblems().add(it.toMutableMap()) }
        return listener.model
    }
}

