package li.songe.json5

import kotlinx.serialization.json.JsonElement

public data class Json5EditConfig(
    public val encoderConfig: Json5EncoderConfig = Json5.encoderConfig,
    public val formatConfig: Json5FormatConfig = Json5.formatConfig,
)

/**
 * The result of one document edit. [edits] are relative to the source document,
 * and [document] is parsed from [text] after applying them.
 */
public data class Json5EditResult(
    public val text: String,
    public val edits: List<Json5TextEdit>,
    public val document: Json5Document,
)

/** Replaces only the source range of the value selected by [path]. */
public fun Json5Document.set(
    path: Json5Path,
    value: JsonElement,
    config: Json5EditConfig = Json5EditConfig(),
): Json5EditResult {
    val target = requireResolved(path)
    return applyEdit(
        Json5TextEdit(
            offset = target.value.range.start,
            length = target.value.range.length,
            content = Json5.encodeToString(value, config.encoderConfig),
        ),
    )
}

/**
 * Removes the property or array element selected by [path], including attached comments.
 * The document root cannot be removed.
 */
public fun Json5Document.remove(path: Json5Path): Json5EditResult {
    val target = requireResolved(path)
    val range = when (target) {
        is Json5ResolvedRoot -> throw IllegalArgumentException("the document root cannot be removed")
        is Json5ResolvedProperty -> removalRange(
            target.property,
            target.parent.properties,
            target.childIndex,
        )

        is Json5ResolvedElement -> removalRange(
            target.value,
            target.parent.elements,
            target.childIndex,
        )
    }
    return applyEdit(Json5TextEdit(range.start, range.length, ""))
}

/** Renames the object property selected by [path], preserving its value and comments. */
public fun Json5Document.renameProperty(
    path: Json5Path,
    newName: String,
    config: Json5EditConfig = Json5EditConfig(),
): Json5EditResult {
    val property = (requireResolved(path) as? Json5ResolvedProperty)?.property
        ?: throw IllegalArgumentException("path does not select an object property")
    return applyEdit(
        Json5TextEdit(
            offset = property.name.range.start,
            length = property.name.range.length,
            content = stringifyKey(newName, config.encoderConfig),
        ),
    )
}

/**
 * Appends a property to the object selected by [objectPath]. Existing properties
 * with the same [name] are retained, preserving JSON5 duplicate-property syntax.
 */
public fun Json5Document.putProperty(
    objectPath: Json5Path,
    name: String,
    value: JsonElement,
    config: Json5EditConfig = Json5EditConfig(),
): Json5EditResult {
    val objectValue = requireResolved(objectPath).value as? Json5Object
        ?: throw IllegalArgumentException("path does not select an object")
    val separator = if (isMultiline(objectValue.range)) ": " else ":"
    val content = stringifyKey(name, config.encoderConfig) + separator +
        Json5.encodeToString(value, config.encoderConfig)
    return appendToContainer(
        container = objectValue,
        isEmpty = objectValue.properties.isEmpty(),
        content = content,
        config = config,
    )
}

/** Inserts [value] before the element currently at [index], or appends it when [index] equals the array size. */
public fun Json5Document.insertArrayElement(
    arrayPath: Json5Path,
    index: Int,
    value: JsonElement,
    config: Json5EditConfig = Json5EditConfig(),
): Json5EditResult {
    val arrayValue = requireResolved(arrayPath).value as? Json5Array
        ?: throw IllegalArgumentException("path does not select an array")
    return insertArrayElement(arrayValue, index, value, config)
}

private fun Json5Document.insertArrayElement(
    arrayValue: Json5Array,
    index: Int,
    value: JsonElement,
    config: Json5EditConfig,
): Json5EditResult {
    require(index in 0..arrayValue.elements.size) { "array insertion index is out of bounds" }
    val content = Json5.encodeToString(value, config.encoderConfig)
    if (index == arrayValue.elements.size) {
        return appendToContainer(
            container = arrayValue,
            isEmpty = arrayValue.elements.isEmpty(),
            content = content,
            config = config,
        )
    }

    val next = arrayValue.elements[index]
    val insertionOffset = leadingStart(next)
    val multiline = isMultiline(arrayValue.range)
    val suffix = if (multiline) {
        "," + editLineSeparator(config.formatConfig) + lineIndentAt(insertionOffset)
    } else {
        ","
    }
    return applyEdit(Json5TextEdit(insertionOffset, 0, content + suffix))
}

public fun Json5Document.appendArrayElement(
    arrayPath: Json5Path,
    value: JsonElement,
    config: Json5EditConfig = Json5EditConfig(),
): Json5EditResult {
    val arrayValue = requireResolved(arrayPath).value as? Json5Array
        ?: throw IllegalArgumentException("path does not select an array")
    return insertArrayElement(arrayValue, arrayValue.elements.size, value, config)
}

/**
 * Adds delimiter-free [content] before the node selected by [path]. Line-comment
 * content must not contain a line break, and block-comment content must not contain its closing delimiter.
 */
public fun Json5Document.addCommentBefore(
    path: Json5Path,
    content: String,
    kind: Json5CommentKind = Json5CommentKind.Line,
    config: Json5EditConfig = Json5EditConfig(),
): Json5EditResult {
    validateCommentContent(content, kind)
    val resolved = requireResolved(path)
    val target = resolved.target
    val rendered = renderComment(content, kind)
    val insertion = rendered + editLineSeparator(config.formatConfig) + lineIndentAt(target.range.start)
    return applyEdit(Json5TextEdit(target.range.start, 0, insertion))
}

/**
 * Adds delimiter-free [content] after the node selected by [path]. Line-comment
 * content must not contain a line break, and block-comment content must not contain its closing delimiter.
 */
public fun Json5Document.addCommentAfter(
    path: Json5Path,
    content: String,
    kind: Json5CommentKind = Json5CommentKind.Line,
    config: Json5EditConfig = Json5EditConfig(),
): Json5EditResult {
    validateCommentContent(content, kind)
    val resolved = requireResolved(path)
    val target = resolved.target
    val parentRange = when (resolved) {
        is Json5ResolvedRoot -> null
        is Json5ResolvedProperty -> resolved.parent.range
        is Json5ResolvedElement -> resolved.parent.range
    }
    val comma = tokens.firstOrNull { token ->
        token.kind == Json5TokenKind.Comma && token.range.start >= target.range.end &&
            (parentRange == null || token.range.end < parentRange.end)
    }
    val offset = comma?.range?.end ?: target.range.end
    var insertion = " " + renderComment(content, kind)
    if (kind == Json5CommentKind.Line && !containsLineBreak(offset, followingSyntaxOffset(offset))) {
        val indentOffset = if (comma == null) {
            parentRange?.end?.minus(1) ?: source.length
        } else {
            followingSyntaxOffset(offset)
        }
        insertion += editLineSeparator(config.formatConfig) + lineIndentAt(indentOffset)
    }
    return applyEdit(Json5TextEdit(offset, 0, insertion))
}

/** Removes [comment] and immediately preceding horizontal indentation; the comment must belong to this document. */
public fun Json5Document.removeComment(comment: Json5Comment): Json5EditResult {
    requireValid()
    require(comments.any { it.range == comment.range }) { "comment does not belong to this document" }
    var start = comment.range.start
    while (start > 0 && (source[start - 1] == ' ' || source[start - 1] == '\t')) start--
    return applyEdit(Json5TextEdit(start, comment.range.end - start, ""))
}

private fun Json5Document.requireResolved(path: Json5Path): Json5ResolvedPath {
    requireValid()
    return resolve(path) ?: throw IllegalArgumentException("path does not exist: $path")
}

private fun Json5Document.applyEdit(edit: Json5TextEdit): Json5EditResult {
    return applyEdits(listOf(edit))
}

private fun Json5Document.applyEdits(edits: List<Json5TextEdit>): Json5EditResult {
    val text = source.applyJson5Edits(edits)
    return Json5EditResult(text, edits, Json5.parseToDocument(text))
}

private fun Json5Document.appendToContainer(
    container: Json5Value,
    isEmpty: Boolean,
    content: String,
    config: Json5EditConfig,
): Json5EditResult {
    val closingOffset = container.range.end - 1
    val danglingStart = commentsOf(container, Json5CommentPlacement.Dangling).asSequence()
        .minOfOrNull { it.range.start }
    val boundary = danglingStart ?: closingOffset
    var insertionOffset = boundary
    while (insertionOffset > container.range.start + 1 && source[insertionOffset - 1] in whiteSpaceChars) {
        insertionOffset--
    }

    val multiline = isMultiline(container.range) || danglingStart != null
    val trailingComma = lastStructuralTokenBefore(boundary)?.kind == Json5TokenKind.Comma
    val lastChild: Json5AstNode? = when (container) {
        is Json5Object -> container.properties.lastOrNull()
        is Json5Array -> container.elements.lastOrNull()
        else -> null
    }
    val separateCommaOffset = if (!isEmpty && !trailingComma) {
        lastChild?.takeIf { child ->
            commentsOf(child, Json5CommentPlacement.Trailing).any { it.kind == Json5CommentKind.Line }
        }?.range?.end
    } else {
        null
    }
    val separateComma = separateCommaOffset != null
    val lineSeparator = editLineSeparator(config.formatConfig)
    val closeIndent = lineIndentAt(closingOffset)
    val childIndent = closeIndent + config.formatConfig.indent
    val insertion = if (isEmpty) {
        if (multiline) lineSeparator + childIndent + content else content
    } else if (multiline) {
        buildString {
            if (!trailingComma && !separateComma) append(',')
            append(lineSeparator).append(childIndent).append(content)
            if (trailingComma) append(',')
        }
    } else {
        buildString {
            if (!trailingComma) append(',')
            append(content)
            if (trailingComma) append(',')
        }
    }
    val insertionEdit = Json5TextEdit(insertionOffset, 0, insertion)
    if (separateComma) {
        return applyEdits(
            listOf(
                Json5TextEdit(separateCommaOffset, 0, ","),
                insertionEdit,
            ),
        )
    }
    return applyEdit(insertionEdit)
}

private fun Json5Document.removalRange(
    target: Json5AstNode,
    siblings: List<Json5AstNode>,
    index: Int,
): Json5SourceRange {
    val startWithComments = leadingStart(target)
    val endWithComments = trailingEnd(target)
    if (siblings.size == 1) return Json5SourceRange(startWithComments, endWithComments)
    if (index < siblings.lastIndex) {
        return Json5SourceRange(startWithComments, leadingStart(siblings[index + 1]))
    }
    val previous = siblings[index - 1]
    val comma = tokens.lastOrNull { token ->
        token.kind == Json5TokenKind.Comma &&
            token.range.start >= previous.range.end && token.range.end <= startWithComments
    } ?: error("missing comma before removable item")
    return Json5SourceRange(comma.range.start, endWithComments)
}

private fun Json5Document.leadingStart(node: Json5AstNode): Int =
    commentsOf(node, Json5CommentPlacement.Leading).asSequence()
    .minOfOrNull { it.range.start } ?: node.range.start

private fun Json5Document.trailingEnd(node: Json5AstNode): Int =
    commentsOf(node, Json5CommentPlacement.Trailing).asSequence()
    .maxOfOrNull { it.range.end }?.let { maxOf(it, node.range.end) } ?: node.range.end

private fun Json5Document.lastStructuralTokenBefore(offset: Int): Json5Token? = tokens.lastOrNull {
    it.range.end <= offset && !it.kind.isTrivia
}

private fun Json5Document.isMultiline(range: Json5SourceRange): Boolean =
    containsLineBreak(range.start, range.end)

private fun Json5Document.containsLineBreak(start: Int, end: Int): Boolean {
    for (index in start until end) {
        if (source[index] in newLineChars) return true
    }
    return false
}

private fun Json5Document.followingSyntaxOffset(offset: Int): Int = tokens.firstOrNull {
    it.range.start >= offset && !it.kind.isTrivia
}?.range?.start ?: source.length

private fun Json5Document.lineIndentAt(offset: Int): String {
    var lineStart = offset
    while (lineStart > 0 && source[lineStart - 1] !in newLineChars) lineStart--
    var end = lineStart
    while (end < source.length && end < offset && (source[end] == ' ' || source[end] == '\t')) end++
    return source.substring(lineStart, end)
}

private fun Json5Document.editLineSeparator(config: Json5FormatConfig): String = when (config.lineSeparator) {
    Json5LineSeparator.Preserve -> detectLineSeparator(source)
    Json5LineSeparator.Lf -> "\n"
    Json5LineSeparator.CrLf -> "\r\n"
}

private fun validateCommentContent(content: String, kind: Json5CommentKind) {
    when (kind) {
        Json5CommentKind.Line -> require(content.none { it in newLineChars }) {
            "line comment content must not contain a line break"
        }

        Json5CommentKind.Block -> require(content.indexOf("*/") < 0) {
            "block comment content must not contain '*/'"
        }
    }
}

private fun renderComment(content: String, kind: Json5CommentKind): String = when (kind) {
    Json5CommentKind.Line -> "// $content"
    Json5CommentKind.Block -> "/* $content */"
}
