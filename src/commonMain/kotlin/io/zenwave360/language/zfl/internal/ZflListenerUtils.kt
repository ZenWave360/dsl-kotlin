package io.zenwave360.language.zfl.internal

import io.zenwave360.language.antlr.ZflParser
import io.zenwave360.language.utils.Inflector
import io.zenwave360.language.utils.buildMap
import org.antlr.v4.kotlinruntime.ParserRuleContext

internal object ZflListenerUtils {

    private val inflector = Inflector

    fun getText(ctx: ParserRuleContext?): String? = ctx?.text
    fun getText(ctx: ParserRuleContext?, defaultValue: Any?): Any? = ctx?.text ?: defaultValue

    fun getValueText(ctx: ZflParser.ValueContext?): Any? {
        if (ctx == null) return null
        ctx.simple()?.let { return getValueText(it) }
        ctx.object_()?.let { return getObject(it) }
        return ctx.text
    }

    fun getValueText(ctx: ZflParser.StringContext?): Any? {
        if (ctx == null) return null
        ctx.keyword()?.let { return it.text }
        ctx.SINGLE_QUOTED_STRING()?.let { return unquote(it.text, "'") }
        ctx.DOUBLE_QUOTED_STRING()?.let { return unquote(it.text, "\"") }
        return ctx.text
    }

    fun getValueText(ctx: ZflParser.SimpleContext?): Any? {
        if (ctx == null) return null
        ctx.keyword()?.let { return it.text }
        ctx.SINGLE_QUOTED_STRING()?.let { return unquote(it.text, "'") }
        ctx.DOUBLE_QUOTED_STRING()?.let { return unquote(it.text, "\"") }
        ctx.INT()?.let { return ctx.INT()!!.text.toLong() }
        ctx.NUMBER()?.let { return ctx.NUMBER()!!.text /* keep as string to avoid java.math in common */ }
        if (ctx.TRUE() != null) return true
        if (ctx.FALSE() != null) return false
        return ctx.text
    }

    fun getOptionValue(ctx: ZflParser.Option_valueContext?): Any? {
        if (ctx == null) return true
        return getComplexValue(ctx.complex_value())
    }

    fun getComplexValue(complexValue: ZflParser.Complex_valueContext?): Any? {
        if (complexValue == null) return true
        complexValue.value()?.let { return getValue(it) }
        val array = getArrayPlain(complexValue.array_plain())
        val obj = getObjectFromPairs(complexValue.pairs())
        return first(array, obj, true)
    }

    fun getValue(value: ZflParser.ValueContext?): Any? {
        if (value == null) return true
        val obj = getObject(value.object_())
        val array = getArray(value.array())
        val simple = getValueText(value.simple())
        return first(obj, array, simple, true)
    }

    fun unquote(text: String, quote: String): String {
        val escape = "\\\\"
        return text
            .replace(Regex("^" + Regex.escape(quote)), "")
            .replace(Regex(escape + Regex.escape(quote)), quote)
            .replace(Regex(Regex.escape(quote) + "$"), "")
    }

    fun getObject(ctx: ZflParser.ObjectContext?): Any? {
        if (ctx == null) return null
        val map = buildMap()
        ctx.pair().forEach { pair ->
            map.put(pair.string().let { getValueText(it).toString() }, getValue(pair.value()))
        }
        return map
    }

    fun getObjectFromPairs(ctx: ZflParser.PairsContext?): Any? {
        if (ctx == null) return null
        val map = buildMap()
        ctx.pair().forEach { pair ->
            map.put(pair.string().let { getValueText(it).toString() }, getValue(pair.value()))
        }
        return map
    }

    fun getArray(ctx: ZflParser.ArrayContext?): Any? {
        if (ctx == null) return null
        val list = mutableListOf<Any?>()
        ctx.value().forEach { v -> list.add(getValue(v)) }
        return list
    }

    fun getArrayPlain(ctx: ZflParser.Array_plainContext?): Any? {
        if (ctx == null) return null
        val list = mutableListOf<Any?>()
        ctx.simple().forEach { v -> list.add(getValueText(v)) }
        return list
    }

    fun getArray(ctx: ParserRuleContext?, split: String): List<String>? {
        if (ctx == null) return null
        return ctx.text.split(split).map { it.trim() }
    }

    fun camelCase(name: String): String? = inflector.upperCamelCase(name)
    fun lowerCamelCase(name: String): String? = inflector.lowerCamelCase(name)
    fun kebabCase(name: String): String? = inflector.kebabCase(name)

    fun <T> first(vararg args: T?): T? = args.firstOrNull { it != null }

    fun javadoc(javadoc: Any?): String? {
        if (javadoc == null) return null
        val text = getText(javadoc as ParserRuleContext)
        return text
            ?.replace(Regex("^/\\*\\*"), "")
            ?.replace(Regex("\\*/\\s*$"), "")
            ?.replace(Regex("^\\s*\\* "), "")
            ?.trim()
    }

    fun getLocations(ctx: ParserRuleContext?): IntArray? {
        if (ctx == null) return null
        var stopCharOffset = 0
        if (ctx.start == ctx.stop) {
            stopCharOffset = ctx.text.length
        }
        return intArrayOf(
            ctx.start!!.startIndex,
            ctx.stop!!.stopIndex + 1,
            ctx.start!!.line,
            ctx.start!!.charPositionInLine,
            ctx.stop!!.line,
            ctx.stop!!.charPositionInLine + stopCharOffset
        )
    }
}

