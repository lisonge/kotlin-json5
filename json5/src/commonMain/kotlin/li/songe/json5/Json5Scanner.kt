package li.songe.json5

internal sealed interface Json5ScannedToken {
    val range: Json5SourceRange
    val kind: Json5TokenKind
}

internal sealed interface Json5ScannedValidToken : Json5ScannedToken

internal data class Json5ScannedSimpleToken(
    override val range: Json5SourceRange,
    override val kind: Json5TokenKind,
) : Json5ScannedValidToken

internal data class Json5ScannedStringToken(
    override val range: Json5SourceRange,
    val value: String,
    val quote: Char,
) : Json5ScannedValidToken {
    override val kind: Json5TokenKind = Json5TokenKind.String
}

internal data class Json5ScannedIdentifierToken(
    override val range: Json5SourceRange,
    val value: String,
) : Json5ScannedValidToken {
    override val kind: Json5TokenKind = Json5TokenKind.Identifier
}

internal data class Json5ScannedNumberToken(
    override val range: Json5SourceRange,
    val value: Json5Number,
) : Json5ScannedValidToken {
    override val kind: Json5TokenKind = Json5TokenKind.Number
}

internal data class Json5ScannedDiagnosticToken(
    override val range: Json5SourceRange,
    override val kind: Json5TokenKind,
    val diagnostic: Json5Diagnostic,
) : Json5ScannedToken

internal fun tokenizeJson5(source: String): List<Json5Token> = buildList {
    val scanner = Json5Scanner(source)
    while (true) {
        val scanned = scanner.nextToken() ?: break
        add(Json5Token(scanned.range, scanned.kind))
    }
}

internal class Json5Scanner(
    private val source: String,
) {
    private var index = 0

    fun nextToken(): Json5ScannedToken? {
        if (index >= source.length) return null
        val start = index
        return when (val char = source[index]) {
            '{' -> fixed(start, Json5TokenKind.LeftBrace)
            '}' -> fixed(start, Json5TokenKind.RightBrace)
            '[' -> fixed(start, Json5TokenKind.LeftBracket)
            ']' -> fixed(start, Json5TokenKind.RightBracket)
            ',' -> fixed(start, Json5TokenKind.Comma)
            ':' -> fixed(start, Json5TokenKind.Colon)
            '\'', '"' -> scanString(start, char)
            '/' -> scanSlash(start)
            '\n', '\r', '\u2028', '\u2029' -> scanLineBreak(start)
            in whiteSpaceChars -> scanWhitespace(start)
            in '0'..'9', '+', '-', '.' -> scanNumber(start)
            else -> when {
                char == '\\' || isIdStartChar(char) -> scanIdentifier(start)
                else -> invalidCharacter(start)
            }
        }
    }

    private fun fixed(start: Int, kind: Json5TokenKind): Json5ScannedToken {
        index++
        return simpleToken(start, kind)
    }

    private fun scanLineBreak(start: Int): Json5ScannedToken {
        if (source[index] == '\r' && source.getOrNull(index + 1) == '\n') index += 2 else index++
        return simpleToken(start, Json5TokenKind.LineBreak)
    }

    private fun scanWhitespace(start: Int): Json5ScannedToken {
        while (index < source.length) {
            val char = source[index]
            if (char !in whiteSpaceChars || char in newLineChars) break
            index++
        }
        return simpleToken(start, Json5TokenKind.Whitespace)
    }

    private fun scanSlash(start: Int): Json5ScannedToken {
        return when (source.getOrNull(index + 1)) {
            '/' -> {
                index += 2
                while (index < source.length && source[index] !in newLineChars) index++
                simpleToken(start, Json5TokenKind.LineComment)
            }

            '*' -> {
                index += 2
                while (index + 1 < source.length) {
                    if (source[index] == '*' && source[index + 1] == '/') {
                        index += 2
                        return simpleToken(start, Json5TokenKind.BlockComment)
                    }
                    index++
                }
                index = source.length
                val diagnostic = diagnostic(
                    Json5DiagnosticCode.UnclosedBlockComment,
                    source.length,
                    source.length,
                    "Unclosed block comment",
                )
                diagnosticToken(start, Json5TokenKind.BlockComment, diagnostic)
            }

            else -> invalidCharacter(start)
        }
    }

    private fun scanString(start: Int, quote: Char): Json5ScannedToken {
        index++
        val contentStart = index
        while (index < source.length) {
            when (source[index]) {
                quote -> {
                    val value = source.substring(contentStart, index)
                    index++
                    return stringToken(start, value, quote)
                }

                '\\', '\n', '\r' -> break
                else -> index++
            }
        }

        val value = StringBuilder()
        value.append(source, contentStart, index)
        var firstDiagnostic: Json5Diagnostic? = null

        while (index < source.length) {
            when (val char = source[index]) {
                quote -> {
                    index++
                    return firstDiagnostic?.let { diagnostic ->
                        diagnosticToken(start, Json5TokenKind.String, diagnostic)
                    } ?: stringToken(start, value.toString(), quote)
                }

                '\n', '\r' -> {
                    firstDiagnostic = firstDiagnostic ?: diagnostic(
                        Json5DiagnosticCode.UnclosedString,
                        index,
                        index + 1,
                        "Unescaped line break in string",
                    )
                    return diagnosticToken(start, Json5TokenKind.String, firstDiagnostic)
                }

                '\\' -> {
                    index++
                    if (index >= source.length) {
                        firstDiagnostic = firstDiagnostic ?: diagnostic(
                            Json5DiagnosticCode.UnclosedString,
                            index,
                            index,
                            "Unclosed string",
                        )
                        break
                    }
                    when (val escaped = source[index]) {
                        'x' -> {
                            index++
                            val decoded = readHexEscape(2)
                            firstDiagnostic = firstDiagnostic ?: decoded.second
                            decoded.first?.let(value::append)
                        }

                        'u' -> {
                            index++
                            val decoded = readHexEscape(4)
                            firstDiagnostic = firstDiagnostic ?: decoded.second
                            decoded.first?.let(value::append)
                        }

                        '\'', '"', '\\' -> {
                            value.append(escaped)
                            index++
                        }

                        'b' -> {
                            value.append('\b')
                            index++
                        }

                        'f' -> {
                            value.append('\u000C')
                            index++
                        }

                        'n' -> {
                            value.append('\n')
                            index++
                        }

                        'r' -> {
                            value.append('\r')
                            index++
                        }

                        't' -> {
                            value.append('\t')
                            index++
                        }

                        'v' -> {
                            value.append('\u000B')
                            index++
                        }

                        '0' -> {
                            value.append('\u0000')
                            index++
                            if (source.getOrNull(index) in '0'..'9') {
                                firstDiagnostic = firstDiagnostic ?: diagnostic(
                                    Json5DiagnosticCode.InvalidEscape,
                                    index,
                                    index + 1,
                                    "A zero escape must not be followed by a digit",
                                )
                            }
                        }

                        '\r' -> {
                            index++
                            if (source.getOrNull(index) == '\n') index++
                        }

                        '\n', '\u2028', '\u2029' -> index++
                        in '1'..'9' -> {
                            firstDiagnostic = firstDiagnostic ?: diagnostic(
                                Json5DiagnosticCode.InvalidEscape,
                                index,
                                index + 1,
                                "Invalid numeric escape",
                            )
                            index++
                        }

                        else -> {
                            value.append(escaped)
                            index++
                        }
                    }
                }

                else -> {
                    value.append(char)
                    index++
                }
            }
        }

        firstDiagnostic = firstDiagnostic ?: diagnostic(
            Json5DiagnosticCode.UnclosedString,
            source.length,
            source.length,
            "Unclosed string",
        )
        return diagnosticToken(start, Json5TokenKind.String, firstDiagnostic)
    }

    private fun readHexEscape(length: Int): Pair<Char?, Json5Diagnostic?> {
        val hexStart = index
        var count = 0
        while (count < length && isHexDigit(source.getOrNull(index))) {
            index++
            count++
        }
        if (count != length) {
            val errorEnd = if (index < source.length) index + 1 else index
            return null to diagnostic(
                Json5DiagnosticCode.InvalidEscape,
                index,
                errorEnd,
                "Invalid hexadecimal escape",
            )
        }
        return source.substring(hexStart, index).toInt(16).toChar() to null
    }

    private fun scanIdentifier(start: Int): Json5ScannedToken {
        val value = StringBuilder()
        var firstDiagnostic: Json5Diagnostic? = null
        var first = true

        if (source[index] != '\\') {
            index++
            while (index < source.length && isIdContinueChar(source[index])) index++
            if (source.getOrNull(index) != '\\') {
                return identifierToken(start, source.substring(start, index))
            }
            value.append(source, start, index)
            first = false
        }

        while (index < source.length) {
            val char = source[index]
            val decoded: Char
            if (char == '\\') {
                val result = readIdentifierEscape()
                firstDiagnostic = firstDiagnostic ?: result.second
                decoded = result.first ?: break
            } else {
                val valid = if (first) isIdStartChar(char) else isIdContinueChar(char)
                if (!valid) break
                decoded = char
                index++
            }

            val validDecoded = if (first) isIdStartChar(decoded) else isIdContinueChar(decoded)
            if (!validDecoded) {
                firstDiagnostic = firstDiagnostic ?: diagnostic(
                    Json5DiagnosticCode.InvalidIdentifier,
                    start,
                    index,
                    "Invalid identifier character",
                )
            }
            value.append(decoded)
            first = false
        }

        if (first) {
            if (index == start) index++
            firstDiagnostic = firstDiagnostic ?: diagnostic(
                Json5DiagnosticCode.InvalidIdentifier,
                start,
                index,
                "Invalid identifier",
            )
        }
        return firstDiagnostic?.let { diagnostic ->
            diagnosticToken(start, Json5TokenKind.Identifier, diagnostic)
        } ?: identifierToken(start, value.toString())
    }

    private fun readIdentifierEscape(): Pair<Char?, Json5Diagnostic?> {
        val start = index
        index++
        if (source.getOrNull(index) != 'u') {
            return null to diagnostic(
                Json5DiagnosticCode.InvalidIdentifier,
                start,
                index,
                "Identifier escapes must use Unicode syntax",
            )
        }
        index++
        val decoded = readHexEscape(4)
        return decoded.first to (decoded.second?.copy(code = Json5DiagnosticCode.InvalidIdentifier))
    }

    private fun scanNumber(start: Int): Json5ScannedToken {
        var firstDiagnostic: Json5Diagnostic? = null
        if (source[index] == '+' || source[index] == '-') index++

        when {
            matchesAt(index, "Infinity") -> {
                index += "Infinity".length
                val raw = source.substring(start, index)
                return numberToken(start, Json5Number(raw.removePrefix("+")))
            }

            matchesAt(index, "NaN") -> {
                index += "NaN".length
                val raw = source.substring(start, index)
                return numberToken(start, Json5Number(raw.removePrefix("+")))
            }
        }

        if (
            source.getOrNull(index) == '0' &&
            (source.getOrNull(index + 1) == 'x' || source.getOrNull(index + 1) == 'X')
        ) {
            index += 2
            val digitsStart = index
            while (isHexDigit(source.getOrNull(index))) index++
            if (digitsStart == index) {
                firstDiagnostic = diagnostic(
                    Json5DiagnosticCode.InvalidNumber,
                    index,
                    if (index < source.length) index + 1 else index,
                    "Hexadecimal number requires at least one digit",
                )
            }
            return if (firstDiagnostic == null) {
                val decimal = hexToDecimalString(source.substring(digitsStart, index))
                numberToken(start, Json5Number(if (source[start] == '-') "-$decimal" else decimal))
            } else {
                diagnosticToken(start, Json5TokenKind.Number, firstDiagnostic)
            }
        }

        val integerStart = index
        while (isDigit(source.getOrNull(index))) index++
        val integerLength = index - integerStart
        if (integerLength > 1 && source[integerStart] == '0') {
            firstDiagnostic = diagnostic(
                Json5DiagnosticCode.InvalidNumber,
                integerStart + 1,
                integerStart + 2,
                "Decimal numbers must not contain a leading zero",
            )
        }

        var fractionDigits = 0
        if (source.getOrNull(index) == '.') {
            index++
            val fractionStart = index
            while (isDigit(source.getOrNull(index))) index++
            fractionDigits = index - fractionStart
        }

        if (integerLength == 0 && fractionDigits == 0) {
            firstDiagnostic = firstDiagnostic ?: diagnostic(
                Json5DiagnosticCode.InvalidNumber,
                start,
                index,
                "Invalid number",
            )
        }

        if (isPowerStartChar(source.getOrNull(index))) {
            index++
            if (source.getOrNull(index) == '+' || source.getOrNull(index) == '-') index++
            val exponentStart = index
            while (isDigit(source.getOrNull(index))) index++
            if (exponentStart == index) {
                firstDiagnostic = firstDiagnostic ?: diagnostic(
                    Json5DiagnosticCode.InvalidNumber,
                    index,
                    index,
                    "Exponent requires at least one digit",
                )
            }
        }

        return if (firstDiagnostic == null) {
            numberToken(start, Json5Number(normalizeDecimal(start, index)))
        } else {
            diagnosticToken(start, Json5TokenKind.Number, firstDiagnostic)
        }
    }

    private fun normalizeDecimal(start: Int, end: Int): String {
        var raw = source.substring(start, end)
        if (raw.startsWith('+')) raw = raw.substring(1)
        val exponentIndex = raw.indexOfFirst { it == 'e' || it == 'E' }
        val exponent = if (exponentIndex >= 0) raw.substring(exponentIndex) else ""
        var mantissa = if (exponentIndex >= 0) raw.substring(0, exponentIndex) else raw
        if (mantissa.endsWith('.')) mantissa = mantissa.substring(0, mantissa.lastIndex)
        if (mantissa.startsWith('.')) mantissa = "0$mantissa"
        if (mantissa.startsWith("-.")) mantissa = "-0" + mantissa.substring(1)
        return if ((mantissa == "0" || mantissa == "-0") && exponent.isNotEmpty()) mantissa else mantissa + exponent
    }

    private fun invalidCharacter(start: Int): Json5ScannedToken {
        index++
        return diagnosticToken(
            start,
            Json5TokenKind.Invalid,
            diagnostic(
                Json5DiagnosticCode.UnexpectedCharacter,
                start,
                index,
                "Unexpected character",
            ),
        )
    }

    private fun matchesAt(start: Int, value: String): Boolean {
        if (start + value.length > source.length) return false
        for (offset in value.indices) {
            if (source[start + offset] != value[offset]) return false
        }
        return true
    }

    private fun simpleToken(start: Int, kind: Json5TokenKind): Json5ScannedToken =
        Json5ScannedSimpleToken(tokenRange(start), kind)

    private fun stringToken(start: Int, value: String, quote: Char): Json5ScannedToken =
        Json5ScannedStringToken(tokenRange(start), value, quote)

    private fun identifierToken(start: Int, value: String): Json5ScannedToken =
        Json5ScannedIdentifierToken(tokenRange(start), value)

    private fun numberToken(start: Int, value: Json5Number): Json5ScannedToken =
        Json5ScannedNumberToken(tokenRange(start), value)

    private fun diagnosticToken(
        start: Int,
        kind: Json5TokenKind,
        diagnostic: Json5Diagnostic,
    ): Json5ScannedToken = Json5ScannedDiagnosticToken(tokenRange(start), kind, diagnostic)

    private fun tokenRange(start: Int): Json5SourceRange = Json5SourceRange(start, index)

    private fun diagnostic(
        code: Json5DiagnosticCode,
        start: Int,
        end: Int,
        message: String,
    ): Json5Diagnostic = Json5Diagnostic(code, Json5SourceRange(start, end), message)
}
