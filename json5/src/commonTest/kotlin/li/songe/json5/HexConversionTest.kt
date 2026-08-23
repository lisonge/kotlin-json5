package li.songe.json5

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class HexConversionTest {
    @Test
    fun convertsHexadecimalToDecimalExactly() {
        assertEquals("0", hexToDecimalString("000"))
        assertEquals("9", hexToDecimalString("9"))
        assertEquals("4095", hexToDecimalString("FFF"))
        assertEquals(
            "5192296858534827628530496329220095",
            hexToDecimalString("FFFFFFFFFFFFFFFFFFFFFFFFFFFF"),
        )
    }

    @Test
    fun matchesReferenceConversionForShortInputs() {
        val random = Random(0)
        val hexDigits = "0123456789abcdefABCDEF"

        repeat(100) {
            val length = random.nextInt(1, 129)
            val hex = buildString(length) {
                repeat(length) {
                    append(hexDigits[random.nextInt(hexDigits.length)])
                }
            }
            assertEquals(referenceHexToDecimalString(hex), hexToDecimalString(hex), hex)
        }
    }

    @Test
    fun convertsLongHexadecimalInputWithinInteractiveBudget() {
        val mark = TimeSource.Monotonic.markNow()
        val decimal = hexToDecimalString("F".repeat(8_000))
        val elapsed = mark.elapsedNow()

        assertEquals(9_633, decimal.length)
        assertTrue(decimal.endsWith('5'))
        assertTrue(elapsed < 500.milliseconds, "conversion took $elapsed")
    }

    private fun referenceHexToDecimalString(hex: String): String {
        val decimalDigits = mutableListOf(0)
        for (char in hex) {
            var carry = when (char) {
                in '0'..'9' -> char - '0'
                in 'a'..'f' -> char - 'a' + 10
                in 'A'..'F' -> char - 'A' + 10
                else -> error("invalid test input: $char")
            }
            for (index in decimalDigits.indices) {
                val value = decimalDigits[index] * 16 + carry
                decimalDigits[index] = value % 10
                carry = value / 10
            }
            while (carry > 0) {
                decimalDigits += carry % 10
                carry /= 10
            }
        }
        return decimalDigits.asReversed().joinToString("")
    }
}
