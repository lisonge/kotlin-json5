package li.songe.json5

public enum class Json5LineSeparator {
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
    public val indent: String = "  ",
    public val lineSeparator: Json5LineSeparator = Json5LineSeparator.Preserve,
    public val trailingComma: Json5TrailingComma = Json5TrailingComma.Preserve,
    public val finalNewline: Json5FinalNewline = Json5FinalNewline.Preserve,
) {
    init {
        requireValidJson5Indent(indent)
    }
}
