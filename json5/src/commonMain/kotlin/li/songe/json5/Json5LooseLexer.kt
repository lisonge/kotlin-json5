package li.songe.json5

internal enum class Json5LooseLexemeKind {
    Unknown,
    Whitespace,
    Comment,
    LeftBrace,
    RightBrace,
    LeftBracket,
    RightBracket,
    Comma,
    Colon,
    Identifier,
    Number,
    String,
}

internal data class Json5LooseLexeme(
    val start: Int,
    val end: Int,
    val kind: Json5LooseLexemeKind,
    val isComplete: Boolean = true,
)

internal class Json5LooseLexer(override val input: CharSequence) : BaseParser {
    override var i: Int = 0

    private val lexemes = mutableListOf<Json5LooseLexeme>()

    private fun addLexeme(
        start: Int,
        kind: Json5LooseLexemeKind,
        isComplete: Boolean = true,
    ) {
        val previous = lexemes.lastOrNull()
        if (kind == Json5LooseLexemeKind.Unknown && previous?.kind == kind && previous.end == start) {
            lexemes[lexemes.lastIndex] = previous.copy(
                end = i,
                isComplete = previous.isComplete && isComplete,
            )
        } else {
            lexemes.add(Json5LooseLexeme(start, i, kind, isComplete))
        }
    }

    fun read(): List<Json5LooseLexeme> {
        while (!end) {
            val start = i
            when (val c = char!!) {
                '{' -> {
                    i++
                    addLexeme(start, Json5LooseLexemeKind.LeftBrace)
                }

                '}' -> {
                    i++
                    addLexeme(start, Json5LooseLexemeKind.RightBrace)
                }

                '[' -> {
                    i++
                    addLexeme(start, Json5LooseLexemeKind.LeftBracket)
                }

                ']' -> {
                    i++
                    addLexeme(start, Json5LooseLexemeKind.RightBracket)
                }

                ',' -> {
                    i++
                    addLexeme(start, Json5LooseLexemeKind.Comma)
                }

                ':' -> {
                    i++
                    addLexeme(start, Json5LooseLexemeKind.Colon)
                }

                '\'', '"' -> {
                    val isComplete = readLooseString()
                    addLexeme(start, Json5LooseLexemeKind.String, isComplete)
                }

                '/' -> {
                    if (input.getOrNull(i + 1) == '/' || input.getOrNull(i + 1) == '*') {
                        val isComplete = readLooseComment()
                        addLexeme(start, Json5LooseLexemeKind.Comment, isComplete)
                    } else {
                        i++
                        addLexeme(start, Json5LooseLexemeKind.Unknown)
                    }
                }

                in whiteSpaceChars -> {
                    readWhitespace()
                    addLexeme(start, Json5LooseLexemeKind.Whitespace)
                }

                in '0'..'9', '+', '-', '.' -> {
                    val isComplete = readLooseNumber()
                    addLexeme(start, Json5LooseLexemeKind.Number, isComplete)
                }

                else -> {
                    if (c == '\\' || isIdStartChar(c)) {
                        val isComplete = readLooseIdentifier()
                        addLexeme(start, Json5LooseLexemeKind.Identifier, isComplete)
                    } else {
                        i++
                        addLexeme(start, Json5LooseLexemeKind.Unknown)
                    }
                }
            }
        }
        return lexemes
    }
}
