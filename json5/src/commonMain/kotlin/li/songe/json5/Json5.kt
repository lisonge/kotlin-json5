package li.songe.json5

import kotlinx.serialization.json.JsonElement

public object Json5 {
    public fun scanSyntax(
        input: CharSequence,
        ignoreWhitespace: Boolean = true,
    ): Json5SyntaxTokens {
        return scanJson5Syntax(input, ignoreWhitespace)
    }

    public fun scanSyntax(
        input: CharSequence,
        ignoreWhitespace: Boolean = true,
        consumer: Json5SyntaxConsumer,
    ) {
        scanJson5Syntax(input, ignoreWhitespace, consumer)
    }

    public fun parseToDocument(input: CharSequence): Json5Document {
        return parseJson5Document(input.toString())
    }

    public fun parseToJsonElement(input: CharSequence): JsonElement {
        return parseJson5Element(input.toString())
    }

    public fun encodeToString(
        element: JsonElement,
        config: Json5EncoderConfig = encoderConfig,
    ): String {
        return innerEncodeToString(element, config)
    }

    public fun format(
        input: CharSequence,
        config: Json5FormatConfig = formatConfig,
    ): String {
        val document = parseToDocument(input).requireValid()
        return formatJson5Document(document, config)
    }

    public fun formatToEdits(
        input: CharSequence,
        config: Json5FormatConfig = formatConfig,
    ): List<Json5TextEdit> {
        val source = input.toString()
        return singleReplacementEdit(source, format(source, config))
    }

    public val encoderConfig: Json5EncoderConfig = Json5EncoderConfig()

    public val formatConfig: Json5FormatConfig = Json5FormatConfig()
}
