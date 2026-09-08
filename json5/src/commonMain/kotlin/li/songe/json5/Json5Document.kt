package li.songe.json5

import kotlinx.serialization.json.JsonElement

/**
 * A source-aware parse result that retains tokens, comments, ranges, and recoverable diagnostics.
 * [root] may be `null` when no value could be recovered.
 */
public class Json5Document internal constructor(
    public val source: String,
    public val root: Json5Value?,
    tokens: List<Json5Token>,
    diagnostics: List<Json5Diagnostic>,
    comments: List<Json5Comment>,
) {
    public val tokens: List<Json5Token> = tokens

    public val diagnostics: List<Json5Diagnostic> = diagnostics

    public val comments: List<Json5Comment> = comments

    public val isValid: Boolean
        get() = diagnostics.isEmpty() && root != null

    /**
     * Returns this document when it contains one valid root value.
     *
     * @throws Json5ParseException for the first diagnostic or when the root is missing.
     */
    public fun requireValid(): Json5Document {
        requireRoot()
        return this
    }

    /**
     * Converts the root to a [JsonElement]. Comments and source spelling are discarded;
     * when object property names are duplicated, the last value is retained.
     *
     * @throws Json5ParseException if this document is invalid or has no root.
     */
    public fun toJsonElement(): JsonElement = requireRoot().toJsonElement()

    private fun requireRoot(): Json5Value {
        diagnostics.firstOrNull()?.let { throw Json5ParseException(it) }
        return root ?: throw Json5ParseException(
            Json5Diagnostic(
                Json5DiagnosticCode.ExpectedValue,
                Json5SourceRange(source.length, source.length),
                "Expected a JSON5 value",
            ),
        )
    }

    public fun text(range: Json5SourceRange): String {
        require(range.end <= source.length) { "range ends outside of the document" }
        return source.substring(range.start, range.end)
    }

    public fun text(node: Json5AstNode): String = text(node.range)

    /** Returns comments attached to [node], optionally filtered by [placement]. */
    public fun commentsOf(
        node: Json5AstNode,
        placement: Json5CommentPlacement? = null,
    ): List<Json5Comment> = comments.filter { comment ->
        comment.ownerNodeId == node.nodeId && (placement == null || comment.placement == placement)
    }

    /** Returns the most specific AST node containing [offset], or `null` outside the root range. */
    public fun findNodeAt(offset: Int): Json5AstNode? {
        if (offset !in source.indices) return null
        var current: Json5AstNode = root?.takeIf { offset in it.range } ?: return null

        while (true) {
            val next = when (current) {
                is Json5Object -> childAt(current.properties, offset)
                is Json5Array -> childAt(current.elements, offset)
                is Json5Property -> current.value.takeIf { offset in it.range }
                else -> null
            } ?: return current
            current = next
        }
    }
}

internal fun createJson5Document(
    source: String,
    root: Json5Value?,
    tokens: List<Json5Token>,
    diagnostics: List<Json5Diagnostic>,
): Json5Document {
    val comments = bindJson5Comments(source, root, tokens)
    return Json5Document(source, root, tokens, diagnostics, comments)
}

private fun childAt(children: List<Json5AstNode>, offset: Int): Json5AstNode? {
    var low = 0
    var high = children.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (children[middle].range.end <= offset) low = middle + 1 else high = middle
    }
    return children.getOrNull(low)?.takeIf { offset in it.range }
}

private data class Json5NodeOrders(
    val byStart: List<Json5AstNode>,
    val byEnd: List<Json5AstNode>,
)

private fun collectNodeOrders(root: Json5Value?): Json5NodeOrders {
    root ?: return Json5NodeOrders(emptyList(), emptyList())
    val byStart = mutableListOf<Json5AstNode>()
    val byEnd = mutableListOf<Json5AstNode>()
    val stack = mutableListOf<Pair<Json5AstNode, Boolean>>(root to false)
    while (stack.isNotEmpty()) {
        val (node, visited) = stack.pop()
        if (visited) {
            byEnd.add(node)
            continue
        }
        byStart.add(node)
        stack.add(node to true)
        when (node) {
            is Json5Object -> for (index in node.properties.indices.reversed()) {
                stack.add(node.properties[index] to false)
            }

            is Json5Array -> for (index in node.elements.indices.reversed()) {
                stack.add(node.elements[index] to false)
            }

            is Json5Property -> stack.add(node.value to false)
            else -> Unit
        }
    }
    return Json5NodeOrders(byStart, byEnd)
}

private fun bindJson5Comments(
    source: String,
    root: Json5Value?,
    tokens: List<Json5Token>,
): List<Json5Comment> {
    val rawComments = tokens.mapIndexedNotNull { tokenIndex, token ->
        if (!token.kind.isComment) {
            null
        } else {
            val kind = if (token.kind == Json5TokenKind.LineComment) {
                Json5CommentKind.Line
            } else {
                Json5CommentKind.Block
            }
            val contentStart = token.range.start + 2
            val hasBlockEnd = kind == Json5CommentKind.Block && token.range.length >= 4 &&
                source[token.range.end - 2] == '*' && source[token.range.end - 1] == '/'
            val contentEnd = if (hasBlockEnd) token.range.end - 2 else token.range.end
            RawJson5Comment(
                token.range,
                kind,
                source.substring(contentStart, contentEnd),
                commentSlot(tokens, tokenIndex),
            )
        }
    }
    if (rawComments.isEmpty()) return emptyList()

    val nodeOrders = collectNodeOrders(root)
    val nodesByStart = nodeOrders.byStart
    val nodesByEnd = nodeOrders.byEnd
    val containers = nodesByStart.filter { it is Json5Object || it is Json5Array }
    val containerStack = mutableListOf<Json5AstNode>()
    var containerIndex = 0

    return rawComments.map { comment ->
        while (containerIndex < containers.size && containers[containerIndex].range.start <= comment.range.start) {
            val container = containers[containerIndex++]
            while (containerStack.isNotEmpty() && containerStack.last().range.end <= container.range.start) {
                containerStack.pop()
            }
            containerStack.add(container)
        }
        while (containerStack.isNotEmpty() && containerStack.last().range.end < comment.range.end) {
            containerStack.pop()
        }

        val enclosing = containerStack.lastOrNull()
        val preceding = findPrecedingNode(nodesByEnd, comment.range.start)
            ?.takeIf { enclosing == null || enclosing.contains(it) }
        val following = findFollowingNode(nodesByStart, comment.range.end)
            ?.takeIf { enclosing == null || enclosing.contains(it) }
        when {
            preceding != null && !sourceContainsLineBreak(source, preceding.range.end, comment.range.start) -> {
                Json5Comment(
                    comment.range,
                    comment.kind,
                    comment.content,
                    Json5CommentPlacement.Trailing,
                    preceding.nodeId,
                    comment.slot,
                )
            }

            following != null -> Json5Comment(
                comment.range,
                comment.kind,
                comment.content,
                Json5CommentPlacement.Leading,
                following.nodeId,
                comment.slot,
            )

            else -> Json5Comment(
                comment.range,
                comment.kind,
                comment.content,
                Json5CommentPlacement.Dangling,
                enclosing?.nodeId,
                comment.slot,
            )
        }
    }
}

private data class RawJson5Comment(
    val range: Json5SourceRange,
    val kind: Json5CommentKind,
    val content: String,
    val slot: Json5CommentSlot,
)

private fun commentSlot(tokens: List<Json5Token>, commentIndex: Int): Json5CommentSlot {
    if (tokens[commentIndex].kind != Json5TokenKind.BlockComment) return Json5CommentSlot.Default
    var previousIndex = commentIndex - 1
    while (previousIndex >= 0 && tokens[previousIndex].kind.isTrivia) previousIndex--
    var nextIndex = commentIndex + 1
    while (nextIndex < tokens.size && tokens[nextIndex].kind.isTrivia) nextIndex++
    val previousKind = tokens.getOrNull(previousIndex)?.kind
    val nextKind = tokens.getOrNull(nextIndex)?.kind
    return when {
        nextKind == Json5TokenKind.Colon &&
            (previousKind == Json5TokenKind.Identifier || previousKind == Json5TokenKind.String) -> {
            Json5CommentSlot.AfterPropertyName
        }

        previousKind == Json5TokenKind.Colon -> Json5CommentSlot.BeforePropertyValue
        else -> Json5CommentSlot.Default
    }
}

private fun findPrecedingNode(nodes: List<Json5AstNode>, offset: Int): Json5AstNode? {
    var low = 0
    var high = nodes.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (nodes[middle].range.end <= offset) low = middle + 1 else high = middle
    }
    return nodes.getOrNull(low - 1)
}

private fun findFollowingNode(nodes: List<Json5AstNode>, offset: Int): Json5AstNode? {
    var low = 0
    var high = nodes.size
    while (low < high) {
        val middle = (low + high) ushr 1
        if (nodes[middle].range.start < offset) low = middle + 1 else high = middle
    }
    return nodes.getOrNull(low)
}

private fun Json5AstNode.contains(node: Json5AstNode): Boolean =
    range.start <= node.range.start && range.end >= node.range.end

private fun sourceContainsLineBreak(source: String, start: Int, end: Int): Boolean {
    for (index in start until end) {
        if (source[index] in newLineChars) return true
    }
    return false
}
