package io.zenwave360.language.zfl

import io.zenwave360.language.antlr.ZflLexer
import io.zenwave360.language.zfl.internal.ZflListenerImpl
import org.antlr.v4.kotlinruntime.CharStreams
import org.antlr.v4.kotlinruntime.CommonTokenStream
import org.antlr.v4.kotlinruntime.tree.ParseTreeWalker

class ZflParser {

    fun parseModel(model: String): ZflModel {
        val zfl = CharStreams.fromString(model)
        val lexer = ZflLexer(zfl)
        val tokens = CommonTokenStream(lexer)
        val parser = io.zenwave360.language.antlr.ZflParser(tokens)
        val listener = ZflListenerImpl()
        val zflRoot = parser.zfl()
        ParseTreeWalker.DEFAULT.walk(listener, zflRoot)

        return listener.model
    }
}

