package li.songe.json5

import kotlinx.serialization.json.*


internal class Json5Decoder(override val input: CharSequence) : BaseParser {

    override var i = 0

    fun skipToken(v: Json5Token): Boolean {
        when (v) {
            is Json5Token.Whitespace -> readWhitespace()
            is Json5Token.Comment -> readComment()
            else -> return false
        }
        return true
    }

    fun parseValueToken(v: Json5Token): JsonPrimitive = when (v) {
        Json5Token.NullLiteral -> readNull().let { JsonNull }
        Json5Token.BooleanLiteral -> JsonPrimitive(readBoolean())
        Json5Token.NumberLiteral -> JsonPrimitive(readNumber())
        Json5Token.StringLiteral -> JsonPrimitive(readString())
        else -> stop()
    }

    // Map<String, JsonElement> | List<JsonElement> | String | JsonPrimitive
    val stack = mutableListOf<Any>()

    fun startAny(token: Json5Token) {
        when (token) {
            Json5Token.LeftBrace -> stack.add(mutableMapOf<String, JsonElement>())
            Json5Token.LeftBracket -> stack.add(mutableListOf<JsonElement>())
            else -> {
                val x0 = parseValueToken(token)
                val x1 = stack.lastOrNull()
                when (x1) {
                    is String -> {
                        stack.pop()
                        val x2 = stack.last().toJsonMap()
                        x2[x1] = x0
                    }

                    is MutableList<*> -> {
                        x1.toJsonList().add(x0)
                    }

                    else -> {
                        stack.add(x0)
                    }
                }
            }
        }
    }

    fun buildTokenSeq() = sequence {
        while (!end) {
            val c = input[i]
            val inMap = stack.lastOrNull() is MutableMap<*, *>
            val token = charToJson5Token(c, inMap)
            if (token == null) {
                stop()
            }
            yield(token)
        }
    }

    var lastVisibleToken: Json5Token? = null

    @Suppress("UNCHECKED_CAST")
    fun read(tokenSeq: Sequence<Json5Token> = buildTokenSeq()): JsonElement {
        for (token in tokenSeq) {
            if (skipToken(token)) {
                continue
            }
            when (val x = stack.lastOrNull()) {
                null -> startAny(token)

                is MutableMap<*, *> -> when (token) {
                    Json5Token.StringLiteral -> stack.add(readString())
                    Json5Token.Property -> stack.add(readObjectProperty())
                    Json5Token.RightBrace -> {
                        if (stack.size > 1) {
                            stack.pop()
                            val x0 = x.toJsonMap()
                            val x1 = stack.last()
                            if (x1 is String) {
                                stack.pop()
                                val x2 = stack.last().toJsonMap()
                                x2[x1] = JsonObject(x0)
                            } else if (x1 is MutableList<*>) {
                                x1.toJsonList().add(JsonObject(x0))
                            }
                        }
                    }

                    Json5Token.Comma -> {
                        if (x.isEmpty() || lastVisibleToken == Json5Token.Comma) {
                            stop()
                        }
                    }

                    else -> stop()
                }

                is String -> {
                    if (token == Json5Token.Colon) {
                        if (lastVisibleToken == Json5Token.Colon) {
                            stop()
                        }
                    } else {
                        if (lastVisibleToken != Json5Token.Colon) {
                            stop()
                        }
                        startAny(token)
                    }
                }

                is MutableList<*> -> when (token) {
                    Json5Token.RightBracket -> {
                        if (stack.size > 1) {
                            stack.pop()
                            val x0 = x.toJsonList()
                            val x1 = stack.last()
                            if (x1 is String) {
                                stack.pop()
                                val x2 = stack.last().toJsonMap()
                                x2[x1] = JsonArray(x0)
                            } else if (x1 is MutableList<*>) {
                                x1.toJsonList().add(JsonArray(x0))
                            }
                        }
                    }

                    Json5Token.Comma -> {
                        if (x.isEmpty() || lastVisibleToken == Json5Token.Comma) {
                            stop()
                        }
                    }

                    else -> startAny(token)
                }

                is JsonPrimitive -> stop()
            }
            if (token is Json5Token.FixedChar) {
                i++
            }
            lastVisibleToken = token
        }
        return (when (val c = stack.lastOrNull()) {
            is JsonElement -> c
            is MutableList<*> -> JsonArray(c.toJsonList())
            is MutableMap<*, *> -> JsonObject(c.toJsonMap())
            else -> stop()
        })
    }

    fun readElementAndRange(): Pair<JsonElement, List<Json5Range>> {
        var lastToken: Json5Token? = null
        var lastIndex = 0
        val tokenList = mutableListOf<Json5Range>()
        val element = read(buildTokenSeq().onEach { token ->
            lastToken?.let {
                tokenList.add(
                    Json5Range(
                        start = lastIndex,
                        end = i,
                        token = it,
                    )
                )
            }
            lastIndex = i
            lastToken = token
        })
        tokenList.add(
            Json5Range(
                start = lastIndex,
                end = i,
                token = lastToken!!,
            )
        )
        return element to tokenList
    }
}