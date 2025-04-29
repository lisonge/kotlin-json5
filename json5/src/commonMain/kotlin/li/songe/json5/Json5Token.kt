package li.songe.json5

sealed class Json5Token() {
    data object Comment : Json5Token()
    data object Whitespace : Json5Token()

    sealed class FixedChar : Json5Token()
    data object LeftBracket : FixedChar()
    data object RightBracket : FixedChar()
    data object LeftBrace : FixedChar()
    data object RightBrace : FixedChar()
    data object Comma : FixedChar()
    data object Colon : FixedChar()

    sealed class Literal : Json5Token()
    data object NullLiteral : Literal()
    data object BooleanLiteral : Literal()
    data object NumberLiteral : Literal()
    data object StringLiteral : Literal()

    data object Property : Json5Token()
}
