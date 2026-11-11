package cc.nanio.pass.totp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RFC 4648 §6 Base32 向量 + 预处理规则（trim / 空格 / `-` / padding / 大小写）。
 */
class Base32Test {

    @Test
    fun encodeMatchesRfc4648Vectors() {
        assertEquals("", Base32.encode(ByteArray(0)))
        assertEquals("MY======", Base32.encode("f".toByteArray()))
        assertEquals("MZXQ====", Base32.encode("fo".toByteArray()))
        assertEquals("MZXW6===", Base32.encode("foo".toByteArray()))
        assertEquals("MZXW6YQ=", Base32.encode("foob".toByteArray()))
        assertEquals("MZXW6YTB", Base32.encode("fooba".toByteArray()))
        assertEquals("MZXW6YTBOI======", Base32.encode("foobar".toByteArray()))
    }

    @Test
    fun decodeMatchesRfc4648Vectors() {
        assertArrayEquals(ByteArray(0), Base32.decode(""))
        assertArrayEquals("f".toByteArray(), Base32.decode("MY======"))
        assertArrayEquals("fo".toByteArray(), Base32.decode("MZXQ===="))
        assertArrayEquals("foo".toByteArray(), Base32.decode("MZXW6==="))
        assertArrayEquals("foob".toByteArray(), Base32.decode("MZXW6YQ="))
        assertArrayEquals("fooba".toByteArray(), Base32.decode("MZXW6YTB"))
        assertArrayEquals("foobar".toByteArray(), Base32.decode("MZXW6YTBOI======"))
    }

    @Test
    fun decodeKnownTotpSecret() {
        val expected = "12345678901234567890".toByteArray()
        assertArrayEquals(expected, Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"))
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Base32.encode(expected))
    }

    @Test
    fun decodeAcceptsPaddingLowercaseSpacesAndHyphens() {
        val expected = "foobar".toByteArray()
        assertArrayEquals(expected, Base32.decode("MZXW6YTBOI======")) // padding
        assertArrayEquals(expected, Base32.decode("mzxw6ytboi")) // 小写、无 padding
        assertArrayEquals(expected, Base32.decode("MZXW 6YTB OI")) // 空格
        assertArrayEquals(expected, Base32.decode("MZX-W6YT-BOI")) // 连字符
        assertArrayEquals(expected, Base32.decode("  MZXW6YTBOI===  ")) // 首尾空白
        assertArrayEquals(expected, Base32.decode("mZx-W6 YtB oI=")) // 混合
        assertArrayEquals(expected, Base32.decode("MZXW\t6YTB\nOI")) // 其它空白
    }

    @Test
    fun decodeTreatsAllPaddingAndBlankAsEmpty() {
        assertArrayEquals(ByteArray(0), Base32.decode("   "))
        assertArrayEquals(ByteArray(0), Base32.decode("===="))
        assertArrayEquals(ByteArray(0), Base32.decode("-- --"))
    }

    @Test
    fun normalizeProducesCanonicalSecret() {
        assertEquals("MZXW6YTBOI", Base32.normalize(" mzx-w6 ytb oi==== "))
        assertEquals("GEZDGNBVGY3TQOJQ", Base32.normalize("gezdgnbvgy3tqojq"))
    }

    @Test
    fun encodeDecodeRoundTripForManyLengths() {
        for (length in 0..40) {
            val bytes = ByteArray(length) { (it * 37 + 11).toByte() }
            assertArrayEquals("length=$length", bytes, Base32.decode(Base32.encode(bytes)))
        }
    }

    @Test
    fun decodeRejectsIllegalCharactersWithPosition() {
        // 规范化后为 "JBSWY3DP!HPK3PXP"，'!' 位于下标 8。
        val error = assertThrows(IllegalArgumentException::class.java) {
            Base32.decode("JBSWY3DP!HPK3PXP")
        }
        assertEquals("Invalid Base32 character '!' (at position 8)", error.message)
    }

    @Test
    fun decodeRejectsAmbiguousDigitsAndLetters() {
        // RFC 4648 字母表不含 0/1/8/9。
        for (illegal in listOf('0', '1', '8', '9')) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                Base32.decode("ABC${illegal}DEF")
            }
            assertTrue("异常消息应包含字符 $illegal", error.message!!.contains(illegal))
            assertTrue("异常消息应包含位置 3", error.message!!.contains("at position 3"))
        }
    }

    @Test
    fun decodeRejectsPaddingInTheMiddle() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            Base32.decode("MZ=X")
        }
        assertTrue(error.message!!.contains("at position 2"))
    }
}
