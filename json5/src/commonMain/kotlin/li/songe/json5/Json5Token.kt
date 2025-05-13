package li.songe.json5

sealed class Json5Token {
    data object Comment : Json5Token()
    data object Whitespace : Json5Token()

    sealed class FixedChar : Json5Token()
    sealed class Bracket : FixedChar()
    data object LeftBracket : Bracket()
    data object RightBracket : Bracket()
    sealed class Brace : FixedChar()
    data object LeftBrace : Brace()
    data object RightBrace : Brace()
    data object Comma : FixedChar()
    data object Colon : FixedChar()

    sealed class Literal : Json5Token()
    data object NullLiteral : Literal()
    data object BooleanLiteral : Literal()
    data object NumberLiteral : Literal()
    data object StringLiteral : Literal()

    data object Property : Json5Token()
}
