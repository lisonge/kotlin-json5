package li.songe.json5

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Json5EncoderTest {
    private fun encodeWithQuoteStrategy(
        value: String,
        strategy: Json5QuoteStrategy,
    ) = Json5.encodeString(value, Json5EncoderConfig(quoteStrategy = strategy))

    @Test
    fun encodesNestedElementsWithIndentation() {
        val element = Json5.parseToJsonElement("{'a-1':1,b:{c:['d',{f:233,h:'str'},NaN]}}")

        assertEquals(
            """
                {
                  'a-1': 1,
                  b: {
                    c: [
                      'd',
                      {
                        f: 233,
                        h: 'str'
                      },
                      NaN
                    ]
                  }
                }
            """.trimIndent(),
            Json5.encodeToString(element, Json5EncoderConfig(indent = "  ")),
        )
    }

    @Test
    fun appliesConfiguredQuoteStrategy() {
        assertEquals(
            """  '"a\''  """.trim(),
            encodeWithQuoteStrategy("""  "a'  """.trim(), Json5QuoteStrategy.Single),
        )
        assertEquals(
            """  "\"a'"  """.trim(),
            encodeWithQuoteStrategy("""  "a'  """.trim(), Json5QuoteStrategy.Double),
        )
        assertEquals(
            """  'a"'  """.trim(),
            encodeWithQuoteStrategy("""  a"  """.trim(), Json5QuoteStrategy.PreferSingle),
        )
        assertEquals(
            """  "a'"  """.trim(),
            encodeWithQuoteStrategy("""  a'  """.trim(), Json5QuoteStrategy.PreferSingle),
        )
        assertEquals(
            """  "a'"  """.trim(),
            encodeWithQuoteStrategy("""  a'  """.trim(), Json5QuoteStrategy.PreferDouble),
        )
        assertEquals(
            """  'a"'  """.trim(),
            encodeWithQuoteStrategy("""  a"  """.trim(), Json5QuoteStrategy.PreferDouble),
        )
    }

    @Test
    fun encodesStandaloneStringsAndPropertyNames() {
        assertEquals("'a\\'b'", Json5.encodeString("a'b"))
        assertEquals("title", Json5.encodeKey("title"))
        assertEquals("'not-valid'", Json5.encodeKey("not-valid"))
        assertEquals(
            "\"not-valid\"",
            Json5.encodeKey(
                "not-valid",
                Json5EncoderConfig(quoteStrategy = Json5QuoteStrategy.Double),
            ),
        )
        assertEquals(
            "'title'",
            Json5.encodeKey("title", Json5EncoderConfig(unquotedKey = false)),
        )
        assertEquals(
            Json5.encodeToString(JsonPrimitive("a'b")),
            Json5.encodeString("a'b"),
        )
    }

    @Test
    fun rejectsIndentationThatCannotProduceValidJson5() {
        for (indent in listOf("x", "\n", " \tx")) {
            assertFailsWith<IllegalArgumentException> {
                Json5EncoderConfig(indent = indent)
            }
        }
    }
}
