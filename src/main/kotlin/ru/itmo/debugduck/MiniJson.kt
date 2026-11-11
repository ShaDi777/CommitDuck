package ru.itmo.debugduck

/**
 * Minimal JSON writer and reader for the LLM API, so the plugin doesn't depend on a JSON library being on the IDE
 * classpath.
 *
 * Values map to Kotlin as follows: object -> `Map<String, Any?>`, array -> `List<Any?>`, string -> `String`,
 * number -> `Long` or `Double`, `true`/`false` -> `Boolean`, `null` -> `null`.
 */
object MiniJson {
    fun stringify(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    /** @throws IllegalArgumentException if [text] is not valid JSON */
    fun parse(text: String): Any? = Parser(text).parseDocument()

    private fun write(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(value)
            is Int, is Long, is Short, is Byte -> out.append(value)
            is Number -> {
                val number = value.toDouble()
                require(number.isFinite()) { "JSON can't represent $number" }
                out.append(number)
            }
            is Map<*, *> -> {
                out.append('{')
                value.entries.forEachIndexed { index, (key, item) ->
                    if (index > 0) out.append(',')
                    writeString(out, key.toString())
                    out.append(':')
                    write(out, item)
                }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                value.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(out, item)
                }
                out.append(']')
            }
            else -> throw IllegalArgumentException("Unsupported JSON value: ${value::class.java.name}")
        }
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ' || c == ' ' || c == ' ') {
                    out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                }
                else {
                    out.append(c)
                }
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun parseDocument(): Any? {
            val value = parseValue()
            skipWhitespace()
            if (pos != text.length) fail("Unexpected trailing characters")
            return value
        }

        private fun parseValue(): Any? {
            skipWhitespace()
            if (pos >= text.length) fail("Unexpected end of JSON")
            return when (val c = text[pos]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isAsciiDigit()) parseNumber() else fail("Unexpected character '$c'")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                if (peek() != '"') fail("Expected a string key")
                val key = parseString()
                skipWhitespace()
                expect(':')
                result[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return result
                    }
                    else -> fail("Expected ',' or '}'")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return result
            }
            while (true) {
                result += parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return result
                    }
                    else -> fail("Expected ',' or ']'")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (pos >= text.length) fail("Unterminated string")
                val c = text[pos++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> {
                        if (pos >= text.length) fail("Unterminated escape")
                        when (val escaped = text[pos++]) {
                            '"', '\\', '/' -> out.append(escaped)
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                if (pos + 4 > text.length) fail("Invalid unicode escape")
                                val code = text.substring(pos, pos + 4).toIntOrNull(16) ?: fail("Invalid unicode escape")
                                out.append(code.toChar())
                                pos += 4
                            }
                            else -> fail("Invalid escape '\\$escaped'")
                        }
                    }
                    c < ' ' -> fail("Unescaped control character in string")
                    else -> out.append(c)
                }
            }
        }

        private fun parseNumber(): Number {
            val start = pos
            if (peek() == '-') pos++
            while (pos < text.length && text[pos].isAsciiDigit()) pos++
            var isInteger = true
            if (peek() == '.') {
                isInteger = false
                pos++
                while (pos < text.length && text[pos].isAsciiDigit()) pos++
            }
            if (peek() == 'e' || peek() == 'E') {
                isInteger = false
                pos++
                if (peek() == '+' || peek() == '-') pos++
                while (pos < text.length && text[pos].isAsciiDigit()) pos++
            }
            val literal = text.substring(start, pos)
            return (if (isInteger) literal.toLongOrNull() ?: literal.toDoubleOrNull() else literal.toDoubleOrNull())
                ?: fail("Invalid number '$literal'")
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, pos)) fail("Unexpected token")
            pos += word.length
            return value
        }

        private fun expect(c: Char) {
            if (peek() != c) fail("Expected '$c'")
            pos++
        }

        private fun peek(): Char? = text.getOrNull(pos)

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].let { it == ' ' || it == '\n' || it == '\r' || it == '\t' }) pos++
        }

        private fun Char.isAsciiDigit() = this in '0'..'9'

        private fun fail(message: String): Nothing = throw IllegalArgumentException("$message at position $pos")
    }
}

/** Convenience accessors for values returned by [MiniJson.parse]. */
fun Any?.jsonField(name: String): Any? = (this as? Map<*, *>)?.get(name)

fun Any?.jsonIndex(index: Int): Any? = (this as? List<*>)?.getOrNull(index)
