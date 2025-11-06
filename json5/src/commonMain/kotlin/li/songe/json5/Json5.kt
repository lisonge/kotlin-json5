package li.songe.json5

import kotlinx.serialization.json.JsonElement

public object Json5 {
    public fun parseToJson5Element(input: CharSequence): JsonElement {
        return Json5Decoder(input).read()
    }

    public fun encodeToString(
        element: JsonElement,
        config: Json5EncoderConfig = encoderConfig,
    ): String {
        return innerEncodeToString(element, config)
    }

    public fun parseToJson5ElementAndRanges(input: CharSequence): Pair<JsonElement, List<Json5Range>> {
        return Json5Decoder(input).readElementAndRange()
    }

    public fun parseToJson5LooseRanges(input: CharSequence): List<Json5LooseRange> {
        return Json5LooseDecoder(input).read()
    }

    public val encoderConfig: Json5EncoderConfig = Json5EncoderConfig()
}
