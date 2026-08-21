package li.songe.json5

import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Json5LooseRangeTest {
    @Test
    fun parsesElementAndReportsLooseRangesSeparately() {
        val input = "{null:1, null:1}// \n"
        val element = Json5.parseToJson5Element(input)
        val ranges = Json5.parseToJson5LooseRanges(input)

        assertEquals(Json.parseToJsonElement("""{"null":1}"""), element)
        assertEquals(
            listOf(
                Json5LooseRange(0, 1, Json5Token.LeftBrace),
                Json5LooseRange(1, 5, Json5Token.Property),
                Json5LooseRange(5, 6, Json5Token.Colon),
                Json5LooseRange(6, 7, Json5Token.NumberLiteral),
                Json5LooseRange(7, 8, Json5Token.Comma),
                Json5LooseRange(8, 9, Json5Token.Whitespace),
                Json5LooseRange(9, 13, Json5Token.Property),
                Json5LooseRange(13, 14, Json5Token.Colon),
                Json5LooseRange(14, 15, Json5Token.NumberLiteral),
                Json5LooseRange(15, 16, Json5Token.RightBrace),
                Json5LooseRange(16, 20, Json5Token.Comment),
            ),
            ranges,
        )
        assertRangesCoverInput(input, ranges.map { it.start to it.end })
    }

    @Test
    fun reportsLooseRangesForInvalidFragments() {
        val input = "#{1:1+,b:true1}//"
        val ranges = Json5.parseToJson5LooseRanges(input)

        assertEquals(
            listOf(
                Json5LooseRange(0, 1, null),
                Json5LooseRange(1, 2, Json5Token.LeftBrace),
                Json5LooseRange(2, 3, Json5Token.NumberLiteral),
                Json5LooseRange(3, 4, Json5Token.Colon),
                Json5LooseRange(4, 6, Json5Token.NumberLiteral),
                Json5LooseRange(6, 7, Json5Token.Comma),
                Json5LooseRange(7, 8, Json5Token.Property),
                Json5LooseRange(8, 9, Json5Token.Colon),
                Json5LooseRange(9, 14, Json5Token.Property),
                Json5LooseRange(14, 15, Json5Token.RightBrace),
                Json5LooseRange(15, 17, Json5Token.Comment),
            ),
            ranges,
        )
        assertRangesCoverInput(input, ranges.map { it.start to it.end })
        assertTrue(ranges.any { it.token == null })
    }

    @Test
    fun recoversAfterBareLineBreakInIncompleteString() {
        val input = "{a:'broken\n,b:true}"

        assertEquals(
            listOf(
                Json5LooseRange(0, 1, Json5Token.LeftBrace),
                Json5LooseRange(1, 2, Json5Token.Property),
                Json5LooseRange(2, 3, Json5Token.Colon),
                Json5LooseRange(3, 10, Json5Token.StringLiteral),
                Json5LooseRange(10, 11, Json5Token.Whitespace),
                Json5LooseRange(11, 12, Json5Token.Comma),
                Json5LooseRange(12, 13, Json5Token.Property),
                Json5LooseRange(13, 14, Json5Token.Colon),
                Json5LooseRange(14, 18, Json5Token.BooleanLiteral),
                Json5LooseRange(18, 19, Json5Token.RightBrace),
            ),
            Json5.parseToJson5LooseRanges(input),
        )
    }

    @Test
    fun keepsEscapedIdentifierPartsInOneLooseRange() {
        val input = "{\\u0061\\u0062:1}"

        assertEquals(
            listOf(
                Json5LooseRange(0, 1, Json5Token.LeftBrace),
                Json5LooseRange(1, 13, Json5Token.Property),
                Json5LooseRange(13, 14, Json5Token.Colon),
                Json5LooseRange(14, 15, Json5Token.NumberLiteral),
                Json5LooseRange(15, 16, Json5Token.RightBrace),
            ),
            Json5.parseToJson5LooseRanges(input),
        )
    }

    @Test
    fun coversEveryNonEmptyTypingPrefix() {
        val input = "{name:'line\\\r\nvalue',enabled:true,count:0x2a}"

        for (length in 1..input.length) {
            val prefix = input.substring(0, length)
            val ranges = Json5.parseToJson5LooseRanges(prefix)
            assertRangesCoverInput(prefix, ranges.map { it.start to it.end })
        }
    }

    @Test
    fun recoversObjectPropertyClassificationAtLineBreak() {
        val input = "{a:1\ntrue:2}"
        val ranges = Json5.parseToJson5LooseRanges(input)
        val trueRange = ranges.single { input.substring(it.start, it.end) == "true" }

        assertEquals(Json5Token.Property, trueRange.token)
        assertRangesCoverInput(input, ranges.map { it.start to it.end })
    }

    @Test
    fun reportsCompleteLooseRangesForValidInput() {
        val input = "{null:true,true:null,NaN:Infinity,text:'value\u2028\u2029',array:[false,0x2a]}"
        Json5.parseToJson5Element(input)
        val looseRanges = Json5.parseToJson5LooseRanges(input)

        assertTrue(looseRanges.all { it.token != null })
        assertRangesCoverInput(input, looseRanges.map { it.start to it.end })
    }

    @Test
    fun coversIncompleteLexemesAndRandomText() {
        val inputs = mutableListOf(
            "/",
            "/*",
            "'",
            "'value\nnext",
            "'value\r\nnext",
            "'value\u2028next",
            "'value\u2029next",
            "0x",
            "1e+",
            "\\u00",
        )
        val random = Random(0)
        val alphabet = "{}[],:/'\\\"+-._$ abcdef0123456789\n\r"
        repeat(200) {
            inputs.add(
                buildString {
                    repeat(random.nextInt(1, 100)) {
                        append(alphabet[random.nextInt(alphabet.length)])
                    }
                },
            )
        }

        inputs.forEach { input ->
            val ranges = Json5.parseToJson5LooseRanges(input)
            assertTrue(ranges.all { it.start < it.end })
            assertRangesCoverInput(input, ranges.map { it.start to it.end })
        }
    }

    private fun assertRangesCoverInput(input: String, ranges: List<Pair<Int, Int>>) {
        assertTrue(ranges.isNotEmpty())
        assertEquals(0, ranges.first().first)
        assertEquals(input.length, ranges.last().second)
        ranges.zipWithNext().forEach { (left, right) ->
            assertEquals(left.second, right.first)
        }
        assertEquals(input, ranges.joinToString("") { (start, end) -> input.substring(start, end) })
    }
}
