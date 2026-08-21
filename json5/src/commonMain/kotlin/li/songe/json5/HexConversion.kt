package li.songe.json5

private const val DECIMAL_LIMB_BASE = 10_000
private const val HEX_CHUNK_SIZE = 3

/** Converts an arbitrary-length hexadecimal integer to its decimal representation. */
internal fun hexToDecimalString(hex: String): String {
    if (hex.isEmpty()) return "0"

    // Each limb stores four decimal digits in little-endian order. Three hex
    // digits fit comfortably in an Int multiplication with a decimal limb.
    val limbs = IntArray(hex.length / HEX_CHUNK_SIZE + 2)
    var limbCount = 1
    var chunkStart = 0
    var chunkLength = hex.length % HEX_CHUNK_SIZE
    if (chunkLength == 0) {
        chunkLength = HEX_CHUNK_SIZE
    }

    while (chunkStart < hex.length) {
        var chunkValue = 0
        repeat(chunkLength) { offset ->
            chunkValue = chunkValue * 16 + hexDigitValue(hex[chunkStart + offset])
        }

        val multiplier = when (chunkLength) {
            1 -> 16
            2 -> 16 * 16
            else -> 16 * 16 * 16
        }
        var carry = chunkValue
        for (limbIndex in 0 until limbCount) {
            val product = limbs[limbIndex] * multiplier + carry
            limbs[limbIndex] = product % DECIMAL_LIMB_BASE
            carry = product / DECIMAL_LIMB_BASE
        }
        while (carry > 0) {
            limbs[limbCount++] = carry % DECIMAL_LIMB_BASE
            carry /= DECIMAL_LIMB_BASE
        }

        chunkStart += chunkLength
        chunkLength = HEX_CHUNK_SIZE
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
    else -> throw IllegalArgumentException("invalid hex digit: $char")
}
