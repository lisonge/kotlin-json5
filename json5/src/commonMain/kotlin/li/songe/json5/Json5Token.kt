package li.songe.json5

sealed class Json5Token {
    data object Comments : Json5Token()
    data object Whitespace : Json5Token()

    data object ArrayBracket : Json5Token()
    data object ObjectBrace : Json5Token()
    data object Comma : Json5Token()

    sealed class Literal : Json5Token()
    data object NullLiteral : Literal()
    data object BooleanLiteral : Literal()
    data object NumberLiteral : Literal()
    data object StringLiteral : Literal()

    data object Property : Json5Token()
    data object Colon : Json5Token()
}
