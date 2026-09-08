package li.songe.json5

/**
 * Selects the quote delimiter for a decoded string value. The `Prefer` strategies
 * switch delimiters when that avoids an escape; when both or neither delimiter
 * occurs, they use their named delimiter.
 */
public fun interface Json5QuoteStrategy {
    /** Returns `true` for a single quote or `false` for a double quote. */
    public fun quote(value: String): Boolean

    public data object Single : Json5QuoteStrategy {
        override fun quote(value: String): Boolean = true
    }

    public data object Double : Json5QuoteStrategy {
        override fun quote(value: String): Boolean = false
    }

    public data object PreferSingle : Json5QuoteStrategy {
        override fun quote(value: String): Boolean {
            return value.contains('"') || !value.contains('\'')
        }
    }

    public data object PreferDouble : Json5QuoteStrategy {
        override fun quote(value: String): Boolean {
            return value.contains('"') && !value.contains('\'')
        }
    }
}
