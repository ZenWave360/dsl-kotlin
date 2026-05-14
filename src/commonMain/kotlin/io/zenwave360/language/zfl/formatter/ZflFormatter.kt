package io.zenwave360.language.zfl.formatter

import io.zenwave360.language.formatter.internal.AntlrTokenFormatter
import io.zenwave360.language.zfl.ZflParser

class ZflFormatter {

    fun format(input: String): String {
        val parseResult = ZflParser().parse(input)
        return AntlrTokenFormatter().format(parseResult.tokens)
    }
}
