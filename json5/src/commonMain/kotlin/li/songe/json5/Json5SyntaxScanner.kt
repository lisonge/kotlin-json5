package li.songe.json5

private const val OBJECT_PROPERTY = 0
private const val OBJECT_COLON = 1
private const val OBJECT_VALUE = 2
private const val OBJECT_COMMA = 3
private const val ARRAY_VALUE = 4
private const val ARRAY_COMMA = 5

internal fun scanJson5Syntax(input: CharSequence, ignoreWhitespace: Boolean): Json5SyntaxTokens {
    val builder = Json5SyntaxTokensBuilder(input.length)
    scanJson5Syntax(input, ignoreWhitespace, builder)
    return builder.build()
}

internal fun scanJson5Syntax(
    input: CharSequence,
    ignoreWhitespace: Boolean,
    consumer: Json5SyntaxConsumer,
) {
    Json5SyntaxScanner(input, ignoreWhitespace, consumer).scan()
}

private class Json5SyntaxScanner(
    private val input: CharSequence,
    private val ignoreWhitespace: Boolean,
    private val consumer: Json5SyntaxConsumer,
) {
    private val inputLength = input.length
    private var scopes = IntArray(8)
    private var scopeSize = 0
    private var index = 0

    fun scan() {
        while (index < inputLength) {
            val start = index
            when (val char = input[index]) {
                '{' -> {
                    index++
                    consumer.accept(Json5SyntaxKind.LeftBrace, start, index)
                    acceptValueOrProperty()
                    pushScope(OBJECT_PROPERTY)
                }

                '}' -> {
                    index++
                    consumer.accept(Json5SyntaxKind.RightBrace, start, index)
                    closeScope(objectScope = true)
                }

                '[' -> {
                    index++
                    consumer.accept(Json5SyntaxKind.LeftBracket, start, index)
                    acceptValueOrProperty()
                    pushScope(ARRAY_VALUE)
                }

                ']' -> {
                    index++
                    consumer.accept(Json5SyntaxKind.RightBracket, start, index)
                    closeScope(objectScope = false)
                }

                ',' -> {
                    index++
                    consumer.accept(Json5SyntaxKind.Comma, start, index)
                    acceptComma()
                }

                ':' -> {
                    index++
                    consumer.accept(Json5SyntaxKind.Colon, start, index)
                    acceptColon()
                }

                '\'', '"' -> {
                    scanString(char)
                    val kind = if (expectsProperty()) {
                        Json5SyntaxKind.PropertyName
                    } else {
                        Json5SyntaxKind.StringLiteral
                    }
                    consumer.accept(kind, start, index)
                    acceptValueOrProperty()
                }

                '/' -> scanSlash(start)

                in whiteSpaceChars -> {
                    scanWhitespace()
                    if (!ignoreWhitespace) {
                        consumer.accept(Json5SyntaxKind.Whitespace, start, index)
                    }
                }

                in '0'..'9', '+', '-', '.' -> {
                    scanNumber()
                    consumer.accept(Json5SyntaxKind.NumberLiteral, start, index)
                    acceptValueOrProperty()
                }

                else -> {
                    if (char == '\\' || isIdStartChar(char)) {
                        scanIdentifier()
                        consumer.accept(classifyIdentifier(start, index), start, index)
                        acceptValueOrProperty()
                    } else {
                        scanInvalid()
                        consumer.accept(Json5SyntaxKind.Invalid, start, index)
                    }
                }
            }
        }
    }

    private fun scanWhitespace() {
        var containsLineBreak = false
        while (index < inputLength && input[index] in whiteSpaceChars) {
            if (input[index] in newLineChars) containsLineBreak = true
            index++
        }
        if (containsLineBreak) recoverAtLineBreak()
    }

    private fun scanSlash(start: Int) {
        if (index + 1 >= inputLength) {
            index++
            consumer.accept(Json5SyntaxKind.Invalid, start, index)
            return
        }

        when (input[index + 1]) {
            '/' -> {
                index += 2
                while (index < inputLength && input[index] !in newLineChars) index++
                consumer.accept(Json5SyntaxKind.Comment, start, index)
            }

            '*' -> {
                index += 2
                var containsLineBreak = false
                while (index < inputLength) {
                    if (index + 1 < inputLength && input[index] == '*' && input[index + 1] == '/') {
                        index += 2
                        consumer.accept(Json5SyntaxKind.Comment, start, index)
                        if (containsLineBreak) recoverAtLineBreak()
                        return
                    }
                    if (input[index] in newLineChars) containsLineBreak = true
                    index++
                }
                consumer.accept(Json5SyntaxKind.Comment, start, index)
                if (containsLineBreak) recoverAtLineBreak()
            }

            else -> {
                index++
                consumer.accept(Json5SyntaxKind.Invalid, start, index)
            }
        }
    }

    private fun scanString(quote: Char) {
        index++
        while (index < inputLength) {
            when (input[index]) {
                quote -> {
                    index++
                    return
                }

                '\n', '\r' -> return
                '\\' -> {
                    index++
                    if (index >= inputLength) return
                    if (input[index] == '\r') {
                        index++
                        if (index < inputLength && input[index] == '\n') index++
                    } else {
                        index++
                    }
                }

                else -> index++
            }
        }
    }

    private fun scanNumber() {
        if (input[index] == '+' || input[index] == '-') index++

        if (index < inputLength && (input[index] == 'N' || input[index] == 'I')) {
            scanIdentifier()
            return
        }

        if (
            index + 1 < inputLength &&
            input[index] == '0' &&
            (input[index + 1] == 'x' || input[index + 1] == 'X')
        ) {
            index += 2
            while (index < inputLength && isHexDigit(input[index])) index++
            return
        }

        while (index < inputLength) {
            when (input[index]) {
                in '0'..'9', '+', '-', '.', 'e', 'E' -> index++
                else -> return
            }
        }
    }

    private fun scanIdentifier() {
        var first = true
        while (index < inputLength) {
            val char = input[index]
            when {
                char == '\\' -> {
                    index++
                    if (index < inputLength && input[index] == 'u') {
                        index++
                        var length = 0
                        while (length < 4 && index < inputLength && isHexDigit(input[index])) {
                            index++
                            length++
                        }
                    }
                    first = false
                }

                (first && isIdStartChar(char)) || (!first && isIdContinueChar(char)) -> {
                    index++
                    first = false
                }

                else -> return
            }
        }
    }

    private fun scanInvalid() {
        index++
        while (index < inputLength && !isTokenStart(input[index])) index++
    }

    private fun isTokenStart(char: Char): Boolean {
        return when (char) {
            '{', '}', '[', ']', ',', ':', '\'', '"', '/', '+', '-', '.', '\\' -> true
            in whiteSpaceChars, in '0'..'9' -> true
            else -> isIdStartChar(char)
        }
    }

    private fun classifyIdentifier(start: Int, end: Int): Json5SyntaxKind {
        if (expectsProperty()) return Json5SyntaxKind.PropertyName
        return when {
            matches(start, end, "null") -> Json5SyntaxKind.NullLiteral
            matches(start, end, "true") || matches(start, end, "false") -> Json5SyntaxKind.BooleanLiteral
            matches(start, end, "NaN") || matches(start, end, "Infinity") -> Json5SyntaxKind.NumberLiteral
            else -> Json5SyntaxKind.Identifier
        }
    }

    private fun matches(start: Int, end: Int, value: String): Boolean {
        if (end - start != value.length) return false
        for (offset in value.indices) {
            if (input[start + offset] != value[offset]) return false
        }
        return true
    }

    private fun expectsProperty(): Boolean =
        scopeSize > 0 && scopes[scopeSize - 1] == OBJECT_PROPERTY

    private fun acceptValueOrProperty() {
        if (scopeSize == 0) return
        val last = scopeSize - 1
        scopes[last] = when (scopes[last]) {
            OBJECT_PROPERTY -> OBJECT_COLON
            OBJECT_VALUE -> OBJECT_COMMA
            ARRAY_VALUE -> ARRAY_COMMA
            else -> scopes[last]
        }
    }

    private fun acceptComma() {
        if (scopeSize == 0) return
        val last = scopeSize - 1
        scopes[last] = if (isObjectState(scopes[last])) OBJECT_PROPERTY else ARRAY_VALUE
    }

    private fun acceptColon() {
        if (scopeSize == 0) return
        val last = scopeSize - 1
        if (isObjectState(scopes[last])) scopes[last] = OBJECT_VALUE
    }

    private fun recoverAtLineBreak() {
        if (scopeSize == 0) return
        val last = scopeSize - 1
        scopes[last] = when (scopes[last]) {
            OBJECT_COMMA -> OBJECT_PROPERTY
            ARRAY_COMMA -> ARRAY_VALUE
            else -> scopes[last]
        }
    }

    private fun pushScope(state: Int) {
        if (scopeSize == scopes.size) scopes = scopes.copyOf(scopes.size * 2)
        scopes[scopeSize] = state
        scopeSize++
    }

    private fun closeScope(objectScope: Boolean) {
        while (scopeSize > 0) {
            val state = scopes[scopeSize - 1]
            scopeSize--
            if (isObjectState(state) == objectScope) return
        }
    }

    private fun isObjectState(state: Int): Boolean = state <= OBJECT_COMMA
}

private class Json5SyntaxTokensBuilder(sourceLength: Int) : Json5SyntaxConsumer {
    private val firstChunkSize = sourceLength.coerceIn(MIN_CHUNK_SIZE, MAX_CHUNK_SIZE).let { size ->
        size - size % TOKEN_WIDTH
    }
    private val fullChunks = mutableListOf<IntArray>()
    private var currentChunk = IntArray(0)
    private var currentSize = 0
    private var size = 0

    override fun accept(kind: Json5SyntaxKind, start: Int, end: Int) {
        ensureChunk()
        currentChunk[currentSize] = kind.ordinal
        currentChunk[currentSize + 1] = start
        currentChunk[currentSize + 2] = end
        currentSize += TOKEN_WIDTH
        size += TOKEN_WIDTH
    }

    fun build(): Json5SyntaxTokens {
        val result = IntArray(size)
        var destination = 0
        for (chunk in fullChunks) {
            chunk.copyInto(result, destination)
            destination += chunk.size
        }
        currentChunk.copyInto(result, destination, endIndex = currentSize)
        return Json5SyntaxTokens(result)
    }

    private fun ensureChunk() {
        if (currentSize < currentChunk.size) return
        val nextChunkSize = if (currentChunk.isEmpty()) {
            firstChunkSize
        } else {
            (currentChunk.size * 2).coerceAtMost(MAX_CHUNK_SIZE)
        }
        if (currentChunk.isNotEmpty()) fullChunks.add(currentChunk)
        currentChunk = IntArray(nextChunkSize)
        currentSize = 0
    }

    private companion object {
        const val TOKEN_WIDTH = 3
        const val MIN_CHUNK_SIZE = TOKEN_WIDTH * 8
        const val MAX_CHUNK_SIZE = TOKEN_WIDTH * 1024
    }
}
