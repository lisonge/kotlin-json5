package li.songe.json5

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Json5SyntaxTest {
    @Test
    fun syntaxTapeCoversEverySourceCharacterExactlyOnce() {
        val inputs = listOf(
            "",
            "// head\r\n{a:'x',b:0x10}",
            "{/* unclosed",
            "{a:'unterminated\nnext:true}",
            "@{a:1,]",
            "+",
            "-",
        )

        for (source in inputs) {
            assertSyntaxCovers(source)
        }
    }

    @Test
    fun classifiesPropertyNamesAndLiteralRoles() {
        val source = "{true:false,null:null,'text':'x',number:NaN,array:[true,null,Infinity,unknown]}"
        val tokens = Json5.scanSyntax(source)
        val semanticKinds = setOf(
            Json5SyntaxKind.PropertyName,
            Json5SyntaxKind.NullLiteral,
            Json5SyntaxKind.BooleanLiteral,
            Json5SyntaxKind.NumberLiteral,
            Json5SyntaxKind.StringLiteral,
            Json5SyntaxKind.Identifier,
        )
        val roles = tokens.indices.mapNotNull { index ->
            val kind = tokens.kindAt(index)
            if (kind !in semanticKinds) return@mapNotNull null
            source.substring(tokens.startAt(index), tokens.endAt(index)) to kind
        }

        assertEquals(
            listOf(
                "true" to Json5SyntaxKind.PropertyName,
                "false" to Json5SyntaxKind.BooleanLiteral,
                "null" to Json5SyntaxKind.PropertyName,
                "null" to Json5SyntaxKind.NullLiteral,
                "'text'" to Json5SyntaxKind.PropertyName,
                "'x'" to Json5SyntaxKind.StringLiteral,
                "number" to Json5SyntaxKind.PropertyName,
                "NaN" to Json5SyntaxKind.NumberLiteral,
                "array" to Json5SyntaxKind.PropertyName,
                "true" to Json5SyntaxKind.BooleanLiteral,
                "null" to Json5SyntaxKind.NullLiteral,
                "Infinity" to Json5SyntaxKind.NumberLiteral,
                "unknown" to Json5SyntaxKind.Identifier,
            ),
            roles,
        )
    }

    @Test
    fun recoversPropertyClassificationAtLineBreak() {
        val source = "{a:1\ntrue:2}"
        val tokens = Json5.scanSyntax(source)
        val trueIndex = tokens.indices.single { index ->
            source.substring(tokens.startAt(index), tokens.endAt(index)) == "true"
        }

        assertEquals(Json5SyntaxKind.PropertyName, tokens.kindAt(trueIndex))
        assertSyntaxCovers(source)
    }

    @Test
    fun recoversPropertyClassificationAtLineBreakInsideBlockComment() {
        val source = "{a:1/* line\ncomment */true:2}"
        val tokens = Json5.scanSyntax(source)
        val trueIndex = tokens.indices.single { index ->
            source.substring(tokens.startAt(index), tokens.endAt(index)) == "true"
        }

        assertEquals(Json5SyntaxKind.PropertyName, tokens.kindAt(trueIndex))
        assertSyntaxCovers(source)
    }

    @Test
    fun preservesAsciiAndUnicodeIdentifierClassification() {
        val source = "{ascii_1:1,中文:2,a\u0301:3,join\u200C:4}"
        val tokens = Json5.scanSyntax(source)
        val propertyNames = tokens.indices.mapNotNull { index ->
            if (tokens.kindAt(index) != Json5SyntaxKind.PropertyName) return@mapNotNull null
            source.substring(tokens.startAt(index), tokens.endAt(index))
        }

        assertEquals(listOf("ascii_1", "中文", "a\u0301", "join\u200C"), propertyNames)
        assertSyntaxCovers(source)
    }

    @Test
    fun keepsIncompleteSyntaxInItsIntendedHighlightClass() {
        val source = "{a:'broken\n,b:1e+,c:/* open"
        val tokens = Json5.scanSyntax(source)
        val highlighted = tokens.indices.map { index ->
            source.substring(tokens.startAt(index), tokens.endAt(index)) to tokens.kindAt(index)
        }

        assertTrue("'broken" to Json5SyntaxKind.StringLiteral in highlighted)
        assertTrue("1e+" to Json5SyntaxKind.NumberLiteral in highlighted)
        assertTrue("/* open" to Json5SyntaxKind.Comment in highlighted)
        assertSyntaxCovers(source)
    }

    @Test
    fun exposesCompactSyntaxKinds() {
        val source = " \n//l\n/*b*/{}[],:key 1 's' @"
        val tokens = Json5.scanSyntax(source, ignoreWhitespace = false)

        assertEquals(
            listOf(
                Json5SyntaxKind.Whitespace,
                Json5SyntaxKind.Comment,
                Json5SyntaxKind.Whitespace,
                Json5SyntaxKind.Comment,
                Json5SyntaxKind.LeftBrace,
                Json5SyntaxKind.RightBrace,
                Json5SyntaxKind.LeftBracket,
                Json5SyntaxKind.RightBracket,
                Json5SyntaxKind.Comma,
                Json5SyntaxKind.Colon,
                Json5SyntaxKind.Identifier,
                Json5SyntaxKind.Whitespace,
                Json5SyntaxKind.NumberLiteral,
                Json5SyntaxKind.Whitespace,
                Json5SyntaxKind.StringLiteral,
                Json5SyntaxKind.Whitespace,
                Json5SyntaxKind.Invalid,
            ),
            tokens.indices.map(tokens::kindAt),
        )
        assertSyntaxCovers(source)
    }

    @Test
    fun ignoresWhitespaceByDefaultAndCanRetainIt() {
        val source = " \n{ value: true }"
        val compact = Json5.scanSyntax(source)
        val retained = Json5.scanSyntax(source, ignoreWhitespace = false)
        val streamedKinds = mutableListOf<Json5SyntaxKind>()
        val retainedStreamedKinds = mutableListOf<Json5SyntaxKind>()

        Json5.scanSyntax(source) { kind, _, _ -> streamedKinds.add(kind) }
        Json5.scanSyntax(source, ignoreWhitespace = false) { kind, _, _ ->
            retainedStreamedKinds.add(kind)
        }

        assertFalse(compact.indices.any { compact.kindAt(it) == Json5SyntaxKind.Whitespace })
        assertTrue(retained.indices.any { retained.kindAt(it) == Json5SyntaxKind.Whitespace })
        assertFalse(Json5SyntaxKind.Whitespace in streamedKinds)
        assertTrue(Json5SyntaxKind.Whitespace in retainedStreamedKinds)
        assertSyntaxCovers(source)
    }

    @Test
    fun coversEveryTypingPrefixAndRandomText() {
        val inputs = mutableListOf(
            "/",
            "/*",
            "'",
            "'value\nnext",
            "'value\r\nnext",
            "0x",
            "1e+",
            "\\u00",
            "{name:'line\\\r\nvalue',enabled:true,count:0x2a}",
        )
        val random = Random(0)
        val alphabet = "{}[],:/'\\\"+-._$ abcdefNInt0123456789\n\r@#"
        repeat(200) {
            inputs.add(
                buildString {
                    repeat(random.nextInt(1, 100)) {
                        append(alphabet[random.nextInt(alphabet.length)])
                    }
                },
            )
        }

        for (source in inputs) {
            for (length in 0..source.length) {
                assertSyntaxCovers(source.substring(0, length))
            }
        }
    }

    @Test
    fun compactResultHasContentEquality() {
        val left = Json5.scanSyntax("{value:true}")
        val right = Json5.scanSyntax(StringBuilder("{value:true}"))

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
        assertTrue(left.isNotEmpty())
        assertFalse(left.isEmpty())
    }

    @Test
    fun streamingAndMaterializedScanningProduceIdenticalSpans() {
        val inputs: List<CharSequence> = listOf(
            "",
            "// head\r\n{ascii_1:true,中文:null,items:[0x2a,'x'],broken:@}\n",
            StringBuilder("{a:1/* line\ncomment */b:Infinity}"),
            "{value:'unterminated\nnext:false}",
        )

        for (source in inputs) {
            for (ignoreWhitespace in listOf(true, false)) {
                val materialized = Json5.scanSyntax(source, ignoreWhitespace)
                val expected = materialized.indices.map { index ->
                    Triple(
                        materialized.kindAt(index),
                        materialized.startAt(index),
                        materialized.endAt(index),
                    )
                }
                val streamed = buildList {
                    Json5.scanSyntax(source, ignoreWhitespace) { kind, start, end ->
                        add(Triple(kind, start, end))
                    }
                }

                assertEquals(expected, streamed, source.toString())
            }
        }
    }

    @Test
    fun keepsDocumentTokensLexicallyContextFree() {
        val document = Json5.parseToDocument("{true:1,value:true}")
        val identifiers = document.tokens.filter { it.kind == Json5TokenKind.Identifier }

        assertEquals(listOf("true", "value", "true"), identifiers.map { document.text(it.range) })
        assertTrue(document.diagnostics.isEmpty())
    }

    @Test
    fun documentSeparatesLineCommentsFromLineBreaks() {
        val document = Json5.parseToDocument("// comment\r\nnull")

        assertEquals(
            listOf(Json5TokenKind.LineComment, Json5TokenKind.LineBreak, Json5TokenKind.Identifier),
            document.tokens.map { it.kind },
        )
    }

    @Test
    fun documentRecoversEveryIncompletePrefixAndReportsDiagnostics() {
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

    private fun assertSyntaxCovers(source: String) {
        val tokens = Json5.scanSyntax(source, ignoreWhitespace = false)
        var offset = 0
        val reconstructed = StringBuilder(source.length)
        for (index in tokens.indices) {
            assertEquals(offset, tokens.startAt(index), source)
            assertTrue(tokens.endAt(index) > tokens.startAt(index), source)
            offset = tokens.endAt(index)
            reconstructed.append(source, tokens.startAt(index), tokens.endAt(index))
        }
        assertEquals(source.length, offset, source)
        assertEquals(source, reconstructed.toString())
    }
}
