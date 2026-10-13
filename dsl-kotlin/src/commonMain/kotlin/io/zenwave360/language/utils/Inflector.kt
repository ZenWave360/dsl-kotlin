package io.zenwave360.language.utils

object Inflector {

    fun pluralize(word: String?): String? {
        if (word == null) return null
        val w = word.trim()
        if (w.isEmpty()) return w

        val esEndings = listOf("s", "x", "z", "ch", "sh")
        if (esEndings.any { w.endsWith(it) }) {
            return w + "es"
        }

        if (w.endsWith("y") && w.length > 1 && !isVowel(w[w.length - 2])) {
            return w.dropLast(1) + "ies"
        }

        return w + "s"
    }

    fun upperCamelCase(input: String?): String? = camelCase(input, true)
    fun lowerCamelCase(input: String?): String? = camelCase(input, false)

    fun camelCase(input: String?, uppercaseFirst: Boolean): String? {
        if (input == null) return null
        val s = input.trim()
        if (s.isEmpty()) return s
        // split by underscores and non-alphanumeric boundaries
        val parts = s.split(Regex("[_\\s.-]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ""
        val first = parts.first()
        val rest = parts.drop(1).map { it.replaceFirstChar { c -> c.uppercaseChar() } }
        return if (uppercaseFirst) {
            (listOf(first.replaceFirstChar { it.uppercaseChar() }) + rest).joinToString("")
        } else {
            (listOf(first.replaceFirstChar { it.lowercaseChar() }) + rest).joinToString("")
        }
    }

    fun underscore(input: String?): String? {
        if (input == null) return null
        var result = input.trim()
        if (result.isEmpty()) return result
        // handle camel case boundaries
        result = result.replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
            .replace(Regex("([a-z\\d])([A-Z])"), "$1_$2")
            .replace('-', '_')
        return result.lowercase()
    }

    fun kebabCase(input: String?): String? = underscore(input)?.replace('_', '-')

    private fun isVowel(c: Char): Boolean = c.lowercaseChar() in listOf('a', 'e', 'i', 'o', 'u')
}

