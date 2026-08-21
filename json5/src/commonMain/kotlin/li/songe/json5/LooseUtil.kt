package li.songe.json5

internal fun BaseParser.readLooseComment(): Boolean {
    i++
    when (input.getOrNull(i)) {
        '/' -> {
            i++
            if (end) return true
            val endIndex = input.indexOfAny(newLineChars, i)
            i = if (endIndex < 0) {
                input.length
            } else {
                if (input[endIndex] == '\r' && input.getOrNull(endIndex + 1) == '\n') {
                    endIndex + 2
                } else {
                    endIndex + 1
                }
            }
            return true
        }

        '*' -> {
            i++
            val endIndex = input.indexOf("*/", i)
            if (endIndex < 0) {
                i = input.length
                return false
            } else {
                i = endIndex + 2
                return true
            }
        }

        else -> error("readLooseComment requires a comment opener")
    }
}

internal fun BaseParser.readLooseString(): Boolean {
    val wrapChar = char // it must be ' or "
    i++
    while (true) {
        val current = char ?: return false
        when (current) {
            wrapChar -> {
                i++
                return true
            }

            '\\' -> {
                i++
                when (char) {
                    null -> return false
                    '\r' -> {
                        i++
                        if (char == '\n') {
                            i++
                        }
                    }

                    else -> i++
                }
            }

            '\n', '\r' -> return false
            else -> i++
        }
    }
}

internal fun BaseParser.readLooseNumber(): Boolean {
    val start = i
    if (char == '+' || char == '-') {
        i++
    }

    if (char == 'N' || char == 'I') {
        readLooseIdentifier()
        val value = input.subSequence(start, i).toString()
        return value == "NaN" || value == "+NaN" || value == "-NaN" ||
            value == "Infinity" || value == "+Infinity" || value == "-Infinity"
    }

    val unsignedStart = i
    if (char == '0' && (input.getOrNull(i + 1) == 'x' || input.getOrNull(i + 1) == 'X')) {
        i += 2
        val digitsStart = i
        while (isHexDigit(char)) {
            i++
        }
        return i > digitsStart
    }

    var hasDigit = false
    while (true) {
        when (char ?: break) {
            in '0'..'9' -> {
                hasDigit = true
                i++
            }

            '+', '-', '.', 'e', 'E' -> i++
            else -> break
        }
    }
    val lastChar = input.getOrNull(i - 1)
    return i > unsignedStart && hasDigit && lastChar != '+' && lastChar != '-' && lastChar != 'e' && lastChar != 'E'
}

internal fun BaseParser.readLooseIdentifier(): Boolean {
    var isFirst = true
    var isComplete = true
    while (!end) {
        val c = char!!
        if (c == '\\') {
            i++
            if (char != 'u') {
                isComplete = false
                isFirst = false
                continue
            }
            i++
            var hexLength = 0
            while (hexLength < 4 && isHexDigit(char)) {
                i++
                hexLength++
            }
            if (hexLength != 4) {
                isComplete = false
            }
            isFirst = false
        } else if ((isFirst && isIdStartChar(c)) || (!isFirst && isIdContinueChar(c))) {
            i++
            isFirst = false
        } else {
            break
        }
    }
    return isComplete
}
