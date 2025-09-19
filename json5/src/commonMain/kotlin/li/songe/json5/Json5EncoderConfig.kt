package li.songe.json5

enum class Quotes { single, double, preferSingle, preferDouble }

data class Json5EncoderConfig(
    val indent: String = "",
    val quotes: Quotes = Quotes.single,
    val unquotedKey: Boolean = true,
    val trailingComma: Boolean = false,
)
