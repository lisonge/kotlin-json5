package li.songe.json5

public enum class Json5SyntaxKind {
    Whitespace,
    Comment,
    LeftBrace,
    RightBrace,
    LeftBracket,
    RightBracket,
    Comma,
    Colon,
    PropertyName,
    NullLiteral,
    BooleanLiteral,
    NumberLiteral,
    StringLiteral,
    Identifier,
    Invalid,
}

public fun interface Json5SyntaxConsumer {
    /** Receives a syntax span from [start] inclusive to [end] exclusive. */
    public fun accept(kind: Json5SyntaxKind, start: Int, end: Int)
}

/**
 * A compact, immutable sequence of syntax kinds and source ranges.
 * Each start offset is inclusive and its corresponding end offset is exclusive.
 */
public class Json5SyntaxTokens internal constructor(
    private val data: IntArray,
) {
    init {
        require(data.size % TOKEN_WIDTH == 0)
    }

    public val size: Int
        get() = data.size / TOKEN_WIDTH

    public val indices: IntRange
        get() = 0 until size

    public fun isEmpty(): Boolean = data.isEmpty()

    public fun isNotEmpty(): Boolean = data.isNotEmpty()

    public fun kindAt(index: Int): Json5SyntaxKind =
        Json5SyntaxKind.entries[data[offset(index)]]

    public fun startAt(index: Int): Int = data[offset(index) + START_OFFSET]

    public fun endAt(index: Int): Int = data[offset(index) + END_OFFSET]

    override fun equals(other: Any?): Boolean =
        this === other || (other is Json5SyntaxTokens && data.contentEquals(other.data))

    override fun hashCode(): Int = data.contentHashCode()

    override fun toString(): String = "Json5SyntaxTokens(size=$size)"

    private fun offset(index: Int): Int {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("index: $index, size: $size")
        }
        return index * TOKEN_WIDTH
    }

    private companion object {
        const val TOKEN_WIDTH = 3
        const val START_OFFSET = 1
        const val END_OFFSET = 2
    }
}
