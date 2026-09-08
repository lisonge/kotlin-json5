package li.songe.json5

public data class Json5EncoderConfig(
    /** Indentation per nesting level; only spaces and tabs are allowed. An empty value produces compact output. */
    val indent: String = "",
    val quoteStrategy: Json5QuoteStrategy = Json5QuoteStrategy.Single,
    val unquotedKey: Boolean = true,
    val trailingComma: Boolean = false,
) {
    init {
        requireValidJson5Indent(indent)
    }
}
