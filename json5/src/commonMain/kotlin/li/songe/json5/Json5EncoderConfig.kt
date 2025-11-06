package li.songe.json5

public data class Json5EncoderConfig(
    val indent: String = "",
    val quoteStrategy: Json5QuoteStrategy = Json5QuoteStrategy.Single,
    val unquotedKey: Boolean = true,
    val trailingComma: Boolean = false,
)
