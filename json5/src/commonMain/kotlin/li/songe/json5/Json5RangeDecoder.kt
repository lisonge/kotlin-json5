package li.songe.json5

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal class Json5RangeDecoder(input: CharSequence) : Json5Decoder(input, false) {
    private val ranges = mutableListOf<Json5Range>()

    fun readRanges(): List<Json5Range> {
        read()
        return ranges
    }

    override fun readObject(): JsonObject {
        ranges.add(Json5Range(i, i + 1, Json5Token.ObjectBrace))
        return super.readObject().apply {
            ranges.add(Json5Range(i - 1, i, Json5Token.ObjectBrace))
        }
    }

    override fun readArray(): JsonArray {
        ranges.add(Json5Range(i, i + 1, Json5Token.ArrayBracket))
        return super.readArray().apply {
            ranges.add(Json5Range(i - 1, i, Json5Token.ArrayBracket))
        }
    }

    override fun readComma() {
        super.readComma()
        ranges.add(Json5Range(i - 1, i, Json5Token.Comma))
    }

    override fun readColon() {
        super.readColon()
        ranges.add(Json5Range(i - 1, i, Json5Token.Colon))
    }

    override fun readComment() {
        val start = i
        super.readComment().apply {
            ranges.add(Json5Range(start, i, Json5Token.Comments))
        }
    }

    override fun readWhitespace() {
        val start = i
        super.readWhitespace().apply {
            ranges.add(Json5Range(start, i, Json5Token.Whitespace))
        }
    }

    override fun readNullLiteral() {
        val start = i
        super.readNullLiteral().apply {
            ranges.add(Json5Range(start, i, Json5Token.NullLiteral))
        }
    }

    override fun readBooleanLiteral(v: Boolean) {
        val start = i
        super.readBooleanLiteral(v).apply {
            ranges.add(Json5Range(start, i, Json5Token.BooleanLiteral))
        }
    }

    override fun readNumber(): Json5Number {
        val start = i
        return super.readNumber().apply {
            ranges.add(Json5Range(start, i, Json5Token.NumberLiteral))
        }
    }

    override fun readString(): String {
        val start = i
        return super.readString().apply {
            ranges.add(Json5Range(start, i, Json5Token.StringLiteral))
        }
    }

    override fun readProperty(): String {
        val start = i
        return super.readProperty().apply {
            ranges.add(Json5Range(start, i, Json5Token.Property))
        }
    }

}