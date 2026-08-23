package li.songe.json5

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private enum class Json5ObjectState {
    PropertyOrEnd,
    Colon,
    Value,
    CommaOrEnd,
}

private enum class Json5ArrayState {
    ValueOrEnd,
    CommaOrEnd,
}

private data class Json5ParsedProperty<V>(
    val name: Json5PropertyName,
    val value: V,
    val valueRange: Json5SourceRange,
)

private sealed interface Json5ParseFrame<V> {
    val start: Int
}

private class Json5ObjectFrame<V>(
    override val start: Int,
) : Json5ParseFrame<V> {
    val properties = mutableListOf<Json5ParsedProperty<V>>()
    var state = Json5ObjectState.PropertyOrEnd
    var propertyName: Json5PropertyName? = null
}

private class Json5ArrayFrame<V>(
    override val start: Int,
) : Json5ParseFrame<V> {
    val elements = mutableListOf<V>()
    var state = Json5ArrayState.ValueOrEnd
}

private interface Json5ParseSink<V, R> {
    fun token(range: Json5SourceRange, kind: Json5TokenKind)

    fun string(range: Json5SourceRange, value: String, quote: Char): V

    fun number(range: Json5SourceRange, value: Json5Number): V

    fun boolean(range: Json5SourceRange, value: Boolean): V

    fun nullValue(range: Json5SourceRange): V

    fun array(range: Json5SourceRange, elements: List<V>): V

    fun objectValue(range: Json5SourceRange, properties: List<Json5ParsedProperty<V>>): V

    fun result(root: V?, diagnostics: List<Json5Diagnostic>): R
}

private class Json5ElementSink : Json5ParseSink<JsonElement, JsonElement> {
    override fun token(range: Json5SourceRange, kind: Json5TokenKind) = Unit

    override fun string(range: Json5SourceRange, value: String, quote: Char): JsonElement =
        JsonPrimitive(value)

    override fun number(range: Json5SourceRange, value: Json5Number): JsonElement =
        JsonPrimitive(value)

    override fun boolean(range: Json5SourceRange, value: Boolean): JsonElement =
        JsonPrimitive(value)

    override fun nullValue(range: Json5SourceRange): JsonElement = JsonNull

    override fun array(range: Json5SourceRange, elements: List<JsonElement>): JsonElement =
        JsonArray(elements)

    override fun objectValue(
        range: Json5SourceRange,
        properties: List<Json5ParsedProperty<JsonElement>>,
    ): JsonElement {
        val values = linkedMapOf<String, JsonElement>()
        for (property in properties) values[property.name.value] = property.value
        return JsonObject(values)
    }

    override fun result(root: JsonElement?, diagnostics: List<Json5Diagnostic>): JsonElement =
        root ?: throw IllegalStateException("strict parsing completed without a root value")
}

private class Json5DocumentSink(
    private val source: String,
) : Json5ParseSink<Json5Value, Json5Document> {
    private val tokens = mutableListOf<Json5Token>()
    private var nextNodeId = 0

    override fun token(range: Json5SourceRange, kind: Json5TokenKind) {
        tokens.add(Json5Token(range, kind))
    }

    override fun string(range: Json5SourceRange, value: String, quote: Char): Json5Value =
        Json5StringLiteral(range, value, quote, nextId())

    override fun number(range: Json5SourceRange, value: Json5Number): Json5Value =
        Json5NumberLiteral(range, value, nextId())

    override fun boolean(range: Json5SourceRange, value: Boolean): Json5Value =
        Json5BooleanLiteral(range, value, nextId())

    override fun nullValue(range: Json5SourceRange): Json5Value =
        Json5NullLiteral(range, nextId())

    override fun array(range: Json5SourceRange, elements: List<Json5Value>): Json5Value =
        Json5Array(range, elements, nextId())

    override fun objectValue(
        range: Json5SourceRange,
        properties: List<Json5ParsedProperty<Json5Value>>,
    ): Json5Value {
        val nodes = properties.map { property ->
            Json5Property(
                range = Json5SourceRange(property.name.range.start, property.valueRange.end),
                name = property.name,
                value = property.value,
                nodeId = nextId(),
            )
        }
        return Json5Object(range, nodes, nextId())
    }

    override fun result(root: Json5Value?, diagnostics: List<Json5Diagnostic>): Json5Document =
        createJson5Document(source, root, tokens, diagnostics)

    private fun nextId(): Int = nextNodeId++
}

private class Json5Parser<V, R>(
    private val source: String,
    private val strict: Boolean,
    private val sink: Json5ParseSink<V, R>,
) {
    private val scanner = Json5Scanner(source)
    private val diagnostics = mutableListOf<Json5Diagnostic>()
    private val frames = mutableListOf<Json5ParseFrame<V>>()
    private var root: V? = null

    fun parse(): R {
        while (true) {
            val scanned = scanner.nextToken() ?: break
            sink.token(scanned.range, scanned.kind)
            when (scanned) {
                is Json5ScannedDiagnosticToken -> report(scanned.diagnostic)
                is Json5ScannedValidToken -> {
                    if (scanned.kind.isTrivia) continue
                    var consumed = false
                    while (!consumed) consumed = consume(scanned)
                }
            }
        }

        finishOpenContainers()
        if (root == null) {
            report(
                diagnostic(
                    Json5DiagnosticCode.ExpectedValue,
                    source.length,
                    source.length,
                    "Expected a JSON5 value",
                ),
            )
        }
        return sink.result(root, diagnostics)
    }

    private fun consume(scanned: Json5ScannedValidToken): Boolean {
        val frame = frames.lastOrNull()
        if (frame == null) {
            if (root != null) {
                report(unexpected(scanned.range, "Unexpected token after root value"))
                return true
            }
            return consumeValue(scanned)
        }
        return when (frame) {
            is Json5ObjectFrame -> consumeObject(scanned, frame)
            is Json5ArrayFrame -> consumeArray(scanned, frame)
        }
    }

    private fun consumeObject(scanned: Json5ScannedValidToken, frame: Json5ObjectFrame<V>): Boolean {
        val range = scanned.range
        return when (frame.state) {
            Json5ObjectState.PropertyOrEnd -> when (scanned.kind) {
                Json5TokenKind.RightBrace -> {
                    closeObject(frame, range.end)
                    true
                }

                Json5TokenKind.RightBracket -> closeMismatched(range, "Expected '}'")
                Json5TokenKind.Comma -> {
                    report(expectedProperty(range))
                    true
                }

                else -> {
                    val name = propertyName(scanned)
                    if (name == null) {
                        report(expectedProperty(range))
                    } else {
                        frame.propertyName = name
                        frame.state = Json5ObjectState.Colon
                    }
                    true
                }
            }

            Json5ObjectState.Colon -> if (scanned.kind == Json5TokenKind.Colon) {
                frame.state = Json5ObjectState.Value
                true
            } else {
                report(
                    diagnostic(
                        Json5DiagnosticCode.ExpectedColon,
                        range.start,
                        range.start,
                        "Expected ':' after property name",
                    ),
                )
                frame.state = Json5ObjectState.Value
                false
            }

            Json5ObjectState.Value -> when (scanned.kind) {
                Json5TokenKind.RightBrace -> {
                    report(expectedValue(range.start))
                    frame.propertyName = null
                    closeObject(frame, range.end)
                    true
                }

                Json5TokenKind.RightBracket -> {
                    report(expectedValue(range.start))
                    frame.propertyName = null
                    closeMismatched(range, "Expected '}'")
                }

                Json5TokenKind.Comma -> {
                    report(expectedValue(range.start))
                    frame.propertyName = null
                    frame.state = Json5ObjectState.PropertyOrEnd
                    true
                }

                else -> consumeValue(scanned)
            }

            Json5ObjectState.CommaOrEnd -> when (scanned.kind) {
                Json5TokenKind.Comma -> {
                    frame.state = Json5ObjectState.PropertyOrEnd
                    true
                }

                Json5TokenKind.RightBrace -> {
                    closeObject(frame, range.end)
                    true
                }

                Json5TokenKind.RightBracket -> closeMismatched(range, "Expected ',' or '}'")
                else -> {
                    report(expectedCommaOrEnd(range, "Expected ',' or '}'"))
                    frame.state = Json5ObjectState.PropertyOrEnd
                    false
                }
            }
        }
    }

    private fun consumeArray(scanned: Json5ScannedValidToken, frame: Json5ArrayFrame<V>): Boolean {
        val range = scanned.range
        return when (frame.state) {
            Json5ArrayState.ValueOrEnd -> when (scanned.kind) {
                Json5TokenKind.RightBracket -> {
                    closeArray(frame, range.end)
                    true
                }

                Json5TokenKind.RightBrace -> closeMismatched(range, "Expected ']'")
                Json5TokenKind.Comma -> {
                    report(expectedValue(range.start))
                    true
                }

                else -> consumeValue(scanned)
            }

            Json5ArrayState.CommaOrEnd -> when (scanned.kind) {
                Json5TokenKind.Comma -> {
                    frame.state = Json5ArrayState.ValueOrEnd
                    true
                }

                Json5TokenKind.RightBracket -> {
                    closeArray(frame, range.end)
                    true
                }

                Json5TokenKind.RightBrace -> closeMismatched(range, "Expected ',' or ']'")
                else -> {
                    report(expectedCommaOrEnd(range, "Expected ',' or ']'"))
                    frame.state = Json5ArrayState.ValueOrEnd
                    false
                }
            }
        }
    }

    private fun consumeValue(scanned: Json5ScannedValidToken): Boolean {
        val range = scanned.range
        when (scanned) {
            is Json5ScannedStringToken -> acceptValue(
                sink.string(range, scanned.value, scanned.quote),
                range,
            )

            is Json5ScannedNumberToken -> acceptValue(sink.number(range, scanned.value), range)

            is Json5ScannedIdentifierToken -> {
                when (val value = scanned.value) {
                    "null" -> acceptValue(sink.nullValue(range), range)
                    "true" -> acceptValue(sink.boolean(range, true), range)
                    "false" -> acceptValue(sink.boolean(range, false), range)
                    "NaN", "Infinity" -> acceptValue(
                        sink.number(range, Json5Number(value)),
                        range,
                    )

                    else -> return reportExpectedValue(range)
                }
            }

            is Json5ScannedSimpleToken -> when (scanned.kind) {
                Json5TokenKind.LeftBrace -> frames.add(Json5ObjectFrame(range.start))
                Json5TokenKind.LeftBracket -> frames.add(Json5ArrayFrame(range.start))
                Json5TokenKind.RightBrace, Json5TokenKind.RightBracket -> {
                    report(
                        diagnostic(
                            Json5DiagnosticCode.MismatchedClosingToken,
                            range.start,
                            range.end,
                            "Unexpected closing token",
                        ),
                    )
                }

                else -> return reportExpectedValue(range)
            }
        }
        return true
    }

    private fun propertyName(scanned: Json5ScannedValidToken): Json5PropertyName? {
        val (value, style) = when (scanned) {
            is Json5ScannedIdentifierToken -> scanned.value to Json5PropertyNameStyle.Identifier
            is Json5ScannedStringToken -> scanned.value to if (scanned.quote == '\'') {
                Json5PropertyNameStyle.SingleQuoted
            } else {
                Json5PropertyNameStyle.DoubleQuoted
            }
            else -> return null
        }
        return Json5PropertyName(value, style, scanned.range)
    }

    private fun acceptValue(value: V, range: Json5SourceRange) {
        when (val frame = frames.lastOrNull()) {
            null -> root = value
            is Json5ArrayFrame -> {
                frame.elements.add(value)
                frame.state = Json5ArrayState.CommaOrEnd
            }

            is Json5ObjectFrame -> {
                val name = frame.propertyName
                if (name == null) {
                    report(expectedProperty(range))
                    return
                }
                frame.properties.add(Json5ParsedProperty(name, value, range))
                frame.propertyName = null
                frame.state = Json5ObjectState.CommaOrEnd
            }
        }
    }

    private fun closeObject(frame: Json5ObjectFrame<V>, end: Int) {
        frames.pop()
        val range = Json5SourceRange(frame.start, end)
        acceptValue(sink.objectValue(range, frame.properties), range)
    }

    private fun closeArray(frame: Json5ArrayFrame<V>, end: Int) {
        frames.pop()
        val range = Json5SourceRange(frame.start, end)
        acceptValue(sink.array(range, frame.elements), range)
    }

    private fun closeMismatched(range: Json5SourceRange, expected: String): Boolean {
        report(
            diagnostic(
                Json5DiagnosticCode.MismatchedClosingToken,
                range.start,
                range.end,
                expected,
            ),
        )
        when (val frame = frames.last()) {
            is Json5ObjectFrame -> closeObject(frame, range.start)
            is Json5ArrayFrame -> closeArray(frame, range.start)
        }
        return false
    }

    private fun finishOpenContainers() {
        while (frames.isNotEmpty()) {
            val frame = frames.last()
            val expected = if (frame is Json5ObjectFrame) "Unclosed object" else "Unclosed array"
            report(
                diagnostic(
                    Json5DiagnosticCode.UnclosedContainer,
                    source.length,
                    source.length,
                    expected,
                ),
            )
            when (frame) {
                is Json5ObjectFrame -> closeObject(frame, source.length)
                is Json5ArrayFrame -> closeArray(frame, source.length)
            }
        }
    }

    private fun reportExpectedValue(range: Json5SourceRange): Boolean {
        report(expectedValue(range.start, range.end))
        return true
    }

    private fun report(diagnostic: Json5Diagnostic) {
        if (strict) throw Json5ParseException(diagnostic)
        diagnostics.add(diagnostic)
    }

    private fun expectedValue(start: Int, end: Int = start): Json5Diagnostic = diagnostic(
        Json5DiagnosticCode.ExpectedValue,
        start,
        end,
        "Expected a JSON5 value",
    )

    private fun expectedProperty(range: Json5SourceRange): Json5Diagnostic = diagnostic(
        Json5DiagnosticCode.ExpectedProperty,
        range.start,
        range.end,
        "Expected an object property",
    )

    private fun expectedCommaOrEnd(range: Json5SourceRange, message: String): Json5Diagnostic = diagnostic(
        Json5DiagnosticCode.ExpectedCommaOrEnd,
        range.start,
        range.start,
        message,
    )

    private fun unexpected(range: Json5SourceRange, message: String): Json5Diagnostic = diagnostic(
        Json5DiagnosticCode.UnexpectedToken,
        range.start,
        range.end,
        message,
    )

    private fun diagnostic(
        code: Json5DiagnosticCode,
        start: Int,
        end: Int,
        message: String,
    ): Json5Diagnostic = Json5Diagnostic(code, Json5SourceRange(start, end), message)
}

internal fun parseJson5Document(source: String): Json5Document =
    Json5Parser(source, strict = false, sink = Json5DocumentSink(source)).parse()

internal fun parseJson5Element(source: String): JsonElement =
    Json5Parser(source, strict = true, sink = Json5ElementSink()).parse()
