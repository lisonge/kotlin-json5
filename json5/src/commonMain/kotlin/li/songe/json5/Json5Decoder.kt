package li.songe.json5

import kotlinx.serialization.json.*

// https://spec.json5.org/
internal open class Json5Decoder(val input: CharSequence, val writable: Boolean = true) {
    var i = 0
    val char: Char?
        get() = input.getOrNull(i)
    val end: Boolean
        get() = i >= input.length

    fun read(): JsonElement {
        val root = i == 0
        readUseless()
        val element = when (char) {
            '{' -> readObject()
            '[' -> readArray()
            '"', '\'' -> JsonPrimitive(readString())
            in '0'..'9', '-', '+', '.', 'N', 'I' -> JsonPrimitive(readNumber())
            't' -> {
                readBooleanLiteral(true)
                JsonPrimitive(true)
            }

            'f' -> {
                readBooleanLiteral(false)
                JsonPrimitive(false)
            }

            'n' -> {
                readNullLiteral()
                JsonNull
            }

            else -> stop()
        }
        if (root) {
            readUseless()
            if (!end) {
                stop()
            }
        }
        return element
    }

    open fun readNullLiteral() {
        readLiteral("null")
    }

    open fun readBooleanLiteral(v: Boolean) {
        if (v) {
            readLiteral("true")
        } else {
            readLiteral("false")
        }
    }

    // 1. -> 1
    // .1 -> 0.1
    // +1 -> 1
    fun readUNumber(): Json5Number {
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

    open fun readNumber(): Json5Number {
        return when (char) {
            '-' -> {
                i++
                val n = readUNumber()
                return Json5Number("-$n")
            }

            '+' -> {
                i++
                return readUNumber()
            }

            else -> readUNumber()
        }
    }

    open fun readString(): String {
        val wrapChar = char!!
        i++
        val sb = StringBuilder()
        while (true) {
            when (char) {
                null -> stop()
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

                else -> {
                    sb.append(char)
                    i++
                }
            }
        }
        return sb.toString()
    }

    open fun readProperty(): String {
        val c = char
        if (c == '\'' || c == '"') {
            return readString()
        }
        val sb = StringBuilder()
        if (c == '\\') {
            i++
            next('u')
            readHex(4)
            val n = input.substring(i - 4, i).toInt(16).toChar()
            if (!isIdStartChar(n)) {
                stop()
            }
            sb.append(n)
        } else if (!isIdStartChar(c)) {
            stop()
        } else {
            sb.append(c)
            i++
        }
        while (!end) {
            if (char == '\\') {
                i++
                next('u')
                readHex(4)
                val n = input.substring(i - 4, i).toInt(16).toChar()
                if (!isIdContinueChar(n)) {
                    stop()
                }
                sb.append(n)
            } else if (isIdContinueChar(char)) {
                sb.append(char)
                i++
            } else {
                break
            }
        }
        return sb.toString()
    }

    open fun readObject(): JsonObject {
        i++
        readUseless()
        if (char == '}') {
            i++
            return JsonObject(emptyMap())
        }
        val map = if (writable) {
            mutableMapOf<String, JsonElement>()
        } else {
            HashMap(0)
        }
        while (true) {
            readUseless()
            val key = readProperty()
            readUseless()
            readColon()
            readUseless()
            val value = read()
            if (writable) {
                map[key] = value
            }
            if (readContainerEnd('}')) {
                break
            }
        }
        return JsonObject(map)
    }

    open fun readArray(): JsonArray {
        i++
        readUseless()
        if (char == ']') {
            i++
            return JsonArray(emptyList())
        }
        val list = if (writable) {
            mutableListOf<JsonElement>()
        } else {
            ArrayList(0)
        }
        while (true) {
            readUseless()
            val value = read()
            if (writable) {
                list.add(value)
            }
            if (readContainerEnd(']')) {
                break
            }
        }
        return JsonArray(list)
    }

    open fun readComment() {
        i++
        when (char) {
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

    open fun readComma() {
        i++
    }

    open fun readColon() {
        next(':')
    }

    open fun readWhitespace() {
        i++
        while (isWhiteSpace(char)) {
            i++
        }
    }

    // others

    fun stop(): Nothing {
        if (end) {
            error("Unexpected Char: EOF")
        }
        error("Unexpected Char: $char at index $i")
    }

    fun next(c: Char) {
        if (c == char) {
            i++
            return
        }
        stop()
    }

    fun readCommentOrWhitespace() {
        when {
            char == '/' -> readComment()
            isWhiteSpace(char) -> readWhitespace()
        }
    }

    fun readUseless() {
        while (true) {
            val oldIndex = i
            readCommentOrWhitespace()
            if (oldIndex == i) {
                return
            }
        }
    }

    fun readHex(len: Int) {
        repeat(len) {
            if (!isHexDigit(char)) {
                stop()
            }
            i++
        }
    }

    fun readContainerEnd(c: Char): Boolean {
        readUseless()
        if (char == c) {
            i++
            return true
        } else if (char == ',') {
            readComma()
            readUseless()
            if (char == c) {
                i++
                return true
            }
        } else {
            stop()
        }
        return false
    }

    fun readDigit() {
        val start = i
        while (isDigit(char)) {
            i++
        }
        if (start == i) {
            stop()
        }
    }

    fun readNumberPower() {
        i++
        if (char == '-' || char == '+') {
            i++
        }
        readDigit()
    }

    fun readLiteral(v: String) {
        v.forEach { next(it) }
    }

}
