package li.songe.json5

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Json5EditorTest {
    @Test
    fun resolvesBracketPaths() {
        val document = Json5.parseToDocument("{server:{ports:[80,443]}}")

        assertEquals(
            "443",
            document[Json5Path["server"]["ports"][1]]?.toJsonElement()?.jsonPrimitive?.content,
        )
    }

    @Test
    fun editsPropertiesWithoutDroppingComments() {
        val source = "{\n  // server port\n  port: 80,\n  hosts: ['a', 'b',],\n}\n"
        val portPath = Json5Path["port"]
        val hostsPath = Json5Path["hosts"]

        val set = Json5.parseToDocument(source).set(portPath, JsonPrimitive(443))
        assertTrue("// server port" in set.text)
        assertEquals("443", set.document[portPath]?.toJsonElement()?.jsonPrimitive?.content)

        val renamed = set.document.renameProperty(portPath, "serverPort")
        assertTrue("serverPort: 443" in renamed.text)

        val inserted = renamed.document.insertArrayElement(hostsPath, 1, JsonPrimitive("middle"))
        assertEquals(
            listOf("a", "middle", "b"),
            inserted.document.toJsonElement().jsonObject["hosts"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        val removed = inserted.document.remove(hostsPath[0])
        assertEquals(
            listOf("middle", "b"),
            removed.document.toJsonElement().jsonObject["hosts"]!!.jsonArray.map { it.jsonPrimitive.content },
        )

        val added = removed.document.putProperty(Json5Path.Root, "enabled", JsonPrimitive(true))
        assertEquals(true, added.document.toJsonElement().jsonObject["enabled"]!!.jsonPrimitive.content.toBoolean())
        assertTrue("// server port" in added.text)
    }

    @Test
    fun selectsDuplicatePropertyOccurrence() {
        val document = Json5.parseToDocument("{a:1,a:2,a:3}")

        val first = document.set(Json5Path["a", 0], JsonPrimitive(10))
        assertEquals("{a:10,a:2,a:3}", first.text)

        val last = document.set(Json5Path["a"], JsonPrimitive(30))
        assertEquals("{a:1,a:2,a:30}", last.text)
    }

    @Test
    fun addsAndRemovesCommentsAndRejectsInvalidPaths() {
        val path = Json5Path["a"]
        val before = Json5.parseToDocument("{\n  a: 1\n}").addCommentBefore(path, "before")
        assertTrue("// before" in before.text)

        val after = before.document.addCommentAfter(path, "after")
        assertTrue("// after" in after.text)
        assertEquals(2, after.document.comments.size)

        val removed = after.document.removeComment(after.document.comments.first())
        assertEquals(1, removed.document.comments.size)

        assertFailsWith<IllegalArgumentException> {
            removed.document.set(Json5Path["missing"], JsonPrimitive(0))
        }
    }

    @Test
    fun appliesMultipleNonOverlappingEditsAgainstOriginalSource() {
        assertEquals(
            "A12D",
            "ABCD".applyJson5Edits(
                listOf(
                    Json5TextEdit(1, 1, "1"),
                    Json5TextEdit(2, 1, "2"),
                ),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            "ABCD".applyJson5Edits(
                listOf(Json5TextEdit(1, 2, ""), Json5TextEdit(2, 1, "")),
            )
        }
    }

    @Test
    fun preservesSameOffsetInsertionsNextToAReplacement() {
        assertEquals(
            "AYZXD",
            "ABCD".applyJson5Edits(
                listOf(
                    Json5TextEdit(1, 2, "X"),
                    Json5TextEdit(1, 0, "Y"),
                    Json5TextEdit(1, 0, "Z"),
                ),
            ),
        )
    }

    @Test
    fun appendsAfterATrailingLineCommentWithoutCommentingOutTheComma() {
        val source = "{\n  a: 1 // belongs to a\n}"

        val result = Json5.parseToDocument(source).putProperty(
            Json5Path.Root,
            "b",
            JsonPrimitive(2),
        )

        assertEquals(2, result.edits.size)
        assertEquals("1", result.document.toJsonElement().jsonObject["a"]!!.jsonPrimitive.content)
        assertEquals("2", result.document.toJsonElement().jsonObject["b"]!!.jsonPrimitive.content)
        assertTrue("// belongs to a" in result.text)
    }

    @Test
    fun removesFirstMiddleAndLastChildren() {
        val objectSource = "{a:1,b:2,c:3}"
        assertEquals(
            "{b:2,c:3}",
            Json5.parseToDocument(objectSource).remove(Json5Path["a"]).text,
        )
        assertEquals(
            "{a:1,c:3}",
            Json5.parseToDocument(objectSource).remove(Json5Path["b"]).text,
        )
        assertEquals(
            "{a:1,b:2}",
            Json5.parseToDocument(objectSource).remove(Json5Path["c"]).text,
        )

        val arraySource = "[1,2,3]"
        assertEquals("[2,3]", Json5.parseToDocument(arraySource).remove(Json5Path[0]).text)
        assertEquals("[1,3]", Json5.parseToDocument(arraySource).remove(Json5Path[1]).text)
        assertEquals("[1,2]", Json5.parseToDocument(arraySource).remove(Json5Path[2]).text)
    }

    @Test
    fun refusesStructuralEditsOnInvalidDocuments() {
        val document = Json5.parseToDocument("{a:1")

        assertFailsWith<Json5ParseException> {
            document.set(Json5Path["a"], JsonPrimitive(2))
        }
    }

    @Test
    fun removesCommentsOwnedByTheRemovedProperty() {
        val result = Json5.parseToDocument("{\n  a: 1 // remove me\n}")
            .remove(Json5Path["a"])

        assertTrue("remove me" !in result.text)
        assertTrue(result.document.isValid)
    }
}
