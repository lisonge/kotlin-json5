package li.songe.json5

private enum class Json5LooseScopeKind {
    Object,
    Array,
}

private enum class Json5LooseExpectation {
    PropertyOrEnd,
    Colon,
    ValueOrEnd,
    Value,
    CommaOrEnd,
}

private data class Json5LooseScope(
    val kind: Json5LooseScopeKind,
    var expectation: Json5LooseExpectation,
)

internal class Json5LooseDecoder(private val input: CharSequence) {
    private val scopes = mutableListOf<Json5LooseScope>()

    private fun expectsProperty(): Boolean {
        val scope = scopes.lastOrNull() ?: return false
        return scope.kind == Json5LooseScopeKind.Object &&
            scope.expectation == Json5LooseExpectation.PropertyOrEnd
    }

    private fun acceptValueOrProperty() {
        val scope = scopes.lastOrNull() ?: return
        scope.expectation = when (scope.kind) {
            Json5LooseScopeKind.Object -> when (scope.expectation) {
                Json5LooseExpectation.PropertyOrEnd -> Json5LooseExpectation.Colon
                Json5LooseExpectation.Value, Json5LooseExpectation.ValueOrEnd -> Json5LooseExpectation.CommaOrEnd
                else -> scope.expectation
            }

            Json5LooseScopeKind.Array -> when (scope.expectation) {
                Json5LooseExpectation.Value, Json5LooseExpectation.ValueOrEnd -> Json5LooseExpectation.CommaOrEnd
                else -> scope.expectation
            }
        }
    }

    private fun openScope(kind: Json5LooseScopeKind) {
        acceptValueOrProperty()
        scopes.add(
            Json5LooseScope(
                kind = kind,
                expectation = when (kind) {
                    Json5LooseScopeKind.Object -> Json5LooseExpectation.PropertyOrEnd
                    Json5LooseScopeKind.Array -> Json5LooseExpectation.ValueOrEnd
                },
            ),
        )
    }

    private fun closeScope(kind: Json5LooseScopeKind) {
        while (scopes.isNotEmpty()) {
            if (scopes.pop().kind == kind) return
        }
    }

    private fun acceptComma() {
        val scope = scopes.lastOrNull() ?: return
        scope.expectation = when (scope.kind) {
            Json5LooseScopeKind.Object -> Json5LooseExpectation.PropertyOrEnd
            Json5LooseScopeKind.Array -> Json5LooseExpectation.ValueOrEnd
        }
    }

    private fun acceptColon() {
        val scope = scopes.lastOrNull() ?: return
        if (scope.kind == Json5LooseScopeKind.Object) {
            scope.expectation = Json5LooseExpectation.Value
        }
    }

    private fun recoverAtLineBreak() {
        val scope = scopes.lastOrNull() ?: return
        if (scope.expectation != Json5LooseExpectation.CommaOrEnd) return
        scope.expectation = when (scope.kind) {
            Json5LooseScopeKind.Object -> Json5LooseExpectation.PropertyOrEnd
            Json5LooseScopeKind.Array -> Json5LooseExpectation.ValueOrEnd
        }
    }

    private fun containsLineBreak(lexeme: Json5LooseLexeme): Boolean {
        for (index in lexeme.start until lexeme.end) {
            if (input[index] in newLineChars) return true
        }
        return false
    }

    private fun classifyIdentifier(lexeme: Json5LooseLexeme): Json5Token {
        if (expectsProperty()) return Json5Token.Property
        return when {
            lexeme.matches("null") -> Json5Token.NullLiteral
            lexeme.matches("true") || lexeme.matches("false") -> Json5Token.BooleanLiteral
            lexeme.matches("NaN") || lexeme.matches("Infinity") -> Json5Token.NumberLiteral
            else -> Json5Token.Property
        }
    }

    private fun Json5LooseLexeme.matches(value: String): Boolean {
        if (end - start != value.length) return false
        for (index in value.indices) {
            if (input[start + index] != value[index]) return false
        }
        return true
    }

    private fun classify(lexeme: Json5LooseLexeme): Json5Token? = when (lexeme.kind) {
        Json5LooseLexemeKind.Unknown -> null
        Json5LooseLexemeKind.Whitespace -> {
            if (containsLineBreak(lexeme)) {
                recoverAtLineBreak()
            }
            Json5Token.Whitespace
        }

        Json5LooseLexemeKind.Comment -> {
            if (input.getOrNull(lexeme.start + 1) == '/' && containsLineBreak(lexeme)) {
                recoverAtLineBreak()
            }
            Json5Token.Comment
        }

        Json5LooseLexemeKind.LeftBrace -> {
            openScope(Json5LooseScopeKind.Object)
            Json5Token.LeftBrace
        }

        Json5LooseLexemeKind.RightBrace -> {
            closeScope(Json5LooseScopeKind.Object)
            Json5Token.RightBrace
        }

        Json5LooseLexemeKind.LeftBracket -> {
            openScope(Json5LooseScopeKind.Array)
            Json5Token.LeftBracket
        }

        Json5LooseLexemeKind.RightBracket -> {
            closeScope(Json5LooseScopeKind.Array)
            Json5Token.RightBracket
        }

        Json5LooseLexemeKind.Comma -> {
            acceptComma()
            Json5Token.Comma
        }

        Json5LooseLexemeKind.Colon -> {
            acceptColon()
            Json5Token.Colon
        }

        Json5LooseLexemeKind.Identifier -> classifyIdentifier(lexeme).also {
            acceptValueOrProperty()
        }

        Json5LooseLexemeKind.Number -> Json5Token.NumberLiteral.also {
            acceptValueOrProperty()
        }

        Json5LooseLexemeKind.String -> Json5Token.StringLiteral.also {
            acceptValueOrProperty()
        }
    }

    fun read(): List<Json5LooseRange> {
        return Json5LooseLexer(input).read().map { lexeme ->
            Json5LooseRange(
                start = lexeme.start,
                end = lexeme.end,
                token = classify(lexeme),
            )
        }
    }
}
