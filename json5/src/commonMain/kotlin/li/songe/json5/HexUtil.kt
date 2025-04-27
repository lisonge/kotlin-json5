package li.songe.json5

internal fun hexToDecimal(hexStr: String): String {
    var decimalStr = "0"
    for (c in hexStr) {
        decimalStr = addStrings(multiplyBy16(decimalStr), getHexDigit(c))
    }
    return decimalStr
}

private fun getHexDigit(c: Char): String = when (c) {
    '0' -> "0"
    '1' -> "1"
    '2' -> "2"
    '3' -> "3"
    '4' -> "4"
    '5' -> "5"
    '6' -> "6"
    '7' -> "7"
    '8' -> "8"
    '9' -> "9"
    'a' -> "10"
    'b' -> "11"
    'c' -> "12"
    'd' -> "13"
    'e' -> "14"
    'f' -> "15"
    'A' -> "10"
    'B' -> "11"
    'C' -> "12"
    'D' -> "13"
    'E' -> "14"
    'F' -> "15"
    else -> throw IllegalArgumentException("invalid hex digit: $c")
}

private fun multiplyBy16(numStr: String): String {
    if (numStr == "0") return "0"
    val multipliedBy10 = numStr + "0"
    val multipliedBy6 = multiplyBySingleDigit(numStr, 16 - 10)
    return addStrings(multipliedBy10, multipliedBy6)
}

@Suppress("SameParameterValue")
private fun multiplyBySingleDigit(numStr: String, digit: Int): String {
    if (digit == 0) return "0"

    var result = ""
    var carry = 0

    for (i in numStr.length - 1 downTo 0) {
        val currentDigit = numStr[i] - '0'
        val product = currentDigit * digit + carry
        result = (product % 10).toString() + result
        carry = product / 10
    }

    if (carry > 0) {
        result = carry.toString() + result
    }

    return result
}

private fun addStrings(num1: String, num2: String): String {
    var i = num1.length - 1
    var j = num2.length - 1
    var carry = 0
    val result = StringBuilder()

    while (i >= 0 || j >= 0 || carry > 0) {
        val digit1 = if (i >= 0) num1[i--] - '0' else 0
        val digit2 = if (j >= 0) num2[j--] - '0' else 0
        val sum = digit1 + digit2 + carry
        result.append(sum % 10)
        carry = sum / 10
    }

    return result.reverse().toString()
}