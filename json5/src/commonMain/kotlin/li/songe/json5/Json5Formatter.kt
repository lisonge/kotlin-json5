package li.songe.json5

private sealed interface Json5FormatToken {
    val kind: Json5TokenKind

    data class Source(
        override val kind: Json5TokenKind,
        val range: Json5SourceRange,
    ) : Json5FormatToken

    data class Comment(
        val value: Json5Comment,
    ) : Json5FormatToken {
        override val kind: Json5TokenKind = when (value.kind) {
            Json5CommentKind.Line -> Json5TokenKind.LineComment
            Json5CommentKind.Block -> Json5TokenKind.BlockComment
        }
    }

    data object SyntheticComma : Json5FormatToken {
        override val kind: Json5TokenKind = Json5TokenKind.Comma
    }
}

internal fun formatJson5Document(
    document: Json5Document,
    config: Json5FormatConfig,
): String {
    val lineSeparator = selectLineSeparator(document.source, config.lineSeparator)
    val originalFinalNewline = endsWithLineBreak(document.source)
    val rawTokens = buildList<Pair<Int, Json5FormatToken>> {
        for (token in document.tokens) {
            if (token.kind.isTrivia) continue
            add(token.range.start to Json5FormatToken.Source(token.kind, token.range))
        }
        for (comment in document.comments) {
            add(comment.range.start to Json5FormatToken.Comment(comment))
        }
    }.sortedBy { it.first }.mapTo(mutableListOf()) { it.second }
    applyTrailingCommaPolicy(rawTokens, config.trailingComma)

    val builder = StringBuilder(document.source.length)
    val indentCache = mutableListOf("")
    var depth = 0
    var previous: Json5FormatToken? = null

    fun indent(level: Int): String {
        while (indentCache.size <= level) {
            indentCache.add(indentCache.last() + config.indent)
        }
        return indentCache[level]
    }

    for (current in rawTokens) {
        if (current.kind.isClosingToken()) depth--
        val prior = previous
        if (prior != null) {
            builder.append(
                whitespaceBefore(
                    previous = prior,
                    current = current,
                    depth = depth,
                    indent = ::indent,
                    lineSeparator = lineSeparator,
                ),
            )
        }
        when (current) {
            is Json5FormatToken.Source -> builder.append(
                document.source,
                current.range.start,
                current.range.end,
            )

            is Json5FormatToken.Comment -> builder.append(
                document.source,
                current.value.range.start,
                current.value.range.end,
            )

            Json5FormatToken.SyntheticComma -> builder.append(',')
        }
        if (current.kind.isOpeningToken()) depth++
        previous = current
    }

    var result = builder.toString()
    val wantsFinalNewline = when (config.finalNewline) {
        Json5FinalNewline.Preserve -> originalFinalNewline
        Json5FinalNewline.Always -> true
        Json5FinalNewline.Never -> false
    }
    result = stripFinalLineBreaks(result)
    if (wantsFinalNewline) result += lineSeparator
    return result
}

private fun applyTrailingCommaPolicy(
    tokens: MutableList<Json5FormatToken>,
    policy: Json5TrailingComma,
) {
    if (policy == Json5TrailingComma.Preserve) return
    var index = 0
    while (index < tokens.size) {
        if (!tokens[index].kind.isClosingToken()) {
            index++
            continue
        }
        var previousIndex = index - 1
        while (previousIndex >= 0 && tokens[previousIndex].kind.isComment) previousIndex--
        if (previousIndex < 0) {
            index++
            continue
        }
        val previousKind = tokens[previousIndex].kind
        when (policy) {
            Json5TrailingComma.Always -> {
                if (!previousKind.isOpeningToken() && previousKind != Json5TokenKind.Comma) {
                    tokens.add(previousIndex + 1, Json5FormatToken.SyntheticComma)
                    index++
                }
            }

            Json5TrailingComma.Never -> {
                if (previousKind == Json5TokenKind.Comma) {
                    tokens.removeAt(previousIndex)
                    index--
                }
            }

            Json5TrailingComma.Preserve -> Unit
        }
        index++
    }
}

private fun whitespaceBefore(
    previous: Json5FormatToken,
    current: Json5FormatToken,
    depth: Int,
    indent: (Int) -> String,
    lineSeparator: String,
): String {
    if (current is Json5FormatToken.Comment) {
        val comment = current.value
        if (
            comment.slot == Json5CommentSlot.AfterPropertyName ||
            comment.slot == Json5CommentSlot.BeforePropertyValue
        ) {
            return " "
        }
        return when (comment.placement) {
            Json5CommentPlacement.Trailing -> " "
            Json5CommentPlacement.Leading,
            Json5CommentPlacement.Dangling,
            -> lineSeparator + indent(depth)
        }
    }

    if (previous is Json5FormatToken.Comment) {
        val comment = previous.value
        if (
            comment.slot == Json5CommentSlot.AfterPropertyName ||
            comment.slot == Json5CommentSlot.BeforePropertyValue
        ) {
            return " "
        }
        if (comment.kind == Json5CommentKind.Line) {
            return lineSeparator + indent(depth)
        }
        return when {
            current.kind == Json5TokenKind.Comma -> ""
            current.kind.isClosingToken() -> lineSeparator + indent(depth)
            comment.placement == Json5CommentPlacement.Trailing -> " "
            else -> lineSeparator + indent(depth)
        }
    }

    if (current.kind == Json5TokenKind.Colon || current.kind == Json5TokenKind.Comma) return ""
    if (previous.kind == Json5TokenKind.Colon) return " "
    if (current.kind.isClosingToken()) {
        return if (previous.kind.matchesOpening(current.kind)) "" else lineSeparator + indent(depth)
    }
    if (previous.kind.isOpeningToken() || previous.kind == Json5TokenKind.Comma) {
        return lineSeparator + indent(depth)
    }
    return " "
}

private fun Json5TokenKind.isOpeningToken(): Boolean =
    this == Json5TokenKind.LeftBrace || this == Json5TokenKind.LeftBracket

private fun Json5TokenKind.isClosingToken(): Boolean =
    this == Json5TokenKind.RightBrace || this == Json5TokenKind.RightBracket

private fun Json5TokenKind.matchesOpening(closing: Json5TokenKind): Boolean =
    (this == Json5TokenKind.LeftBrace && closing == Json5TokenKind.RightBrace) ||
        (this == Json5TokenKind.LeftBracket && closing == Json5TokenKind.RightBracket)

private fun selectLineSeparator(source: String, option: Json5LineSeparator): String = when (option) {
    Json5LineSeparator.Lf -> "\n"
    Json5LineSeparator.CrLf -> "\r\n"
    Json5LineSeparator.Preserve -> detectLineSeparator(source)
}

internal fun detectLineSeparator(source: String): String {
    for (index in source.indices) {
        when (source[index]) {
            '\r' -> return if (source.getOrNull(index + 1) == '\n') "\r\n" else "\r"
            '\n' -> return "\n"
            '\u2028' -> return "\u2028"
            '\u2029' -> return "\u2029"
        }
    }
    return "\n"
}

private fun endsWithLineBreak(source: String): Boolean =
    source.lastOrNull() != null && source.last() in newLineChars

private fun stripFinalLineBreaks(source: String): String {
    var end = source.length
    while (end > 0 && source[end - 1] in newLineChars) end--
    return source.substring(0, end)
}
