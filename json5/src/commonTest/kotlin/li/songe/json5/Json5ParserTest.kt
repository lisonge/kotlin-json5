package li.songe.json5

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class Json5ParserTest {
    private fun parseJson5(source: String) = Json5.parseToJsonElement(source)

    private fun parseJson(source: String) = Json.parseToJsonElement(source)

    // Based on https://github.com/json5/json5/blob/main/test/parse.js
    @Test
    fun parsesObjectSyntax() {
        assertEquals(parseJson("{}"), parseJson5("{}"))
        assertEquals(parseJson(""" {"a":1} """), parseJson5(""" {"a":1} """))
        assertEquals(parseJson(""" {"a":1} """), parseJson5(""" {'a':1} """))
        assertEquals(parseJson(""" {"a":1} """), parseJson5(""" {a:1} """))
        assertEquals(
            parseJson(" {\"\$_\":1,\"_$\":2,\"a\u200C\":3} "),
            parseJson5(" {\u0024_:1,_$:2,a\u200C:3} "),
        )
        assertEquals(parseJson(""" {"ùńîċõďë":9} """), parseJson5(""" {ùńîċõďë:9} """))
        assertEquals(
            parseJson(" {\"ab\":1,\"\$_\":2,\"_$\":3} "),
            parseJson5(" {\\u0061\\u0062:1,\\u0024\\u005F:2,\\u005F\\u0024:3} "),
        )
        assertEquals(
            parseJson("1"),
            parseJson5(""" {"__proto__":1} """).jsonObject["__proto__"],
        )
        assertEquals(
            parseJson(""" {"abc":1,"def":2} """),
            parseJson5(""" {abc:1,def:2} """),
        )
        assertEquals(parseJson(""" {"a":{"b":2}} """), parseJson5(""" {a:{b:2}} """))
    }

    @Test
    fun parsesArraysAndPrimitiveValues() {
        assertEquals(parseJson("[]"), parseJson5("[]"))
        assertEquals(parseJson("[1]"), parseJson5("[1]"))
        assertEquals(parseJson("[1,2]"), parseJson5("[1,2]"))
        assertEquals(parseJson("[1,[2,3]]"), parseJson5("[1,[2,3]]"))
        assertEquals(parseJson("null"), parseJson5("null"))
        assertEquals(parseJson("true"), parseJson5("true"))
        assertEquals(parseJson("false"), parseJson5("false"))
    }

    @Test
    fun parsesJson5NumberSyntax() {
        assertEquals(parseJson("[0,0,0]"), parseJson5("[0,0.,0e0]"))
        assertEquals(parseJson("[1,23,456,7890]"), parseJson5("[1,23,456,7890]"))
        assertEquals(
            JsonArray(listOf(JsonPrimitive(-1), JsonPrimitive(2), parseJson("-0.1"), parseJson("-0"))),
            parseJson5("[-1,+2,-.1,-0]"),
        )
        assertEquals(parseJson("[1,0.1,0.23]"), parseJson5("[1.,.1,.23]"))
        assertEquals(parseJson("[1e+3,0.1e+3]"), parseJson5("[1.e+3,.1e+3]"))
        assertEquals(
            parseJson("[1e0,1e1,1e01,1.0e0,1.1e0,1e-1,1e+1]"),
            parseJson5("[1e0,1e1,1e01,1.0e0,1.1e0,1e-1,1e+1]"),
        )
        assertEquals(parseJson("[1,16,255,255]"), parseJson5("[0x1,0x10,0xff,0xFF]"))
        assertEquals(parseJson("[Infinity,-Infinity]"), parseJson5("[Infinity,-Infinity]"))
        assertEquals(parseJson("[NaN,-NaN]"), parseJson5("[NaN,-NaN]"))
        assertEquals(parseJson("1"), parseJson5("1"))
        assertEquals(parseJson("[1.23e100,1E2]"), parseJson5("[+1.23e100,1E2,]"))
        assertEquals(parseJson("1"), parseJson5("0x1"))
        assertEquals(
            parseJson("[-1375488932539311409843695]"),
            parseJson5("[-0x0123456789abcdefABCDEF,]"),
        )
    }

    @Test
    fun parsesStringsAndEscapedLineContinuations() {
        assertEquals(parseJson("\"abc\""), parseJson5("\"abc\""))
        assertEquals(parseJson("\"abc\""), parseJson5("'abc'"))
        assertEquals(
            JsonArray(listOf(JsonPrimitive("\""), JsonPrimitive("'"))),
            parseJson5("['\"',\"'\"]"),
        )
        assertEquals(
            JsonPrimitive("\b\u000C\n\r\t\u000B\u0000\u000F\u01FF\u0061'\""),
            parseJson5("'\\b\\f\\n\\r\\t\\v\\0\\x0f\\u01fF\\\n\\\r\n\\\r\\\u2028\\\u2029\\a\\'\\\"'"),
        )
        assertEquals(JsonPrimitive("\u2028\u2029"), parseJson5("'\u2028\u2029'"))
        assertEquals(JsonPrimitive("ab"), parseJson5("'a\\\nb'"))
        assertEquals(JsonPrimitive("ab"), parseJson5("'a\\\rb'"))
        assertEquals(JsonPrimitive("ab"), parseJson5("'a\\\r\nb'"))
    }

    @Test
    fun skipsCommentsAndJson5Whitespace() {
        assertEquals(parseJson("{}"), parseJson5("{//comment\n}"))
        assertEquals(parseJson("{}"), parseJson5("{}//comment"))
        assertEquals(parseJson("{}"), parseJson5(" {/*comment\n** */} "))
        assertEquals(parseJson("{}"), parseJson5("{\t\u000B\u000C \u00A0\uFEFF\n\r\u2028\u2029\u2003}"))
        assertEquals(parseJson("{}"), parseJson5("{}//"))
    }

    @Test
    fun directSemanticParsingMatchesLosslessDocumentConversion() {
        val sources = listOf(
            "null",
            "[1,.2,0x10,NaN,Infinity,]",
            "{a:1,a:'last',nested:{enabled:true},list:[false,null]}",
            "// head\r\n{key/* name */:/* value */'text'}// tail",
        )

        for (source in sources) {
            assertEquals(
                Json5.parseToJsonElement(source),
                Json5.parseToDocument(source).toJsonElement(),
                source,
            )
        }
    }

    @Test
    fun directSemanticParsingDoesNotUseTheCallStack() {
        val depth = 2_000
        var value = Json5.parseToJsonElement("[".repeat(depth) + "0" + "]".repeat(depth))

        repeat(depth) { value = value.jsonArray.single() }
        assertEquals(JsonPrimitive(0), value)
    }
}
