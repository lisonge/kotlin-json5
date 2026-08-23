package li.songe.json5

public enum class Json5DiagnosticCode {
    UnexpectedCharacter,
    UnexpectedToken,
    ExpectedValue,
    ExpectedProperty,
    ExpectedColon,
    ExpectedCommaOrEnd,
    InvalidNumber,
    InvalidIdentifier,
    InvalidEscape,
    UnclosedString,
    UnclosedBlockComment,
    UnclosedContainer,
    MismatchedClosingToken,
}

public data class Json5Diagnostic(
    public val code: Json5DiagnosticCode,
    public val range: Json5SourceRange,
    public val message: String,
)

public class Json5ParseException(
    public val diagnostic: Json5Diagnostic,
) : IllegalStateException("${diagnostic.message} at index ${diagnostic.range.start}") {
    public val offset: Int
        get() = diagnostic.range.start
}
