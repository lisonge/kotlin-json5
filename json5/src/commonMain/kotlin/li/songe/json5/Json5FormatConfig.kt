package li.songe.json5

public enum class Json5LineSeparator {
    /** Uses the first separator found in the source, or LF when none is present. */
    Preserve,
    Lf,
    CrLf,
}

public enum class Json5TrailingComma {
    Preserve,
    Always,
    Never,
}

public enum class Json5FinalNewline {
    Preserve,
    Always,
    Never,
}

public data class Json5FormatConfig(
    /** Indentation per nesting level; only spaces and tabs are allowed. */
    public val indent: String = "  ",
    public val lineSeparator: Json5LineSeparator = Json5LineSeparator.Preserve,
    public val trailingComma: Json5TrailingComma = Json5TrailingComma.Preserve,
    public val finalNewline: Json5FinalNewline = Json5FinalNewline.Preserve,
    /**
     * Re-encodes string literals and property names with this strategy.
     * `null` preserves their original source representation.
     */
    public val quoteStrategy: Json5QuoteStrategy? = null,
    /**
     * Whether property names eligible for identifier syntax are left unquoted
     * when [quoteStrategy] enables re-encoding.
     */
    public val unquotedKey: Boolean = true,
) {
    init {
        requireValidJson5Indent(indent)
    }
}
