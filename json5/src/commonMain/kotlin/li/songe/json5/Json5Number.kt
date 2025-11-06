package li.songe.json5

// just for keep raw number format
public class Json5Number(
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