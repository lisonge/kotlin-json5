package li.songe.json5

data class Json5EncoderConfig(
    val indent: String = "",
    val singleQuote: Boolean = true,
    val unquotedKey: Boolean = true,
    val trailingComma: Boolean = false,
)
