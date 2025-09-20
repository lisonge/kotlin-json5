package li.songe.json5


@Suppress("unused")
sealed class Json5QuoteStrategy {
    /**
     * - true -> single quote
     * - false -> double quote
     */
    internal abstract fun quote(value: String): Boolean

    data object Single : Json5QuoteStrategy() {
        override fun quote(value: String) = true
    }

    data object Double : Json5QuoteStrategy() {
        override fun quote(value: String) = false
    }

    data object PreferSingle : Json5QuoteStrategy() {
        override fun quote(value: String): Boolean {
            return value.contains('"') || !value.contains('\'')
        }
    }

    data object PreferDouble : Json5QuoteStrategy() {
        override fun quote(value: String): Boolean {
            return value.contains('"') && !value.contains('\'')
        }
    }
}
