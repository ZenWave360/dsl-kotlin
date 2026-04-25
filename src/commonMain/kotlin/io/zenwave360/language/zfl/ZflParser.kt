package io.zenwave360.language.zfl

import io.zenwave360.language.antlr.ZflLexer
import io.zenwave360.language.zfl.internal.ZflListenerImpl
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.tree.ParseTreeWalker

data class ZflParseResult(
    val tree: io.zenwave360.language.antlr.ZflParser.ZflContext,
    val tokens: CommonTokenStream
)

class ZflParser {

    fun parse(input: String): ZflParseResult {
        val zfl = CharStreams.fromString(input)
        val lexer = ZflLexer(zfl)
        val tokens = CommonTokenStream(lexer)
        val parser = io.zenwave360.language.antlr.ZflParser(tokens)
        val zflRoot = parser.zfl()
        return ZflParseResult(
            tree = zflRoot,
            tokens = tokens
        )
    }

    fun parseModel(model: String): ZflModel {
        val parseResult = parse(model)
        val listener = ZflListenerImpl()
        ParseTreeWalker.DEFAULT.walk(listener, parseResult.tree)
        return listener.model
    }
}

