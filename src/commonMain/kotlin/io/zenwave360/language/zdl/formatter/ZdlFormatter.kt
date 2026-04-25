package io.zenwave360.language.zdl.formatter

import io.zenwave360.language.formatter.internal.AntlrTokenFormatter
import io.zenwave360.language.zdl.ZdlParser

class ZdlFormatter {

    fun format(input: String): String {
        val parseResult = ZdlParser().parse(input)
        return AntlrTokenFormatter().format(parseResult.tokens)
    }
}
