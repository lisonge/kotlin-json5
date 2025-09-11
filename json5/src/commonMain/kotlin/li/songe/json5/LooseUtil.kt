package li.songe.json5

internal fun isBoundChar(c: Char?): Boolean {
    c ?: return true
    return !isIdContinueChar(c)
}

internal fun BaseParser.peekLiteral(v: String): Boolean {
    v.forEachIndexed { index, c ->
        if (input.getOrNull(index + i) != c) {
            return false
        }
    }
    return isBoundChar(input.getOrNull(i + v.length))
}

internal fun BaseParser.readLooseComment() {
    i++
    when (input.getOrNull(i)) {
        '/' -> {
            i++
            if (end) return
            val endIndex = input.indexOfAny(newLineChars, i)
            i = if (endIndex < 0) {
                input.length
            } else {
                endIndex + 1
            }
        }

        '*' -> {
            i++
            val endIndex = input.indexOf("*/", i)
            i = if (endIndex < 0) {
                input.length
            } else {
                endIndex + 2
            }
        }

        else -> i--
    }
}

internal fun BaseParser.readLooseString() {
    val wrapChar = char // it must be ' or "
    i++
    while (true) {
        when (char) {
            null -> break
            wrapChar -> {
                i++
                break
            }

            '\\' -> {
                i++
                if (end) break
                i++
            }

            else -> i++
        }
    }
}

internal fun BaseParser.readLooseNumber(): Json5Token {
    val startChar = char!!
    if (startChar == 'N') {
        if (peekLiteral("NaN")) {
            i += 3
            return Json5Token.NumberLiteral
        }
    } else if (startChar == 'I') {
        if (peekLiteral("Infinity")) {
            i += 8
            return Json5Token.NumberLiteral
        }
    } else {
        i++
        while (!end) {
            val c = input[i]
            if (c !in "-+." && isBoundChar(c)) {
                break
            }
            i++
        }
        return Json5Token.NumberLiteral
    }
    // readLooseProperty
    readLooseProperty()
    return Json5Token.Property
}

internal fun BaseParser.readLooseProperty() {
    i++
    while (!end && isIdContinueChar(input[i])) {
        i++
    }
}
