package net.serverpeon.twitcharchiver.hls

import com.google.common.base.Preconditions.checkState
import java.math.BigDecimal
import java.math.BigInteger

/**
 *
 */
class AttributeListParser(private val input: String) {
    private var cursor: Int = 0

    fun hasMoreAttributes(): Boolean {
        if (cursor != -1) {
            return findExpectedToken('=') != -1
        } else {
            return false
        }
    }

    fun readAttributeName(): String {
        val nextIndex = findExpectedToken('=')
        checkState(nextIndex != -1, "Malformed attribute list, missing = after attribute name")
        val name = readUntil(nextIndex)
        setAfterToken(nextIndex)
        return name
    }

    /**
     * decimal-integer: an unquoted string of characters from the set
     * [0..9] expressing an integer in base-10 arithmetic.
     */
    fun readDecimalInt(): Long {
        val nextPair = findExpectedToken(',')
        val value = try {
            readUntil(nextPair).toLong()
        } catch (ex: NumberFormatException) {
            throw IllegalStateException("Malformed attribute list, unable to read decimal-integer", ex)
        }
        setAfterToken(nextPair)
        return value
    }

    /**
     * hexadecimal-integer: an unquoted string of characters from the set
     * [0..9] and \[A..F] that is prefixed with 0x or 0X and which
     * expresses an integer in base-16 arithmetic.
     */
    fun readHexadecimalInt(): BigInteger {
        val nextPair = findExpectedToken(',')
        val str = readUntil(nextPair)
        checkState(str.startsWith("0x", ignoreCase = true))
        val value = try {
            BigInteger(str.substring(2), 16)
        } catch (ex: NumberFormatException) {
            throw IllegalStateException("Malformed attribute list, unable to read hexadecimal-integer", ex)
        }
        setAfterToken(nextPair)
        return value
    }

    /**
     * decimal-floating-point: an unquoted string of characters from the
     * set [0..9] and '.' which expresses a floating-point number in
     * decimal positional notation.
     */
    fun readDecimalFloat(): BigDecimal {
        val nextPair = findExpectedToken(',')
        val value = try {
            BigDecimal(readUntil(nextPair))
        } catch (ex: NumberFormatException) {
            throw IllegalStateException("Malformed attribute list, unable to read decimal float", ex)
        }
        setAfterToken(nextPair)
        return value
    }

    /**
     * quoted-string: a string of characters within a pair of double-
     * quotes (").  The set of characters allowed in the string and any
     * rules for escaping special characters are specified by the
     * Attribute definition, but any double-quote (") character and any
     * carriage-return or linefeed will always be replaced by an escape
     * sequence.
     *
     * @return Quoted string without the outer quotes, may contain escaped characters
     */
    fun readQuotedString(): String {
        val firstQuote = findExpectedToken('"')
        checkState(readUntil(firstQuote).isEmpty(), "Malformed attribute list, characters before opening quote of a quoted string")
        var lastQuote = firstQuote + 1
        while (true) {
            lastQuote = this.input.indexOf('"', startIndex = lastQuote)
            // Check if it is escaped
            checkState(lastQuote != -1, "Malformed attribute list, quoted string not closed")
            if (isEscaped(lastQuote, firstQuote + 1)) {
                lastQuote += 1 //Start searching from after this quote
                continue
            } else {
                break
            }
        }
        checkState(this.input.length == (lastQuote + 1) || this.input[lastQuote + 1] == ',',
                "Malformed attribute list, last quote not followed by a ','")
        val value = this.input.substring(firstQuote + 1, lastQuote)
        setAfterToken(lastQuote + 1)
        return value
    }

    /**
     * enumerated-string: an unquoted character string from a set which
     * is explicitly defined by the Attribute.  An enumerated-string will
     * never contain double-quotes ("), commas (,), or whitespace.
     */
    fun readEnumeratedString(): String {
        val nextPair = findExpectedToken(',')
        val value = readUntil(nextPair)
        setAfterToken(nextPair)
        return value
    }

    /**
     * decimal-resolution: two decimal-integers separated by the "x"
     * character.  The first integer is a horizontal pixel dimension
     * (width); the second is a vertical pixel dimension (height).
     */
    fun readResolution(allowStrayQuotes: Boolean = false): Resolution {
        val previousCursor = this.cursor
        val nextPair = findExpectedToken(',')
        val pivot = findExpectedToken('x')

        // If we have both a pivot and a next pair, ensure the pivot is before the next pair
        checkState(pivot != -1 && (nextPair == -1 || pivot < nextPair), "Malformed attribute list, invalid resolution format")

        try {
            val width = readUntil(pivot).let { str ->
                if (allowStrayQuotes && str.startsWith('"')) {
                    str.substring(1)
                } else {
                    str
                }
            }.toInt()
            setAfterToken(pivot)
            val height = readUntil(nextPair).let { str ->
                if (allowStrayQuotes && str.endsWith('"')) {
                    str.substring(0, str.length - 1)
                } else {
                    str
                }
            }.toInt()
            setAfterToken(nextPair)
            return Resolution(width, height)
        } catch (ex: NumberFormatException) {
            this.cursor = previousCursor //Restore the cursor
            throw IllegalStateException("Malformed attribute list, invalid resolution value", ex)
        }
    }

    data class Resolution(val width: Int, val height: Int)

    private fun isEscaped(position: Int, boundary: Int): Boolean {
        var isEscaped = false
        var pos = position
        while (pos-- >= boundary) {
            if (this.input[pos] == '\\') {
                isEscaped = !isEscaped //invert
            } else {
                break //non-escape character, break
            }
        }
        return isEscaped
    }

    private fun readUntil(index: Int): String {
        if (index == -1) {
            return this.input.substring(this.cursor)
        } else {
            return this.input.substring(this.cursor, index)
        }
    }

    private fun findExpectedToken(token: Char): Int {
        checkState(this.cursor != -1, "Parser reached end unexpectedly")
        return this.input.indexOf(token, startIndex = this.cursor)
    }

    private fun setAfterToken(position: Int) {
        if (position != -1) {
            this.cursor = position + 1
        } else {
            this.cursor = -1
        }
    }
}