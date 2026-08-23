package li.songe.json5

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Json5FormatterTest {
    @Test
    fun formatsCommentsAndPreservesCrLfAndRawLiterals() {
        val source = "// head\r\n{a:0x10,// one\r\nb:[true,/* block */false,],}\r\n"
        val expected = "// head\r\n{\r\n" +
            "  a: 0x10, // one\r\n" +
            "  b: [\r\n" +
            "    true, /* block */ false,\r\n" +
            "  ],\r\n" +
            "}\r\n"

        val formatted = Json5.format(source)

        assertEquals(expected, formatted)
        assertEquals(formatted, Json5.format(formatted))
        assertEquals(
            Json5.parseToJsonElement(source),
            Json5.parseToJsonElement(formatted),
        )
    }

    @Test
    fun appliesTrailingCommaAndFinalNewlinePolicies() {
        val source = "{a:[1,2]}"
        val formatted = Json5.format(
            source,
            Json5FormatConfig(
                trailingComma = Json5TrailingComma.Always,
                finalNewline = Json5FinalNewline.Always,
            ),
        )

        assertEquals("{\n  a: [\n    1,\n    2,\n  ],\n}\n", formatted)
        assertEquals(formatted, Json5.format(formatted, Json5FormatConfig(
            trailingComma = Json5TrailingComma.Always,
            finalNewline = Json5FinalNewline.Always,
        )))
    }

    @Test
    fun returnsApplicableTextEdits() {
        val source = "{a:1,b:2}"
        val edits = Json5.formatToEdits(source)
        val formatted = source.applyJson5Edits(edits)

        assertEquals(Json5.format(source), formatted)
        assertTrue(edits.size <= 1)
        assertEquals(emptyList(), Json5.formatToEdits(formatted))
    }

    @Test
    fun keepsDanglingAndTrailingBlockCommentsInsideTheirContainers() {
        assertEquals(
            "{\n  /* empty */\n}",
            Json5.format("{/* empty */}"),
        )
        assertEquals(
            "{\n  a: 1 /* value */\n}",
            Json5.format("{a:1/* value */}"),
        )
    }

    @Test
    fun preservesCommentsBetweenPropertySyntaxTokens() {
        val source = "{/* before */key/* key */:/* colon */1/* value */}"
        val formatted = Json5.format(source)

        assertEquals(
            documentCommentContents(source),
            documentCommentContents(formatted),
        )
        assertTrue("key /* key */ : /* colon */ 1" in formatted)
        assertEquals(Json5.parseToJsonElement(source), Json5.parseToJsonElement(formatted))
        assertEquals(formatted, Json5.format(formatted))
    }

    @Test
    fun keepsLineCommentsFromCommentingOutPropertyPunctuation() {
        val source = "{key// after key\n:// after colon\n1}"
        val formatted = Json5.format(source)

        assertEquals(Json5.parseToJsonElement(source), Json5.parseToJsonElement(formatted))
        assertEquals(formatted, Json5.format(formatted))
    }

    @Test
    fun refusesToFormatInvalidDocuments() {
        assertFailsWith<Json5ParseException> {
            Json5.format("{a:1")
        }
    }

    private fun documentCommentContents(source: String): List<String> =
        Json5.parseToDocument(source).comments.map { it.content }
}
