package li.songe.json5

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class Json5EncoderTest {
    private fun encodeString(
        value: String,
        config: Json5EncoderConfig = Json5.encoderConfig,
    ) = Json5.encodeToString(JsonPrimitive(value), config)

    private fun encodeWithQuoteStrategy(
        value: String,
        strategy: Json5QuoteStrategy,
    ) = encodeString(value, Json5EncoderConfig(quoteStrategy = strategy))

    @Test
    fun encodesNestedElementsWithIndentation() {
        val element = Json5.parseToJson5Element("{'a-1':1,b:{c:['d',{f:233,h:'str'},NaN]}}")

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
}
