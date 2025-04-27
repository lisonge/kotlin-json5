package li.songe.json5

import kotlin.test.Test
import kotlin.test.assertEquals

class HexTest {
    @Test
    fun hex() {
        assertEquals(hexToDecimal("9"), "9")
        assertEquals(hexToDecimal("FFF"), "4095")
        assertEquals(
            hexToDecimal("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFF".substring(2)),
            "5192296858534827628530496329220095"
        )
    }
}
