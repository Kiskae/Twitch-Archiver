package net.serverpeon.twitcharchiver.hls

import org.junit.Assert
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AttributeListParserTest {
    @Test fun testDecimalInt() {
        val parser = AttributeListParser("BANDWIDTH=2100")
        assertEquals("BANDWIDTH", parser.readAttributeName())
        assertEquals(2100, parser.readDecimalInt())
    }

    @Test fun testMultipleAttributesDecimal() {
        val parser = AttributeListParser("BANDWIDTH=2100,HELLO=")
        assertEquals("BANDWIDTH", parser.readAttributeName())
        assertEquals(2100, parser.readDecimalInt())
        assertTrue { parser.hasMoreAttributes() }
        assertEquals("HELLO", parser.readAttributeName())
    }

    @Test fun testHexadecimalInt() {
        val parser = AttributeListParser("IV=0x1703C70")
        assertEquals("IV", parser.readAttributeName())
        assertEquals(BigInteger.valueOf(24132720), parser.readHexadecimalInt())

        assertFalse { parser.hasMoreAttributes() }


        val parser2 = AttributeListParser("IV=0x714A3A746F71552E3136375C3E")
        assertEquals("IV", parser2.readAttributeName())
        assertEquals(BigInteger("714a3a746f71552e3136375c3e", 16), parser2.readHexadecimalInt())

        assertFalse { parser2.hasMoreAttributes() }
    }

    @Test fun testMultipleAttributesHexadecimal() {
        val parser = AttributeListParser("IV=0x714A3A746F71552E3136375C3E,BANDWIDTH=2100")
        assertEquals("IV", parser.readAttributeName())
        assertEquals(BigInteger("714a3a746f71552e3136375c3e", 16), parser.readHexadecimalInt())
        assertTrue { parser.hasMoreAttributes() }
        assertEquals("BANDWIDTH", parser.readAttributeName())
        assertEquals(2100, parser.readDecimalInt())
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test(expected = IllegalStateException::class)
    fun testInvalidHexadecimal() {
        val parser = AttributeListParser("IV=0xZZZZZ")
        assertEquals("IV", parser.readAttributeName())
        parser.readHexadecimalInt()
        Assert.fail()
    }

    @Test fun testDecimalFloat() {
        val parser = AttributeListParser("TEST=123.456")
        assertEquals("TEST", parser.readAttributeName())
        assertEquals(BigDecimal("123.456"), parser.readDecimalFloat())
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test fun testMultipleAttributesFloat() {
        val parser = AttributeListParser("TEST=123.456,BANDWIDTH=2100")
        assertEquals("TEST", parser.readAttributeName())
        assertEquals(BigDecimal("123.456"), parser.readDecimalFloat())
        assertTrue { parser.hasMoreAttributes() }
        assertEquals("BANDWIDTH", parser.readAttributeName())
        assertEquals(2100, parser.readDecimalInt())
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test(expected = IllegalStateException::class)
    fun testInvalidFloat() {
        val parser = AttributeListParser("TEST=123.hello")
        assertEquals("TEST", parser.readAttributeName())
        parser.readDecimalFloat()
        Assert.fail()
    }

    @Test fun testQuotedString() {
        val parser = AttributeListParser("TITLE=\"Hello World\"")
        assertEquals("TITLE", parser.readAttributeName())
        assertEquals("Hello World", parser.readQuotedString())
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test fun testQuotedStringComplex() {
        val parser = AttributeListParser("TITLE=\"Hello World\\\"\\nHello\"")
        assertEquals("TITLE", parser.readAttributeName())
        assertEquals("Hello World\\\"\\nHello", parser.readQuotedString())
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test fun testRediculousQuotedString() {
        val parser = AttributeListParser("TITLE=\"Hello World\\\\\\\"\\nHello\"")
        assertEquals("TITLE", parser.readAttributeName())
        assertEquals("Hello World\\\\\\\"\\nHello", parser.readQuotedString())
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test(expected = IllegalStateException::class)
    fun testMissingQuote() {
        val parser = AttributeListParser("TITLE=\"Hello World")
        assertEquals("TITLE", parser.readAttributeName())
        parser.readQuotedString()
        Assert.fail()
    }

    @Test(expected = IllegalStateException::class)
    fun testBadEscaping() {
        val parser = AttributeListParser("TITLE=\"Hello World\\\"")
        assertEquals("TITLE", parser.readAttributeName())
        parser.readQuotedString()
        Assert.fail()
    }

    @Test fun testMultipleAttributesQuotedString() {
        val parser = AttributeListParser("TITLE=\"Hello World\",BANDWIDTH=2100")
        assertEquals("TITLE", parser.readAttributeName())
        assertEquals("Hello World", parser.readQuotedString())
        assertTrue { parser.hasMoreAttributes() }
        assertEquals("BANDWIDTH", parser.readAttributeName())
        assertEquals(2100, parser.readDecimalInt())
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test(expected = IllegalStateException::class)
    fun testMultipleAttributesQuotedStringInvalid() {
        val parser = AttributeListParser("TITLE=\"Hello World\"derp,BANDWIDTH=2100")
        assertEquals("TITLE", parser.readAttributeName())
        assertEquals("Hello World", parser.readQuotedString())
        Assert.fail()
    }

    @Test fun testEnumeratedString() {
        val parser = AttributeListParser("VALUE=HELLO")
        assertEquals("VALUE", parser.readAttributeName())
        assertEquals("HELLO", parser.readEnumeratedString())
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test fun testMultipleAttributesEnumeratedString() {
        val parser = AttributeListParser("VALUE=HELLO,BANDWIDTH=2100")
        assertEquals("VALUE", parser.readAttributeName())
        assertEquals("HELLO", parser.readEnumeratedString())
        assertTrue { parser.hasMoreAttributes() }
        assertEquals("BANDWIDTH", parser.readAttributeName())
        assertEquals(2100, parser.readDecimalInt())
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test fun testResolution() {
        val parser = AttributeListParser("SIZE=200x300")
        assertEquals("SIZE", parser.readAttributeName())
        assertEquals(AttributeListParser.Resolution(width = 200, height = 300), parser.readResolution())
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test fun testMultipleAttributesResolution() {
        val parser = AttributeListParser("SIZE=200x300,BANDWIDTH=2100")
        assertEquals("SIZE", parser.readAttributeName())
        assertEquals(AttributeListParser.Resolution(width = 200, height = 300), parser.readResolution())
        assertTrue { parser.hasMoreAttributes() }
        assertEquals("BANDWIDTH", parser.readAttributeName())
        assertEquals(2100, parser.readDecimalInt())
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test(expected = IllegalStateException::class)
    fun testInvalidResolution() {
        val parser = AttributeListParser("SIZE=200xhello!")
        assertEquals("SIZE", parser.readAttributeName())
        parser.readResolution()
        Assert.fail()
    }

    @Test fun testNoAttributes() {
        val parser = AttributeListParser("")
        assertFalse { parser.hasMoreAttributes() }
    }

    @Test fun testHasAttribute() {
        val parser = AttributeListParser("BANDWIDTH=2100")
        assertTrue { parser.hasMoreAttributes() }

        // Skip to end
        assertNotNull(parser.readAttributeName())
        assertNotNull(parser.readDecimalInt())

        assertFalse { parser.hasMoreAttributes() }
    }

    @Test fun testHasMultipleAttributes() {
        val parser = AttributeListParser("BANDWIDTH=2100,BANDWIDTH=2100")
        assertTrue { parser.hasMoreAttributes() }

        // Skip to next attribute
        assertNotNull(parser.readAttributeName())
        assertNotNull(parser.readDecimalInt())

        assertTrue { parser.hasMoreAttributes() }

        // Skip to end
        assertNotNull(parser.readAttributeName())
        assertNotNull(parser.readDecimalInt())

        assertFalse { parser.hasMoreAttributes() }
    }


    @Test(expected = IllegalStateException::class)
    fun testMalformedName() {
        val parser = AttributeListParser("derp")
        parser.readAttributeName()
        Assert.fail()
    }

    @Test(expected = IllegalStateException::class)
    fun testInvalidDecimalInt() {
        val parser = AttributeListParser("BANDWIDTH=21a")
        assertNotNull(parser.readAttributeName())
        parser.readDecimalInt()
        Assert.fail()
    }
}