package li.songe.json5

import kotlin.test.Test
import kotlin.test.assertFails

class Json5Error {
    @Test
    fun t1() {
        assertFails {
            Json5.parseToJson5Element("{")
        }
        assertFails {
            Json5.parseToJson5Element("[")
        }
        assertFails {
            Json5.parseToJson5Element("}")
        }
        assertFails {
            Json5.parseToJson5Element("]")
        }
    }
}