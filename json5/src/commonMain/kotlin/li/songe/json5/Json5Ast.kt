package li.songe.json5

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A zero-based, half-open source range from [start] inclusive to [end] exclusive. */
@ConsistentCopyVisibility
public data class Json5SourceRange internal constructor(
    public val start: Int,
    public val end: Int,
) {
    public val length: Int
        get() = end - start

    public operator fun contains(offset: Int): Boolean = offset in start until end
}

public enum class Json5PropertyNameStyle {
    Identifier,
    SingleQuoted,
    DoubleQuoted,
}

/** A decoded property name together with its original source style and range. */
public data class Json5PropertyName(
    public val value: String,
    public val style: Json5PropertyNameStyle,
    public val range: Json5SourceRange,
)

public sealed class Json5AstNode(
    public open val range: Json5SourceRange,
    internal val nodeId: Int,
)

public sealed class Json5Value(
    override val range: Json5SourceRange,
    nodeId: Int,
) : Json5AstNode(range, nodeId)

public class Json5Object internal constructor(
    override val range: Json5SourceRange,
    public val properties: List<Json5Property>,
    nodeId: Int,
) : Json5Value(range, nodeId), Iterable<Json5Property> {

    public override fun iterator(): Iterator<Json5Property> = properties.iterator()
}

public class Json5Array internal constructor(
    override val range: Json5SourceRange,
    public val elements: List<Json5Value>,
    nodeId: Int,
) : Json5Value(range, nodeId), Iterable<Json5Value> {

    public operator fun get(index: Int): Json5Value = elements[index]

    public override fun iterator(): Iterator<Json5Value> = elements.iterator()
}

public class Json5Property internal constructor(
    override val range: Json5SourceRange,
    public val name: Json5PropertyName,
    public val value: Json5Value,
    nodeId: Int,
) : Json5AstNode(range, nodeId)

/** A decoded string value together with its original quote delimiter and source range. */
public class Json5StringLiteral internal constructor(
    override val range: Json5SourceRange,
    public val value: String,
    public val quote: Char,
    nodeId: Int,
) : Json5Value(range, nodeId)

public class Json5NumberLiteral internal constructor(
    override val range: Json5SourceRange,
    public val value: Json5Number,
    nodeId: Int,
) : Json5Value(range, nodeId)

public class Json5BooleanLiteral internal constructor(
    override val range: Json5SourceRange,
    public val value: Boolean,
    nodeId: Int,
) : Json5Value(range, nodeId)

public class Json5NullLiteral internal constructor(
    override val range: Json5SourceRange,
    nodeId: Int,
) : Json5Value(range, nodeId)

public enum class Json5CommentKind {
    Line,
    Block,
}

/** Describes how a comment is attached to an AST node or enclosing container. */
public enum class Json5CommentPlacement {
    /** Appears before its owner. */
    Leading,
    /** Appears after its owner on the same source line. */
    Trailing,
    /** Attached to an enclosing container rather than an adjacent child node. */
    Dangling,
}

internal enum class Json5CommentSlot {
    Default,
    AfterPropertyName,
    BeforePropertyValue,
}

/**
 * A source comment. [content] excludes its line- or block-comment delimiters,
 * while [range] covers the complete token.
 */
public class Json5Comment internal constructor(
    public val range: Json5SourceRange,
    public val kind: Json5CommentKind,
    public val content: String,
    public val placement: Json5CommentPlacement,
    internal val ownerNodeId: Int?,
    internal val slot: Json5CommentSlot,
)

internal fun Json5Value.toJsonElement(): JsonElement {
    val nodes = mutableListOf<Pair<Json5Value, Boolean>>()
    val values = mutableListOf<JsonElement>()
    nodes.add(this to false)

    while (nodes.isNotEmpty()) {
        val (node, visited) = nodes.pop()
        when (node) {
            is Json5StringLiteral -> values.add(JsonPrimitive(node.value))
            is Json5NumberLiteral -> values.add(JsonPrimitive(node.value))
            is Json5BooleanLiteral -> values.add(JsonPrimitive(node.value))
            is Json5NullLiteral -> values.add(JsonNull)
            is Json5Array -> {
                if (!visited) {
                    nodes.add(node to true)
                    for (index in node.elements.indices.reversed()) {
                        nodes.add(node.elements[index] to false)
                    }
                } else {
                    val start = values.size - node.elements.size
                    val elements = values.subList(start, values.size).toList()
                    repeat(node.elements.size) { values.removeAt(values.lastIndex) }
                    values.add(JsonArray(elements))
                }
            }

            is Json5Object -> {
                if (!visited) {
                    nodes.add(node to true)
                    for (index in node.properties.indices.reversed()) {
                        nodes.add(node.properties[index].value to false)
                    }
                } else {
                    val start = values.size - node.properties.size
                    val properties = linkedMapOf<String, JsonElement>()
                    node.properties.forEachIndexed { index, property ->
                        properties[property.name.value] = values[start + index]
                    }
                    repeat(node.properties.size) { values.removeAt(values.lastIndex) }
                    values.add(JsonObject(properties))
                }
            }
        }
    }
    return values.single()
}
