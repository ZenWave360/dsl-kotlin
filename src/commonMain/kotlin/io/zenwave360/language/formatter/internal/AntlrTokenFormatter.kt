package io.zenwave360.language.formatter.internal

import org.antlr.v4.kotlinruntime.BufferedTokenStream
import org.antlr.v4.kotlinruntime.Token

internal class AntlrTokenFormatter(
    private val indentSize: Int = 4
) {

    fun format(tokens: BufferedTokenStream): String {
        tokens.fill()

        val writer = FormatterWriter(indentSize)
        var pendingWhitespace = StringBuilder()
        var previousVisibleToken: Token? = null
        var inWhenHeader = false
        var whenHeaderUsesPipeLayout = false

        tokens.tokens.forEach { token ->
            if (token.type == Token.EOF) return@forEach

            val text = token.text ?: return@forEach
            when {
                isWhitespace(token) -> {
                    pendingWhitespace.append(text)
                }

                isCommentToken(token, text) -> {
                    val whitespace = pendingWhitespace.toString()
                    pendingWhitespace = StringBuilder()

                    val newlineCount = countNewlines(whitespace)
                    if (newlineCount > 0) {
                        writer.writeNewlines(newlineCount)
                    } else if (!writer.isAtLineStart) {
                        writer.writeSpace()
                    }

                    writer.writeComment(text)
                }

                else -> {
                    val whitespace = pendingWhitespace.toString()
                    pendingWhitespace = StringBuilder()

                    if (text == "when") {
                        inWhenHeader = true
                        whenHeaderUsesPipeLayout = false
                    }

                    if (inWhenHeader && text == "|") {
                        if (!whenHeaderUsesPipeLayout) {
                            writer.increaseIndent()
                            whenHeaderUsesPipeLayout = true
                        }
                        if (!writer.isAtLineStart) {
                            writer.writeNewlines(1)
                        }
                        writer.writeToken(text)
                        previousVisibleToken = token
                        return@forEach
                    }

                    val newlineCount = countNewlines(whitespace)
                    val closingBraceOnNewLine = text == "}" && newlineCount > 0
                    if (closingBraceOnNewLine) {
                        writer.decreaseIndent()
                    }

                    if (inWhenHeader && whenHeaderUsesPipeLayout && text == "do") {
                        writer.decreaseIndent()
                        writer.writeNewlines(1)
                    } else if (newlineCount > 0) {
                        writer.writeNewlines(newlineCount)
                    } else if (shouldInsertSpace(previousVisibleToken, token, whitespace)) {
                        writer.writeSpace()
                    }

                    writer.writeToken(text)

                    if (text == "{") {
                        writer.increaseIndent()
                        if (inWhenHeader) {
                            inWhenHeader = false
                            whenHeaderUsesPipeLayout = false
                        }
                    } else if (text == "}" && !closingBraceOnNewLine) {
                        // Inline empty blocks like "{}" still close the indentation scope.
                        writer.decreaseIndent()
                    }

                    previousVisibleToken = token
                }
            }
        }

        return writer.finish()
    }

    private fun isWhitespace(token: Token): Boolean =
        token.channel != Token.DEFAULT_CHANNEL && (token.text?.isBlank() == true)

    private fun isCommentToken(token: Token, text: String): Boolean =
        token.channel != Token.DEFAULT_CHANNEL || text.startsWith("/**")

    private fun shouldInsertSpace(previousToken: Token?, currentToken: Token, whitespace: String): Boolean {
        if (previousToken == null) return false
        if (countNewlines(whitespace) > 0) return false

        val previousText = previousToken.text ?: return false
        val currentText = currentToken.text ?: return false

        if (currentText == "{") {
            return whitespace.isNotEmpty()
        }

        if (NO_SPACE_BEFORE.contains(currentText)) return false
        if (NO_SPACE_AFTER.contains(previousText)) return false

        return true
    }

    private fun countNewlines(text: String): Int =
        text.count { it == '\n' }

    private companion object {
        val NO_SPACE_BEFORE = setOf("(", ")", "]", "}", ",", ".", "?", "[]", ":")
        val NO_SPACE_AFTER = setOf("(", "[", "{", ".")
    }
}

internal class FormatterWriter(
    private val indentSize: Int
) {
    private val buffer = StringBuilder()
    private var indentLevel = 0
    var isAtLineStart: Boolean = true
        private set

    fun increaseIndent() {
        indentLevel++
    }

    fun decreaseIndent() {
        if (indentLevel > 0) {
            indentLevel--
        }
    }

    fun writeSpace() {
        if (isAtLineStart) {
            return
        }
        if (buffer.isNotEmpty() && buffer.last() != ' ' && buffer.last() != '\n') {
            buffer.append(' ')
        }
    }

    fun writeToken(text: String) {
        writeIndentIfNeeded()
        buffer.append(text)
        isAtLineStart = false
    }

    fun writeComment(text: String) {
        writeIndentIfNeeded()

        if (text.startsWith("//")) {
            val normalized = text.trimEnd()
            buffer.append(normalized)
            writeNewlines(1)
            return
        }

        val normalized = text
            .replace("\r\n", "\n")
            .lines()
            .joinToString("\n") { it.trimEnd() }
        buffer.append(normalized)
        isAtLineStart = normalized.endsWith('\n')
    }

    fun writeNewlines(count: Int) {
        repeat(count) {
            trimTrailingSpaces()
            if (buffer.isNotEmpty() && buffer.last() == '\r') {
                buffer.deleteRange(buffer.length - 1, buffer.length)
            }
            buffer.append('\n')
        }
        isAtLineStart = true
    }

    fun finish(): String {
        val result = buffer.toString()
            .trimEnd('\n')

        return if (result.isEmpty()) {
            result
        } else {
            "$result\n"
        }
    }

    private fun writeIndentIfNeeded() {
        if (!isAtLineStart) {
            return
        }
        repeat(indentLevel * indentSize) {
            buffer.append(' ')
        }
        isAtLineStart = false
    }

    private fun trimTrailingSpaces() {
        while (buffer.isNotEmpty() && (buffer.last() == ' ' || buffer.last() == '\t')) {
            buffer.deleteRange(buffer.length - 1, buffer.length)
        }
    }
}
