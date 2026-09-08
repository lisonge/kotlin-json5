package li.songe.json5

public enum class Json5TokenKind {
    Whitespace,
    LineBreak,
    LineComment,
    BlockComment,
    LeftBrace,
    RightBrace,
    LeftBracket,
    RightBracket,
    Comma,
    Colon,
    Identifier,
    Number,
    String,
    Invalid,
    ;

    /** Whether this kind represents whitespace, a line break, or a comment. */
    public val isTrivia: Boolean
        get() = this == Whitespace || this == LineBreak ||
            this == LineComment || this == BlockComment

    public val isComment: Boolean
        get() = this == LineComment || this == BlockComment
}

public data class Json5Token(
    public val range: Json5SourceRange,
    public val kind: Json5TokenKind,
)
