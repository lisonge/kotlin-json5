package li.songe.json5


public fun interface Json5QuoteStrategy {
    /**
     * - true -> single quote
     * - false -> double quote
     */
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
