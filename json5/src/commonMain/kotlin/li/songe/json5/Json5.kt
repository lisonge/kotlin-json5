package li.songe.json5

import kotlinx.serialization.json.JsonElement

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

    fun parseToJson5ElementAndRanges(input: String): Pair<JsonElement, List<Json5Range>> {
        return Json5Decoder(input).readElementAndRange()
    }

    fun parseToJson5LooseRanges(input: String): List<Json5LooseRange> {
        return Json5LooseDecoder(input).read()
    }
}
