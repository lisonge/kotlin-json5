package li.songe.json5

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Json5SyntaxTest {
    @Test
    fun tokenTapeCoversEverySourceCharacterExactlyOnce() {
        val inputs = listOf(
            "// head\r\n{a:'x',b:0x10}",
            "{/* unclosed",
            "{a:'unterminated\nnext:true}",
            "@{a:1,]",
            "+",
            "-",
        )

        for (source in inputs) {
            val tokens = Json5.tokenize(source)
            var offset = 0
            for (token in tokens) {
                assertEquals(offset, token.range.start, source)
                assertTrue(token.range.end > token.range.start, source)
                offset = token.range.end
            }
            assertEquals(source.length, offset, source)
            assertEquals(source, tokens.joinToString("") { source.substring(it.range.start, it.range.end) })
        }
    }

    @Test
    fun tokenizationMatchesRecoveringDocumentTokens() {
        val inputs = listOf(
            "",
            "// head\r\n{a:'x',b:0x10}",
            "{a 1,b:0x,c:'open",
            "{a:'unterminated\nnext:true}",
            "@{a:1,]",
            "/* unclosed",
        )

        for (source in inputs) {
            assertEquals(Json5.parseToDocument(source).tokens, Json5.tokenize(source), source)
        }
    }

    @Test
    fun keepsLexicalKindsIndependentFromGrammarContext() {
        val document = Json5.parseToDocument("{true:1,value:true}")
        val identifiers = document.tokens.filter { it.kind == Json5TokenKind.Identifier }

        assertEquals(listOf("true", "value", "true"), identifiers.map { document.text(it.range) })
        assertTrue(document.diagnostics.isEmpty())
    }

    @Test
    fun separatesLineCommentsFromLineBreaks() {
        val document = Json5.parseToDocument("// comment\r\nnull")

        assertEquals(
            listOf(Json5TokenKind.LineComment, Json5TokenKind.LineBreak, Json5TokenKind.Identifier),
            document.tokens.map { it.kind },
        )
    }

    @Test
    fun recoversEveryIncompletePrefixAndReportsDiagnostics() {
        val source = "{server:{port:8080},enabled:true}"
        for (end in 0..source.length) {
            val prefix = source.substring(0, end)
            val document = Json5.parseToDocument(prefix)
            assertEquals(prefix, document.tokens.joinToString("") { document.text(it.range) })
            if (end < source.length) assertFalse(document.isValid)
        }

        val recovered = Json5.parseToDocument("{a:1 b:2")
        assertNotNull(recovered.root)
        assertTrue(recovered.diagnostics.any { it.code == Json5DiagnosticCode.ExpectedCommaOrEnd })
        assertTrue(recovered.diagnostics.any { it.code == Json5DiagnosticCode.UnclosedContainer })
    }

    @Test
    fun exposesEveryLexicalKindWithoutGrammarClassification() {
        val source = " \n//l\n/*b*/{}[],:key 1 's' @"
        val tokens = Json5.tokenize(source)

        assertEquals(
            listOf(
                Json5TokenKind.Whitespace,
                Json5TokenKind.LineBreak,
                Json5TokenKind.LineComment,
                Json5TokenKind.LineBreak,
                Json5TokenKind.BlockComment,
                Json5TokenKind.LeftBrace,
                Json5TokenKind.RightBrace,
                Json5TokenKind.LeftBracket,
                Json5TokenKind.RightBracket,
                Json5TokenKind.Comma,
                Json5TokenKind.Colon,
                Json5TokenKind.Identifier,
                Json5TokenKind.Whitespace,
                Json5TokenKind.Number,
                Json5TokenKind.Whitespace,
                Json5TokenKind.String,
                Json5TokenKind.Whitespace,
                Json5TokenKind.Invalid,
            ),
            tokens.map { it.kind },
        )
    }
}
