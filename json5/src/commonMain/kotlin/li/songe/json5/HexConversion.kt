package li.songe.json5

private const val DECIMAL_LIMB_BASE = 10_000
private const val HEX_CHUNK_SIZE = 3

/** Converts an arbitrary-length hexadecimal integer to its decimal representation. */
internal fun hexToDecimalString(hex: String): String {
    // Each limb stores four decimal digits in little-endian order. Three hex
    // digits fit comfortably in an Int multiplication with a decimal limb.
    val limbs = IntArray(hex.length / HEX_CHUNK_SIZE + 2)
    var limbCount = 1
    val firstChunkLength = (hex.length - 1) % HEX_CHUNK_SIZE + 1
    var firstChunkValue = hexDigitValue(hex[0])
    for (offset in 1 until firstChunkLength) {
        firstChunkValue = firstChunkValue * 16 + hexDigitValue(hex[offset])
    }
    limbs[0] = firstChunkValue
    var chunkStart = firstChunkLength

    while (chunkStart < hex.length) {
        var chunkValue = 0
        repeat(HEX_CHUNK_SIZE) { offset ->
            chunkValue = chunkValue * 16 + hexDigitValue(hex[chunkStart + offset])
        }

        var carry = chunkValue
        for (limbIndex in 0 until limbCount) {
            val product = limbs[limbIndex] * 4_096 + carry
            limbs[limbIndex] = product % DECIMAL_LIMB_BASE
            carry = product / DECIMAL_LIMB_BASE
        }
        while (carry > 0) {
            limbs[limbCount++] = carry % DECIMAL_LIMB_BASE
            carry /= DECIMAL_LIMB_BASE
        }

        chunkStart += HEX_CHUNK_SIZE
    }

    return buildString(limbCount * 4) {
        append(limbs[limbCount - 1])
        for (limbIndex in limbCount - 2 downTo 0) {
            val limb = limbs[limbIndex]
            when {
                limb < 10 -> append("000")
                limb < 100 -> append("00")
                limb < 1_000 -> append('0')
            }
            append(limb)
        }
    }
}

private fun hexDigitValue(char: Char): Int = when (char) {
    in '0'..'9' -> char - '0'
    in 'a'..'f' -> char - 'a' + 10
    in 'A'..'F' -> char - 'A' + 10
    else -> error("scanner produced invalid hex digit: $char")
}
