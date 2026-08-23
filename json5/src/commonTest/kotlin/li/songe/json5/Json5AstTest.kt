package li.songe.json5

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Json5AstTest {
    @Test
    fun preservesSourceTokensCommentsAndDuplicateProperties() {
        val source = "// head\n{a:0x10,a:'last',\n  // list\n  list:[true]} // tail"
        val document = Json5.parseToDocument(source)
        val root = assertIs<Json5Object>(document.root)

        assertTrue(document.diagnostics.isEmpty())
        assertEquals(listOf("a", "a", "list"), root.map { it.name.value })
        val array = assertIs<Json5Array>(root.properties.last().value)
        assertEquals(array.elements.single(), array[0])
        assertEquals(array.elements, array.toList())
        assertEquals("0x10", document.text(root.properties[0].value))
        assertEquals(Json5PropertyNameStyle.Identifier, root.properties[0].name.style)
        assertEquals('\'', assertIs<Json5StringLiteral>(root.properties[1].value).quote)
        assertEquals("last", document.toJsonElement().jsonObject["a"]?.jsonPrimitive?.content)
        assertEquals(source, document.tokens.joinToString("") { document.text(it.range) })
        assertEquals(3, document.comments.size)
        assertTrue(document.comments.any { it.content.trim() == "head" })
        assertTrue(document.comments.any { it.content.trim() == "list" })
        assertTrue(document.comments.any { it.content.trim() == "tail" })
    }

    @Test
    fun findsSmallestNodeAtOffsetAndCommentsForNode() {
        val source = "// root\n{\n  // value\n  enabled:true\n}"
        val document = Json5.parseToDocument(source)
        val root = assertIs<Json5Object>(document.root)
        val property = root.properties.single()

        assertEquals(property, document.findNodeAt(source.indexOf("enabled")))
        assertEquals(property.value, document[Json5Path["enabled"]])
        assertTrue(document.commentsOf(property, Json5CommentPlacement.Leading).isNotEmpty())
        assertTrue(document.commentsOf(root, Json5CommentPlacement.Leading).isNotEmpty())
    }

    @Test
    fun parsesDeeplyNestedArraysWithoutUsingTheCallStack() {
        val depth = 2_000
        val source = "[".repeat(depth) + "0" + "]".repeat(depth)
        var value: Json5Value = requireNotNull(Json5.parseToDocument(source).root)

        repeat(depth) {
            value = assertIs<Json5Array>(value).elements.single()
        }
        assertIs<Json5NumberLiteral>(value)
    }

    @Test
    fun keepsCommentsInsideTheirSmallestContainer() {
        val document = Json5.parseToDocument("{a:1,b:{/* inside */},c:2}")
        val inner = assertIs<Json5Object>(document[Json5Path["b"]])
        val comment = document.comments.single()

        assertEquals(Json5CommentPlacement.Dangling, comment.placement)
        assertEquals(listOf(comment), document.commentsOf(inner, Json5CommentPlacement.Dangling))
    }

    @Test
    fun freezesMutableInputAndKeepsNodeCollectionsReadOnly() {
        val input = StringBuilder("{a:[1]}")
        val document = Json5.parseToDocument(input)
        input[1] = 'b'

        assertEquals("{a:[1]}", document.source)
        assertEquals("a", assertIs<Json5Object>(document.root).properties.single().name.value)
    }

    @Test
    fun attachesSameLinePropertyCommentsToTheProperty() {
        val document = Json5.parseToDocument("{a:1 // property\n}")
        val property = assertIs<Json5Object>(document.root).properties.single()

        assertEquals(1, document.commentsOf(property, Json5CommentPlacement.Trailing).size)
    }
}
