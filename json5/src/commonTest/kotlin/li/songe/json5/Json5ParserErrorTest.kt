package li.songe.json5

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Json5ParserErrorTest {
    @Test
    fun rejectsIncompleteOrMismatchedContainers() {
        listOf("{", "[", "}", "]", "{},}").forEach { source ->
            assertFails("expected parsing to fail for: $source") {
                Json5.parseToJsonElement(source)
            }
        }
    }

    @Test
    fun rejectsBareLineBreaksInStringsAtTheirStart() {
        assertParseFailsAt("'a\nb'", 2)
        assertParseFailsAt("\"a\rb\"", 2)
        assertParseFailsAt("'a\r\nb'", 2)
    }

    @Test
    fun exposesTypedParseErrorsWithOffsets() {
        val exception = assertFailsWith<Json5ParseException> {
            Json5.parseToJsonElement("{value:'\\1'}")
        }

        assertEquals(9, exception.offset)
        assertEquals(Json5DiagnosticCode.InvalidEscape, exception.diagnostic.code)
    }

    @Test
    fun recoveringDocumentsKeepAllDiagnosticsWithoutThrowing() {
        val document = Json5.parseToDocument("{a 1,b:0x,c:'open")

        assertTrue(document.diagnostics.any { it.code == Json5DiagnosticCode.ExpectedColon })
        assertTrue(document.diagnostics.any { it.code == Json5DiagnosticCode.InvalidNumber })
        assertTrue(document.diagnostics.any { it.code == Json5DiagnosticCode.UnclosedString })
        assertTrue(document.diagnostics.any { it.code == Json5DiagnosticCode.UnclosedContainer })
        assertFailsWith<Json5ParseException> { document.toJsonElement() }
    }

    private fun assertParseFailsAt(input: String, index: Int) {
        val exception = assertFailsWith<IllegalStateException> {
            Json5.parseToJsonElement(input)
        }
        assertTrue(exception.message.orEmpty().contains("at index $index"), exception.message)
    }
}
