package li.songe.json5

import kotlinx.serialization.json.JsonElement

private val unicodeLetterCategories = hashSetOf(
    CharCategory.UPPERCASE_LETTER,
    CharCategory.LOWERCASE_LETTER,
    CharCategory.TITLECASE_LETTER,
    CharCategory.MODIFIER_LETTER,
    CharCategory.OTHER_LETTER,
    CharCategory.LETTER_NUMBER,
)

private val unicodeIdCategories = hashSetOf(
    CharCategory.NON_SPACING_MARK,
    CharCategory.COMBINING_SPACING_MARK,
    CharCategory.DECIMAL_DIGIT_NUMBER,
    CharCategory.CONNECTOR_PUNCTUATION,
)

internal fun isIdStartChar(c: Char): Boolean {
    return c == '_' || c == '$' || c.category in unicodeLetterCategories
}

internal fun isIdContinueChar(c: Char): Boolean {
    return isIdStartChar(c) || c.category in unicodeIdCategories || c == '\u200C' || c == '\u200D'
}

internal fun isDigit(c: Char?): Boolean {
    c ?: return false
    return c in '0'..'9'
}

internal fun isHexDigit(c: Char?): Boolean {
    c ?: return false
    return (c in '0'..'9') || (c in 'A'..'F') || (c in 'a'..'f')
}

internal fun isPowerStartChar(c: Char?): Boolean {
    c ?: return false
    return c == 'e' || c == 'E'
}

internal fun isHexStartChar(c: Char?): Boolean {
    c ?: return false
    return c == 'x' || c == 'X'
}

// https://github.com/json5/json5/blob/b935d4a280eafa8835e6182551b63809e61243b0/lib/parse.js#L135-L144
// https://github.com/json5/json5/blob/b935d4a280eafa8835e6182551b63809e61243b0/lib/unicode.js#L2
internal const val whiteSpaceChars =
    "\u0009\u000A\u000B\u000C\u000D\u0020\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF"

// https://github.com/json5/json5/blob/b935d4a280eafa8835e6182551b63809e61243b0/lib/parse.js#L222-L225
internal val newLineChars = "\u000A\u000D\u2028\u2029".toCharArray()

private val escapeReplacements = hashMapOf(
    '\\' to "\\\\",
    '\b' to "\\b",
    '\u000C' to "\\f",
    '\n' to "\\n",
    '\r' to "\\r",
    '\t' to "\\t",
    '\u000B' to "\\v",
    '\u0000' to "\\0",
    '\u2028' to "\\u2028",
    '\u2029' to "\\u2029",
)

// https://github.com/json5/json5/blob/b935d4a280eafa8835e6182551b63809e61243b0/lib/stringify.js#L104
internal fun stringifyString(value: String, singleQuote: Boolean): String {
    val wrapChar = if (singleQuote) '\'' else '"'
    val sb = StringBuilder()
    sb.append(wrapChar)
    value.forEachIndexed { i, c ->
        when {
            c == wrapChar -> {
                sb.append("\\$wrapChar")
            }

            c == '\u0000' -> {
                if (isDigit(value.getOrNull(i + 1))) {
                    // "\u00002" -> \x002, avoid octal ambiguity
                    sb.append("\\x00")
                } else {
                    sb.append("\\0")
                }
            }

            c in escapeReplacements -> {
                sb.append(escapeReplacements[c])
            }

            c.code in 0..0x1f -> {
                sb.append("\\x" + c.code.toString(16).padStart(2, '0'))
            }

            else -> {
                sb.append(c)
            }
        }
    }
    sb.append(wrapChar)
    return sb.toString()
}

internal fun stringifyKey(value: String, singleQuote: Boolean, unquotedKey: Boolean): String {
    if (value.isEmpty() || !unquotedKey) {
        return stringifyString(value, singleQuote)
    }
    if (!isIdStartChar(value[0])) {
        return stringifyString(value, singleQuote)
    }
    for (c in value) {
        if (!isIdContinueChar(c)) {
            return stringifyString(value, singleQuote)
        }
    }
    return value
}

internal fun <T> MutableList<T>.pop(): T = removeAt(lastIndex)

@Suppress("UNCHECKED_CAST")
internal fun Any.toJsonMap() = this as MutableMap<String, JsonElement>

@Suppress("UNCHECKED_CAST")
internal fun Any.toJsonList() = this as MutableList<JsonElement>

internal fun charToJson5Token(c: Char, inMap: Boolean = false): Json5Token? {
    if (inMap && isIdStartChar(c)) {
        // null, true, false, Infinity, NaN can be property name
        return Json5Token.Property
    }
    return when (c) {
        '{' -> Json5Token.LeftBrace
        '}' -> Json5Token.RightBrace
        '[' -> Json5Token.LeftBracket
        ']' -> Json5Token.RightBracket
        ':' -> Json5Token.Colon
        ',' -> Json5Token.Comma
        'n' -> Json5Token.NullLiteral
        't', 'f' -> Json5Token.BooleanLiteral
        in '0'..'9', '-', '+', '.', 'N', 'I' -> Json5Token.NumberLiteral
        '\'', '"' -> Json5Token.StringLiteral
        '/' -> Json5Token.Comment
        in whiteSpaceChars -> Json5Token.Whitespace
        else -> if (c == '\\' || isIdStartChar(c)) {
            Json5Token.Property
        } else {
            null
        }
    }
}
