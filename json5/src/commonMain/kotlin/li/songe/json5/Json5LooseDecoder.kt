package li.songe.json5

internal class Json5LooseDecoder(override val input: CharSequence) : BaseParser {
    override var i = 0

    val scopes = mutableListOf<Json5Token>()
    val ranges = mutableListOf<Json5Range>()
    val lastVisibleToken: Json5Token?
        get() {
            for (i in ranges.lastIndex downTo 0) {
                val token = ranges[i].token
                if (token == Json5Token.Whitespace || token == Json5Token.Comment) {
                    continue
                }
                return token
            }
            return null
        }

    fun read(): List<Json5Range> {
        while (!end) {
            val inMap = scopes.lastOrNull() == Json5Token.LeftBrace && lastVisibleToken.let {
                it == Json5Token.Comma || it == Json5Token.LeftBrace
            }
            val token = charToJson5Token(input[i], inMap)
            when (token) {
                null -> {
                    i++
                }

                Json5Token.Whitespace -> {
                    val start = i
                    readWhitespace()
                    ranges.add(Json5Range(start, end = i, token))
                }

                Json5Token.Comment -> {
                    val start = i
                    readLooseComment()
                    if (i > start) {
                        ranges.add(Json5Range(start, end = i, token))
                    } else {
                        i++
                    }
                }

                is Json5Token.FixedChar -> {
                    val start = i
                    i++
                    ranges.add(Json5Range(start, end = i, token))
                    when (token) {
                        Json5Token.LeftBrace, Json5Token.LeftBracket -> scopes.add(token)

                        Json5Token.RightBrace -> {
                            if (scopes.lastOrNull() == Json5Token.LeftBrace) {
                                scopes.pop()
                            }
                        }

                        Json5Token.RightBracket -> {
                            if (scopes.lastOrNull() == Json5Token.LeftBracket) {
                                scopes.pop()
                            }
                        }

                        else -> {}
                    }
                }

                Json5Token.NullLiteral -> {
                    if (peekLiteral("null")) {
                        val start = i
                        i += 4
                        ranges.add(Json5Range(start, end = i, token))
                    } else {
                        i++
                    }
                }

                Json5Token.BooleanLiteral -> {
                    if (peekLiteral("true")) {
                        val start = i
                        i += 4
                        ranges.add(Json5Range(start, end = i, token))
                    } else if (peekLiteral("false")) {
                        val start = i
                        i += 5
                        ranges.add(Json5Range(start, end = i, token))
                    } else {
                        i++
                    }
                }

                Json5Token.NumberLiteral -> {
                    val start = i
                    val actualToken = readLooseNumber()
                    ranges.add(Json5Range(start, end = i, actualToken))
                }

                Json5Token.StringLiteral -> {
                    val start = i
                    readLooseString()
                    ranges.add(Json5Range(start, end = i, token))
                }

                Json5Token.Property -> {
                    val start = i
                    readLooseProperty()
                    if (i > start) {
                        ranges.add(Json5Range(start, end = i, token))
                    } else {
                        i++
                    }
                }
            }
        }
        return ranges
    }
}
