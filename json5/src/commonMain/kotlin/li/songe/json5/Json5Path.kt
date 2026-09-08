package li.songe.json5

public sealed interface Json5PathSegment {
    /**
     * Selects a property by [name]. A `null` [occurrence] selects the last
     * duplicate; otherwise it is a zero-based occurrence from source order.
     */
    public data class Property(
        public val name: String,
        public val occurrence: Int? = null,
    ) : Json5PathSegment {
        init {
            require(occurrence == null || occurrence >= 0) { "occurrence must not be negative" }
        }
    }

    public data class Index(
        public val index: Int,
    ) : Json5PathSegment {
        init {
            require(index >= 0) { "index must not be negative" }
        }
    }
}

/** An immutable path from the document root to an object property or array element. */
public class Json5Path(
    segments: List<Json5PathSegment>,
) {
    public val segments: List<Json5PathSegment> = segments.toList()

    public operator fun get(name: String, occurrence: Int? = null): Json5Path =
        Json5Path(segments + Json5PathSegment.Property(name, occurrence))

    public operator fun get(index: Int): Json5Path =
        Json5Path(segments + Json5PathSegment.Index(index))

    public companion object {
        public val Root: Json5Path = Json5Path(emptyList())

        public operator fun get(name: String, occurrence: Int? = null): Json5Path =
            Root[name, occurrence]

        public operator fun get(index: Int): Json5Path = Root[index]
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Json5Path && segments == other.segments)

    override fun hashCode(): Int = segments.hashCode()

    override fun toString(): String = "Json5Path(segments=$segments)"
}

internal sealed interface Json5ResolvedPath {
    val value: Json5Value
    val target: Json5AstNode
}

internal data class Json5ResolvedRoot(
    override val value: Json5Value,
) : Json5ResolvedPath {
    override val target: Json5AstNode = value
}

internal data class Json5ResolvedProperty(
    override val value: Json5Value,
    val parent: Json5Object,
    val property: Json5Property,
    val childIndex: Int,
) : Json5ResolvedPath {
    override val target: Json5AstNode = property
}

internal data class Json5ResolvedElement(
    override val value: Json5Value,
    val parent: Json5Array,
    val childIndex: Int,
) : Json5ResolvedPath {
    override val target: Json5AstNode = value
}

internal fun Json5Document.resolve(path: Json5Path): Json5ResolvedPath? {
    var current: Json5Value = root ?: return null
    var resolved: Json5ResolvedPath = Json5ResolvedRoot(current)

    for (segment in path.segments) {
        resolved = when (segment) {
            is Json5PathSegment.Property -> {
                val objectValue = current as? Json5Object ?: return null
                val selectedIndex = objectValue.propertyIndex(segment) ?: return null
                val selected = objectValue.properties[selectedIndex]
                current = selected.value
                Json5ResolvedProperty(current, objectValue, selected, selectedIndex)
            }

            is Json5PathSegment.Index -> {
                val arrayValue = current as? Json5Array ?: return null
                val selected = arrayValue.elements.getOrNull(segment.index) ?: return null
                current = selected
                Json5ResolvedElement(current, arrayValue, segment.index)
            }
        }
    }
    return resolved
}

public operator fun Json5Document.get(path: Json5Path): Json5Value? = resolve(path)?.value

public fun Json5Document.findProperty(path: Json5Path): Json5Property? =
    (resolve(path) as? Json5ResolvedProperty)?.property

private fun Json5Object.propertyIndex(segment: Json5PathSegment.Property): Int? {
    if (segment.occurrence == null) {
        for (index in properties.indices.reversed()) {
            if (properties[index].name.value == segment.name) return index
        }
        return null
    }

    var occurrence = 0
    for (index in properties.indices) {
        if (properties[index].name.value != segment.name) continue
        if (occurrence == segment.occurrence) return index
        occurrence++
    }
    return null
}
