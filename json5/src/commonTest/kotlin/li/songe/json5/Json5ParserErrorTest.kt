package li.songe.json5

import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Json5ParserErrorTest {
    @Test
    fun rejectsIncompleteOrMismatchedContainers() {
        listOf("{", "[", "}", "]", "{},}").forEach { source ->
            assertFails("expected parsing to fail for: $source") {
                Json5.parseToJson5Element(source)
            }
        }
    }

    @Test
    fun rejectsBareLineBreaksInStringsAtTheirStart() {
        assertParseFailsAt("'a\nb'", 2)
        assertParseFailsAt("\"a\rb\"", 2)
        assertParseFailsAt("'a\r\nb'", 2)
    }

    private fun assertParseFailsAt(input: String, index: Int) {
        val exception = assertFailsWith<IllegalStateException> {
            Json5.parseToJson5Element(input)
        }
        assertTrue(exception.message.orEmpty().contains("at index $index"), exception.message)
    }
}
