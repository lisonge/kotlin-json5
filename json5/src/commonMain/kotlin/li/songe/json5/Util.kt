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

internal fun <T> MutableList<T>.pop(): T = removeAt(lastIndex)

@Suppress("UNCHECKED_CAST")
internal fun Any.toJsonMap() = this as MutableMap<String, JsonElement>

@Suppress("UNCHECKED_CAST")
internal fun Any.toJsonList() = this as MutableList<JsonElement>

// inMap: null, true, false, Infinity, NaN can be property name
internal fun BaseParser.charToJson5Token(inMapLeft: Boolean = false): Json5Token? = when (val c = input[i]) {
    '{' -> Json5Token.LeftBrace
    '}' -> Json5Token.RightBrace
    '[' -> Json5Token.LeftBracket
    ']' -> Json5Token.RightBracket
    ':' -> Json5Token.Colon
    ',' -> Json5Token.Comma
    'n' -> if (inMapLeft) Json5Token.Property else Json5Token.NullLiteral
    't', 'f' -> if (inMapLeft) Json5Token.Property else Json5Token.BooleanLiteral
    'N', 'I' -> if (inMapLeft) Json5Token.Property else Json5Token.NumberLiteral
    in '0'..'9', '-', '+', '.' -> Json5Token.NumberLiteral
    '\'', '"' -> Json5Token.StringLiteral
    '/' -> Json5Token.Comment
    in whiteSpaceChars -> Json5Token.Whitespace
    else -> if (c == '\\' || isIdStartChar(c)) {
        Json5Token.Property
    } else {
        null
    }
}
