package li.songe.json5

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object Json5 {
    fun parseToJson5Element(input: String): JsonElement {
        return Json5Decoder(input).read()
    }

    fun encodeToString(
        element: JsonElement,
        config: Json5EncoderConfig = Json5EncoderConfig(),
    ): String {
        return innerEncodeToString(element, config)
    }
}

private fun innerEncodeToString(
    element: JsonElement,
    config: Json5EncoderConfig,
    level: Int = 0,
): String = if (element is JsonPrimitive) {
    if (element.isString) {
        stringifyString(element.content, config.singleQuote)
    } else {
        element.content
    }
} else {
    val indent = config.indent
    val lineSeparator = if (indent.isEmpty()) "" else "\n"
    val keySeparator = if (indent.isEmpty()) ":" else ": "
    val newLevel = level + 1
    val prefixSpaces = if (indent.isEmpty()) "" else indent.repeat(newLevel)
    val closingSpaces = if (indent.isEmpty()) "" else indent.repeat(level)
    val postfix = if (config.trailingComma) "," else ""
    if (element is JsonObject) {
        if (element.isEmpty()) {
            "{}"
        } else {
            element.entries.joinToString(",$lineSeparator", postfix = postfix) { (key, value) ->
                "${prefixSpaces}${stringifyKey(key, config.singleQuote, config.unquotedKey)}${keySeparator}${
                    innerEncodeToString(
                        value,
                        config,
                        newLevel
                    )
                }"
            }.let {
                "{$lineSeparator$it$lineSeparator$closingSpaces}"
            }
        }
    } else if (element is JsonArray) {
        if (element.isEmpty()) {
            "[]"
        } else {
            element.joinToString(",$lineSeparator", postfix = postfix) {
                "${prefixSpaces}${innerEncodeToString(it, config, newLevel)}"
            }.let {
                "[$lineSeparator$it$lineSeparator$closingSpaces]"
            }
        }
    } else {
        throw IllegalArgumentException()
    }
}
