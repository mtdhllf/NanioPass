package cc.nanio.pass.totp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RFC 4226 (HOTP) / RFC 6238 (TOTP) 标准测试向量，以及算法细节。
 */
class TotpEngineTest {

    /** `"12345678901234567890"` 的 Base32（RFC 4226/6238 SHA1 种子）。 */
    private val secretSha1 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

    /** `"12345678901234567890123456789012"` 的 Base32（RFC 6238 SHA256 种子）。 */
    private val secretSha256 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZA"

    /** 64 字节 `"1234...1234"` 的 Base32（RFC 6238 SHA512 种子）。 */
    private val secretSha512 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ" +
        "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQGEZDGNA"

    // ---------------------------------------------------------------- RFC 4226

    @Test
    fun hotpMatchesRfc4226AppendixDVectors() {
        val config = TotpConfig(secret = secretSha1, digits = 6)
        val expected = listOf(
            "755224", "287082", "359152", "969429", "338314",
            "254676", "287922", "162583", "399871", "520489",
        )
        expected.forEachIndexed { counter, code ->
            assertEquals("counter=$counter", code, TotpEngine.codeAt(config, counter.toLong()))
        }
    }

    // ---------------------------------------------------------------- RFC 6238

    @Test
    fun totpMatchesRfc6238Sha1Vectors() {
        val config = TotpConfig(secret = secretSha1, algorithm = "SHA1", digits = 8)
        val vectors = listOf(
            59L to "94287082",
            1111111109L to "07081804",
            1111111111L to "14050471",
            1234567890L to "89005924",
            2000000000L to "69279037",
            20000000000L to "65353130",
        )
        vectors.forEach { (timeSeconds, code) ->
            assertEquals("t=$timeSeconds", code, TotpEngine.generate(config, timeSeconds * 1000L))
        }
    }

    @Test
    fun totpMatchesRfc6238Sha256Vectors() {
        val config = TotpConfig(secret = secretSha256, algorithm = "SHA256", digits = 8)
        val vectors = listOf(
            59L to "46119246",
            1111111109L to "68084774",
            1111111111L to "67062674",
            1234567890L to "91819424",
            2000000000L to "90698825",
            20000000000L to "77737706",
        )
        vectors.forEach { (timeSeconds, code) ->
            assertEquals("t=$timeSeconds", code, TotpEngine.generate(config, timeSeconds * 1000L))
        }
    }

    @Test
    fun totpMatchesRfc6238Sha512Vectors() {
        val config = TotpConfig(secret = secretSha512, algorithm = "SHA512", digits = 8)
        val vectors = listOf(
            59L to "90693936",
            1111111109L to "25091201",
            1111111111L to "99943326",
            1234567890L to "93441116",
            2000000000L to "38618901",
            20000000000L to "47863826",
        )
        vectors.forEach { (timeSeconds, code) ->
            assertEquals("t=$timeSeconds", code, TotpEngine.generate(config, timeSeconds * 1000L))
        }
    }

    // ------------------------------------------------------------ 行为细节

    @Test
    fun defaultDigitsIsSixAndTruncatesRfcVector() {
        // 同一时间窗的 8 位 RFC 码 94287082 → 6 位应为 287082。
        assertEquals("287082", TotpEngine.generate(TotpConfig(secret = secretSha1), 59_000L))
    }

    @Test
    fun digitsOtherThanSixOrEightFallBackToSix() {
        val six = TotpConfig(secret = secretSha1, digits = 6)
        val invalid = TotpConfig(secret = secretSha1, digits = 7)
        assertEquals(TotpEngine.generate(six, 59_000L), TotpEngine.generate(invalid, 59_000L))
        assertEquals(
            TotpEngine.generate(six, 59_000L),
            TotpEngine.generate(TotpConfig(secret = secretSha1, digits = 0), 59_000L),
        )
    }

    @Test
    fun algorithmIsCaseInsensitive() {
        val lowercase = TotpConfig(secret = secretSha256, algorithm = "sha256", digits = 8)
        assertEquals("46119246", TotpEngine.generate(lowercase, 59_000L))
    }

    @Test
    fun unknownAlgorithmThrowsIllegalArgument() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            TotpEngine.generate(TotpConfig(secret = secretSha1, algorithm = "MD5"))
        }
        assertTrue(error.message!!.contains("MD5"))
    }

    @Test
    fun emptyAlgorithmDefaultsToSha1() {
        val config = TotpConfig(secret = secretSha1, algorithm = "")
        assertEquals("287082", TotpEngine.generate(config, 59_000L))
    }

    @Test
    fun hmacPrefixedAlgorithmAliasIsAccepted() {
        // 历史数据里 totpAlgorithm 可能存 HMACSHA1/HMACSHA256/HMACSHA512。
        val sha1 = TotpConfig(secret = secretSha1, algorithm = "SHA1", digits = 8)
        val alias = TotpConfig(secret = secretSha1, algorithm = "HMACSHA1", digits = 8)
        assertEquals(TotpEngine.generate(sha1, 59_000L), TotpEngine.generate(alias, 59_000L))
    }

    @Test
    fun generateUsesConfigPeriod() {
        val config = TotpConfig(secret = secretSha1, digits = 6, period = 60)
        assertEquals(TotpEngine.codeAt(config, 0L), TotpEngine.generate(config, 0L))
        assertEquals(TotpEngine.codeAt(config, 0L), TotpEngine.generate(config, 59_999L))
        assertEquals(TotpEngine.codeAt(config, 1L), TotpEngine.generate(config, 60_000L))
        // 默认 30 秒：60_000ms 已经进入 counter=2。
        assertEquals(
            TotpEngine.codeAt(TotpConfig(secret = secretSha1, digits = 6), 2L),
            TotpEngine.generate(TotpConfig(secret = secretSha1, digits = 6), 60_000L),
        )
    }

    @Test
    fun negativeTimeFloorsCounter() {
        val config = TotpConfig(secret = secretSha1, digits = 6)
        assertEquals(TotpEngine.codeAt(config, -1L), TotpEngine.generate(config, -1L))
    }

    @Test
    fun emptySecretThrowsIllegalArgument() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            TotpEngine.generate(TotpConfig(secret = ""))
        }
        assertTrue(error.message!!.contains("secret key is empty"))
    }

    @Test
    fun invalidSecretCharacterThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException::class.java) {
            TotpEngine.generate(TotpConfig(secret = "ABC!DEF"))
        }
    }

    // ------------------------------------------------- remainingSeconds / progress

    @Test
    fun remainingSecondsCoversWindowBoundaries() {
        assertEquals(30, TotpEngine.remainingSeconds(0L))
        assertEquals(30, TotpEngine.remainingSeconds(999L))
        assertEquals(29, TotpEngine.remainingSeconds(1_000L))
        assertEquals(15, TotpEngine.remainingSeconds(15_000L))
        assertEquals(1, TotpEngine.remainingSeconds(29_999L))
        assertEquals(30, TotpEngine.remainingSeconds(30_000L))
        assertEquals(30, TotpEngine.remainingSeconds(30_001L))
        assertEquals(1, TotpEngine.remainingSeconds(59_999L))
        assertEquals(30, TotpEngine.remainingSeconds(60_000L))
    }

    @Test
    fun progressIsRemainingRatio() {
        assertEquals(1f, TotpEngine.progress(0L), 1e-6f)
        assertEquals(0.5f, TotpEngine.progress(15_000L), 1e-6f)
        assertEquals(1f, TotpEngine.progress(30_000L), 1e-6f)
        assertEquals(1f / 30f, TotpEngine.progress(29_999L), 1e-6f)
    }

    // ------------------------------------------------------------------ formatCode

    @Test
    fun formatCodeSplitsInTheMiddle() {
        assertEquals("123 456", TotpEngine.formatCode("123456", 6))
        assertEquals("1234 5678", TotpEngine.formatCode("12345678", 8))
        assertEquals("000 123", TotpEngine.formatCode("123", 6))
        assertEquals("", TotpEngine.formatCode("", 6))
    }
}
