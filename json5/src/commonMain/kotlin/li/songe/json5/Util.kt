package li.songe.json5

internal fun isIdStartChar(c: Char): Boolean {
    if (c == '_' || c == '$' || c in 'A'..'Z' || c in 'a'..'z') return true
    if (c.code < ASCII_LIMIT) return false
    return when (c.category) {
        CharCategory.UPPERCASE_LETTER,
        CharCategory.LOWERCASE_LETTER,
        CharCategory.TITLECASE_LETTER,
        CharCategory.MODIFIER_LETTER,
        CharCategory.OTHER_LETTER,
        CharCategory.LETTER_NUMBER,
        -> true

        else -> false
    }
}

internal fun isIdContinueChar(c: Char): Boolean {
    if (isIdStartChar(c) || c in '0'..'9') return true
    if (c.code < ASCII_LIMIT) return false
    return when (c.category) {
        CharCategory.NON_SPACING_MARK,
        CharCategory.COMBINING_SPACING_MARK,
        CharCategory.DECIMAL_DIGIT_NUMBER,
        CharCategory.CONNECTOR_PUNCTUATION,
        -> true

        else -> c == '\u200C' || c == '\u200D'
    }
}

internal fun isDigit(c: Char?): Boolean {
    c ?: return false
    return c in '0'..'9'
}

internal fun isHexDigit(c: Char): Boolean {
    return (c in '0'..'9') || (c in 'A'..'F') || (c in 'a'..'f')
}

internal fun isPowerStartChar(c: Char?): Boolean {
    c ?: return false
    return c == 'e' || c == 'E'
}

internal fun requireValidJson5Indent(indent: String) {
    for (char in indent) {
        require(char == ' ' || char == '\t') { "indent may only contain spaces or tabs" }
    }
}

// https://github.com/json5/json5/blob/b935d4a280eafa8835e6182551b63809e61243b0/lib/parse.js#L135-L144
// https://github.com/json5/json5/blob/b935d4a280eafa8835e6182551b63809e61243b0/lib/unicode.js#L2
internal const val whiteSpaceChars =
    "\u0009\u000A\u000B\u000C\u000D\u0020\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF"

// https://github.com/json5/json5/blob/b935d4a280eafa8835e6182551b63809e61243b0/lib/parse.js#L222-L225
internal val newLineChars = "\u000A\u000D\u2028\u2029".toCharArray()

internal fun <T> MutableList<T>.pop(): T = removeAt(lastIndex)

private const val ASCII_LIMIT = 0x80
