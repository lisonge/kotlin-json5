package li.songe.json5

public sealed class Json5Token {
    public data object Comment : Json5Token()
    public data object Whitespace : Json5Token()

    public sealed class FixedChar : Json5Token()
    public sealed class Bracket : FixedChar()
    public data object LeftBracket : Bracket()
    public data object RightBracket : Bracket()
    public sealed class Brace : FixedChar()
    public data object LeftBrace : Brace()
    public data object RightBrace : Brace()
    public data object Comma : FixedChar()
    public data object Colon : FixedChar()

    public sealed class Literal : Json5Token()
    public data object NullLiteral : Literal()
    public data object BooleanLiteral : Literal()
    public data object NumberLiteral : Literal()
    public data object StringLiteral : Literal()

    public data object Property : Json5Token()
}
