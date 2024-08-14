package li.songe.json5

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class Json5Test {
    private fun p5(string: String) = Json5.parseToJson5Element(string)

    private fun p(string: String) = Json.parseToJsonElement(string)

    // https://github.com/json5/json5/blob/main/test/parse.js
    @Test
    fun parse() {
        // objects
        assertEquals(
            p5("{}"),
            p("{}"),
        )
        assertEquals(
            p5(""" {"a":1} """),
            p(""" {"a":1} """),
        )
        assertEquals(
            p5(""" {'a':1} """),
            p(""" {"a":1} """),
        )
        assertEquals(
            p5(""" {a:1} """),
            p(""" {"a":1} """),
        )
        assertEquals(
            p5(" {\u0024_:1,_$:2,a\u200C:3} "),
            p(" {\"\$_\":1,\"_\$\":2,\"a\u200C\":3} "),
        )
        assertEquals(
            p5(""" {ùńîċõďë:9} """),
            p(""" {"ùńîċõďë":9} """),
        )
        assertEquals(
            p5(" {\\u0061\\u0062:1,\\u0024\\u005F:2,\\u005F\\u0024:3} "),
            p(" {\"ab\":1,\"\$_\":2,\"_\$\":3} "),
        )
        assertEquals(
            p5(""" {"__proto__":1} """).jsonObject["__proto__"],
            p(""" 1 """),
        )
        assertEquals(
            p5(""" {abc:1,def:2} """),
            p(""" {"abc": 1, "def": 2} """),
        )
        assertEquals(
            p5(""" {a:{b:2}} """),
            p(""" {"a": {"b": 2}} """),
        )

        // arrays
        assertEquals(
            p5(""" [] """),
            p(""" [] """),
        )
        assertEquals(
            p5(""" [1] """),
            p(""" [1] """),
        )
        assertEquals(
            p5(""" [1,2] """),
            p(""" [1, 2] """),
        )
        assertEquals(
            p5(""" [1,[2,3]] """),
            p(""" [1, [2, 3]] """),
        )

        // nulls
        assertEquals(
            p5(""" null """),
            p(""" null """),
        )

        // Booleans
        assertEquals(
            p5(""" true """),
            p(""" true """),
        )
        assertEquals(
            p5(""" false """),
            p(""" false """),
        )

        // numbers
        assertEquals(
            p5(""" [0,0.,0e0] """),
            p(""" [0, 0, 0] """),
        )
        assertEquals(
            p5(""" [1,23,456,7890] """),
            p(""" [1, 23, 456, 7890] """),
        )
        // kotlinx.serialization will keep the sign of -0
        assertEquals(
            p5(""" [-1,+2,-.1,-0] """),
            JsonArray(listOf(JsonPrimitive(-1), JsonPrimitive(2), JsonPrimitive(-0.1), JsonPrimitive(-0))),
        )
        assertEquals(
            p5(""" [.1,.23] """),
            p(""" [0.1, 0.23] """),
        )
        // 1e1 is integer in js, but double in kotlin
        assertEquals(
            p5(""" [1e0,1e1,1e01,1.e0,1.1e0,1e-1,1e+1] """),
            JsonArray(
                listOf(
                    JsonPrimitive(1e0),
                    JsonPrimitive(1e1),
                    JsonPrimitive(1e01),
                    JsonPrimitive(1.0e0),
                    JsonPrimitive(1.1e0),
                    JsonPrimitive(1e-1),
                    JsonPrimitive(1e+1)
                )
            ),
        )
        assertEquals(
            p5(""" [0x1,0x10,0xff,0xFF] """),
            p(""" [1, 16, 255, 255] """),
        )
        assertEquals(
            p5(""" [Infinity,-Infinity] """),
            p(""" [Infinity,-Infinity] """),
        )
        assertEquals(
            p5(""" NaN """),
            p(""" NaN """),
        )
        assertEquals(
            p5(""" -NaN """),
            JsonPrimitive(-Double.NaN),
        )
        assertEquals(
            p5(""" 1 """),
            p(""" 1 """),
        )
        // json5: e, json: E
        assertEquals(
            p5(""" +1.23e100 """),
            JsonPrimitive(1.23e100),
        )
        // json5: 1, json: 0x1
        assertEquals(
            p5(""" 0x1 """),
            JsonPrimitive(0x1),
        )
        // current json5 not support big int
//        assertEquals(
//            p5(""" -0x0123456789abcdefABCDEF """),
//            p(""" -0x0123456789abcdefABCDEF """),
//        )

        // strings
        assertEquals(
            p5(""" "abc" """),
            p(""" "abc" """),
        )
        assertEquals(
            p5(""" 'abc' """),
            p(""" "abc" """),
        )
        assertEquals(
            p5(""" ['"',"'"] """),
            JsonArray(listOf(JsonPrimitive("\""), JsonPrimitive("'"))),
        )
        assertEquals(
            p5("'\\b\\f\\n\\r\\t\\v\\0\\x0f\\u01fF\\\n\\\r\n\\\r\\\u2028\\\u2029\\a\\'\\\"'"),
            JsonPrimitive("\b\u000C\n\r\t\u000B\u0000\u000F\u01FF\u0061'\""),
        )
        assertEquals(
            p5(""" '\u2028\u2029' """),
            p(""" "\u2028\u2029" """),
        )

        // comments
        assertEquals(
            p5("{//comment\n}"),
            p(""" {} """),
        )
        assertEquals(
            p5(""" {}//comment """),
            p(""" {} """),
        )
        assertEquals(
            p5(" {/*comment\n** */} "),
            p(""" {} """),
        )

        // whitespace
        assertEquals(
            p5("{\t\u000B\u000C \u00A0\uFEFF\n\r\u2028\u2029\u2003}"),
            p(""" {} """),
        )
//        assertEquals(
//            p5("""  """),
//            p("""  """),
//        )
    }

    @Test
    fun format() {
        val element = Json5.parseToJson5Element("{'a-1':1,b:{c:['d',{f:233}]}}")
        println("element: $element")
        val formatted = Json5.encodeToString(element, 2)
        println("formatted:\n$formatted")
    }
}
