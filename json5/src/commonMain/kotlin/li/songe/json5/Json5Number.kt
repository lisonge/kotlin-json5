package li.songe.json5

/**
 * A parsed JSON5 number in normalized form. Use [Json5Document.text] with the
 * containing [Json5NumberLiteral] when the original source spelling is needed.
 */
public class Json5Number internal constructor(
    public val value: String,
) : Number() {
    override fun toString(): String {
        return value
    }

    override fun toByte(): Byte {
        return value.toDouble().toInt().toByte()
    }

    override fun toDouble(): Double {
        return value.toDouble()
    }

    override fun toFloat(): Float {
        return value.toDouble().toFloat()
    }

    override fun toInt(): Int {
        return value.toDouble().toInt()
    }

    override fun toLong(): Long {
        return value.toDouble().toLong()
    }

    override fun toShort(): Short {
        return value.toDouble().toInt().toShort()
    }
}
