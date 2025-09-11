package li.songe.json5

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun stringifyKey(key: String, config: Json5EncoderConfig): String {
    if (key.isEmpty() || !config.unquotedKey) {
        return stringifyString(key, config.singleQuote)
    }
    if (!isIdStartChar(key[0])) {
        return stringifyString(key, config.singleQuote)
    }
    for (c in key) {
        if (!isIdContinueChar(c)) {
            return stringifyString(key, config.singleQuote)
        }
    }
    return key
}

private val escapeReplacements = hashMapOf(
    '\\' to "\\\\",
    '\b' to "\\b",
    '\u000C' to "\\f",
    '\n' to "\\n",
    '\r' to "\\r",
    '\t' to "\\t",
    '\u000B' to "\\v",
    '\u0000' to "\\0",
    '\u2028' to "\\u2028",
    '\u2029' to "\\u2029",
)

// https://github.com/json5/json5/blob/b935d4a280eafa8835e6182551b63809e61243b0/lib/stringify.js#L104
internal fun stringifyString(value: String, singleQuote: Boolean): String {
    val wrapChar = if (singleQuote) '\'' else '"'
    val sb = StringBuilder()
    sb.append(wrapChar)
    value.forEachIndexed { i, c ->
        when {
            c == wrapChar -> {
                sb.append("\\$wrapChar")
            }

            c == '\u0000' -> {
                if (isDigit(value.getOrNull(i + 1))) {
                    // "\u00002" -> \x002, avoid octal ambiguity
                    sb.append("\\x00")
                } else {
                    sb.append("\\0")
                }
            }

            c in escapeReplacements -> {
                sb.append(escapeReplacements[c])
            }

            c.code in 0..0x1f -> {
                sb.append("\\x" + c.code.toString(16).padStart(2, '0'))
            }

            else -> {
                sb.append(c)
            }
        }
    }
    sb.append(wrapChar)
    return sb.toString()
}

internal fun stringifyPrimitive(value: JsonPrimitive, config: Json5EncoderConfig): String = when {
    value.isString -> stringifyString(value.content, config.singleQuote)
    else -> value.content
}

internal fun JsonObject.getByIndex(index: Int): Map.Entry<String, JsonElement> {
    var i = 0
    forEach {
        if (i == index) return it
        i++
    }
    throw IndexOutOfBoundsException("Index: $index, Size: $size")
}

private fun getIndent(config: Json5EncoderConfig, ind: Int): String {
    return config.indent.repeat(ind)
}

internal fun innerEncodeToString(element: JsonElement, config: Json5EncoderConfig): String {
    val sb = StringBuilder()
    val elementStack = mutableListOf(element)
    val indexStack = mutableListOf(0)
    val indentStack = mutableListOf(0)

    val hasIndent = config.indent.isNotEmpty()
    val keySeparator = if (hasIndent) ": " else ":"
    fun newLine() {
        if (hasIndent) {
            sb.append("\n")
        }
    }

    while (elementStack.isNotEmpty()) {
        val value = elementStack.pop()
        val idx = indexStack.pop()
        val ind = indentStack.pop()

        when (value) {
            is JsonPrimitive -> sb.append(stringifyPrimitive(value, config))
            is JsonArray -> {
                if (idx == 0) {
                    sb.append("[")
                    if (value.isNotEmpty()) {
                        newLine()
                    }
                }

                if (idx < value.size) {
                    if (idx > 0) {
                        sb.append(",")
                        newLine()
                    }
                    if (hasIndent) sb.append(getIndent(config, ind + 1))

                    elementStack.add(value)
                    indexStack.add(idx + 1)
                    indentStack.add(ind)

                    elementStack.add(value[idx])
                    indexStack.add(0)
                    indentStack.add(ind + 1)
                } else {
                    if (value.isNotEmpty()) {
                        if (config.trailingComma) sb.append(",")
                        if (hasIndent) sb.append("\n").append(getIndent(config, ind))
                    }
                    sb.append("]")
                }
            }

            is JsonObject -> {
                if (idx == 0) {
                    sb.append("{")
                    if (value.isNotEmpty()) {
                        newLine()
                    }
                }
                if (idx < value.size) {
                    if (idx > 0) {
                        sb.append(",")
                        newLine()
                    }
                    val (k, v) = value.getByIndex(idx)
                    if (hasIndent) sb.append(getIndent(config, ind + 1))
                    sb.append(stringifyKey(k, config)).append(keySeparator)

                    elementStack.add(value)
                    indexStack.add(idx + 1)
                    indentStack.add(ind)

                    elementStack.add(v)
                    indexStack.add(0)
                    indentStack.add(ind + 1)
                } else {
                    if (value.isNotEmpty()) {
                        if (config.trailingComma) sb.append(",")
                        if (hasIndent) sb.append("\n").append(getIndent(config, ind))
                    }
                    sb.append("}")
                }
            }
        }
    }

    return sb.toString()
}

