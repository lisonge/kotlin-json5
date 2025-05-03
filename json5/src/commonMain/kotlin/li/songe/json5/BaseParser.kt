package li.songe.json5

internal interface BaseParser {
    val input: CharSequence
    var i: Int
    val char: Char?
        get() = input.getOrNull(i)
    val end: Boolean
        get() = i >= input.length
}

internal fun BaseParser.stop(): Nothing {
    if (end) {
        error("Unexpected Char: EOF")
    }
    error("Unexpected Char: ${input[i]} at index $i")
}

internal fun BaseParser.next(c: Char) {
    if (c == char) {
        i++
        return
    }
    stop()
}

private fun BaseParser.readHex(len: Int) {
    repeat(len) {
        if (!isHexDigit(char)) {
            stop()
        }
        i++
    }
}

internal fun BaseParser.readComment() {
    i++
    when (input.getOrNull(i)) {
        '/' -> {
            i++
            val endIndex = input.indexOfAny(newLineChars, i)
            i = if (endIndex < 0) {
                input.lastIndex
            } else {
                endIndex + 1
            }
        }

        '*' -> {
            i++
            val endIndex = input.indexOf("*/", i)
            if (endIndex < 0) {
                i = input.lastIndex
                stop()
            } else {
                i = endIndex + 2
            }
        }

        else -> stop()
    }
}

internal fun BaseParser.readWhitespace() {
    i++
    while (!end && input[i] in whiteSpaceChars) {
        i++
    }
}

private fun BaseParser.readLiteral(v: String) {
    v.forEach { next(it) }
}

internal fun BaseParser.readNull() {
    readLiteral("null")
}

internal fun BaseParser.readBoolean(): Boolean {
    return if (char == 't') {
        readLiteral("true")
        true
    } else {
        readLiteral("false")
        false
    }
}

private fun BaseParser.readDigit() {
    val start = i
    while (isDigit(char)) {
        i++
    }
    if (start == i) {
        stop()
    }
}

private fun BaseParser.readNumberPower() {
    i++
    if (char == '-' || char == '+') {
        i++
    }
    readDigit()
}

// json5 number -> json number
// 1. -> 1
// .1 -> 0.1
// +1 -> 1
private fun BaseParser.readUNumber(): Json5Number {
    return when (char) {
        'N' -> {
            readLiteral("NaN")
            Json5Number("NaN")
        }

        'I' -> {
            readLiteral("Infinity")
            Json5Number("Infinity")
        }

        '.' -> {
            // .123 -> 0.123
            var start = i
            i++
            readDigit()
            val numPart = "0" + input.substring(start, i)

            if (isPowerStartChar(char)) {
                start = i
                readNumberPower()
                val power = input.substring(start, i)
                Json5Number(numPart + power)
            } else {
                Json5Number(input.substring(start, i).let {
                    if (it.first() == '.') {
                        "0$it"
                    } else {
                        it
                    }
                })
            }
        }

        in '0'..'9' -> {
            var start = i
            var hasHex = false
            if (char == '0') { // 0x11
                i++
                if (isDigit(char)) {// not allow 00 01
                    stop()
                } else if (isHexStartChar(char)) {
                    i++
                    hasHex = true
                }
            }
            if (hasHex) {
                if (!isHexDigit(char)) {
                    stop()
                }
                i++
                while (!end && isHexDigit(char)) {
                    i++
                }
                Json5Number(hexToDecimal(input.substring(start + 2, i)))
            } else {
                var hasPoint = false // 1.2
                while (!end) {
                    if (char == '.') {
                        if (!hasPoint) {
                            hasPoint = true
                        } else {
                            stop()
                        }
                    } else if (!isDigit(char)) {
                        break
                    }
                    i++
                }
                val hasEndPoint = hasPoint && input[i - 1] == '.' // not support 1.
                val numPart = if (hasEndPoint) {
                    hasPoint = false
                    input.substring(start, i - 1) // 1. -> 1
                } else {
                    input.substring(start, i)
                }
                if (numPart == "0") { // 0e233 -> 0
                    if (isPowerStartChar(char)) {
                        readNumberPower()
                    }
                    Json5Number("0")
                } else {
                    if (isPowerStartChar(char)) {
                        start = i
                        readNumberPower()
                        val power = input.substring(start, i)
                        Json5Number(numPart + power)
                    } else {
                        if (hasPoint) {
                            Json5Number(numPart)
                        } else {
                            Json5Number(numPart)
                        }
                    }
                }
            }
        }

        else -> stop()
    }
}

internal fun BaseParser.readNumber(): Json5Number = when (char) {
    '-' -> {
        i++
        val n = readUNumber()
        Json5Number("-$n")
    }

    '+' -> {
        i++
        readUNumber()
    }

    else -> readUNumber()
}

// https://github.com/json5/json5/blob/b935d4a280eafa8835e6182551b63809e61243b0/lib/parse.js#L570
internal fun BaseParser.readString(): String {
    val wrapChar = char!! // must be ' or "
    i++
    // most
    for (j in i..input.lastIndex) {
        when (input[j]) {
            '\\' -> break
            wrapChar -> return input.substring(i, j).apply {
                i = j + 1
            }
        }
    }
    val sb = StringBuilder()
    while (true) {
        when (char) {
            wrapChar -> {
                i++
                break
            }

            '\\' -> {
                i++
                when (char) {
                    null -> stop()
                    wrapChar -> {
                        sb.append(wrapChar)
                        i++
                    }

                    'x' -> {
                        i++
                        readHex(2)
                        val hex = input.substring(i - 2, i)
                        sb.append(hex.toInt(16).toChar())
                    }

                    'u' -> {
                        i++
                        readHex(4)
                        val hex = input.substring(i - 4, i)
                        sb.append(hex.toInt(16).toChar())
                    }

                    '\'' -> {
                        sb.append('\'')
                        i++
                    }

                    '\"' -> {
                        sb.append('\"')
                        i++
                    }

                    '\\' -> {
                        sb.append('\\')
                        i++
                    }

                    'b' -> {
                        sb.append('\b')
                        i++
                    }

                    'f' -> {
                        sb.append('\u000C')
                        i++
                    }

                    'n' -> {
                        sb.append('\n')
                        i++
                    }

                    'r' -> {
                        sb.append('\r')
                        i++
                    }

                    't' -> {
                        sb.append('\t')
                        i++
                    }

                    'v' -> {
                        sb.append('\u000B')
                        i++
                    }

                    '0' -> {
                        sb.append('\u0000')
                        i++
                        if (isDigit(char)) {
                            // avoid octal ambiguity
                            stop()
                        }
                    }

                    // multiline string
                    '\u000D' -> {// \r
                        i++
                        if (char == '\u000A') {// \n
                            i++
                        }
                    }

                    // multiline string
                    '\u000A', '\u2028', '\u2029' -> {
                        i++
                    }

                    in '1'..'9' -> stop()

                    else -> {
                        sb.append(char)
                        i++
                    }
                }
            }

            null, '\n', '\r' -> stop()

            else -> {
                sb.append(char)
                i++
            }
        }
    }
    return sb.toString()
}

private fun BaseParser.readUnicode(): Char {
    i++
    next('u')
    readHex(4)
    return input.substring(i - 4, i).toInt(16).toChar()
}

internal fun BaseParser.readObjectProperty(): String {
    val startChar = char!! // must be \ or idStartChar
    val realStartChar = if (startChar == '\\') {
        val n = readUnicode()
        if (!isIdStartChar(n)) {
            stop()
        }
        n
    } else {
        // most
        for (j in (i + 1)..input.lastIndex) {
            val c = input[j]
            if (c == '\\') break
            if (!isIdContinueChar(c)) {
                return input.substring(i, j).apply {
                    i = j
                }
            }
        }
        i++
        startChar
    }
    val sb = StringBuilder()
    sb.append(realStartChar)
    while (!end) {
        val c = char!!
        if (c == '\\') {
            val n = readUnicode()
            if (!isIdContinueChar(n)) {
                stop()
            }
            sb.append(n)
        } else if (isIdContinueChar(c)) {
            i++
            sb.append(c)
        } else {
            break
        }
    }
    return sb.toString()
}
