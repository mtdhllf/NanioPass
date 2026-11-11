package cc.nanio.pass.totp


import java.nio.ByteBuffer
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP 生成器。
 *
 * - 算法：`SHA1 → HMACSHA1`、`SHA256 → HMACSHA256`、`SHA512 → HMACSHA512`，默认 SHA1；
 * - 周期固定 30 秒；
 * - 位数 6 或 8，非法值归一到 6；
 * - counter = `floor(unixSec / period)`，HMAC 输入为 8 字节大端 counter；
 * - 动态截断后 `code % 10^digits`，左侧补 0。
 *
 * 注意：`remainingSeconds` / `progress` 没有 config 参数，固定使用 30 秒周期。
 */
object TotpEngine {

    /** 固定周期（秒）。 */
    const val PERIOD_SECONDS: Int = 30

    private const val DIGITS_SIX = 6
    private const val DIGITS_EIGHT = 8

    private val POWERS_OF_TEN = intArrayOf(
        1,
        10,
        100,
        1_000,
        10_000,
        100_000,
        1_000_000,
        10_000_000,
        100_000_000,
        1_000_000_000,
    )

    /**
     * 生成指定时刻的动态验证码。
     *
     * 计数窗口使用 [TotpConfig.period]（默认 30），算法使用 [TotpConfig.algorithm]。
     *
     * @throws IllegalArgumentException 算法未知、密钥非法或为空。
     */
    fun generate(config: TotpConfig, timeMillis: Long = System.currentTimeMillis()): String {
        val periodSeconds = effectivePeriod(config.period).toLong()
        val seconds = Math.floorDiv(timeMillis, 1000L)
        val counter = Math.floorDiv(seconds, periodSeconds)
        return codeAt(config, counter)
    }

    /**
     * HOTP 层：对指定 counter 生成验证码（RFC 4226），便于用标准测试向量验证。
     *
     * @throws IllegalArgumentException 算法未知、密钥非法或为空。
     */
    fun codeAt(config: TotpConfig, counter: Long): String {
        val digits = effectiveDigits(config.digits)
        val key = Base32.decode(config.secret)
        require(key.isNotEmpty()) { "The secret key is empty; cannot generate a TOTP code" }

        val mac = Mac.getInstance(hmacAlgorithm(config.algorithm))
        mac.init(SecretKeySpec(key, mac.algorithm))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())

        // 标准动态截断（RFC 4226 §5.3）：offset 取最后一个字节低 4 位。
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        val code = binary % POWERS_OF_TEN[digits]
        return code.toString().padStart(digits, '0')
    }

    /**
     * 当前窗口剩余秒数，范围 1..30；返回 30 表示刚好翻到新窗口。
     *
     * 等价于 `30000 - now % 30000`（单位为秒）。
     */
    fun remainingSeconds(timeMillis: Long = System.currentTimeMillis()): Int {
        val seconds = Math.floorDiv(timeMillis, 1000L)
        val remainder = Math.floorMod(seconds, PERIOD_SECONDS.toLong())
        return (PERIOD_SECONDS - remainder).toInt()
    }

    /** 当前窗口剩余比例，范围 (0, 1]；1 表示刚刷新。 */
    fun progress(timeMillis: Long = System.currentTimeMillis()): Float =
        remainingSeconds(timeMillis).toFloat() / PERIOD_SECONDS

    /**
     * 把验证码按展示格式分组：`"123456" → "123 456"`、`"12345678" → "1234 5678"`。
     *
     * 不足 [digits] 位时左侧补 0；仅用于展示，不会修改/回写任何数据。
     */
    fun formatCode(code: String, digits: Int): String {
        if (code.isEmpty()) return ""
        val normalizedDigits = effectiveDigits(digits)
        val text = if (code.length < normalizedDigits) code.padStart(normalizedDigits, '0') else code
        val split = text.length / 2
        return text.substring(0, split) + " " + text.substring(split)
    }

    /**
     * 算法名 → JCE Mac 算法名。
     *
     * 历史数据里 `totpAlgorithm` 可能存 `HMACSHA1/HMACSHA256/HMACSHA512`，
     * 这里同时接受 `SHA1/SHA256/SHA512` 与 `HMACSHA1/HMACSHA256/HMACSHA512`
     * 两种写法；算法名为空时默认 SHA1。
     *
     * @throws IllegalArgumentException 未知算法。
     */
    fun hmacAlgorithm(algorithm: String?): String {
        return when (val key = algorithm?.trim()?.uppercase(Locale.US).orEmpty()) {
            "", "SHA1", "HMACSHA1" -> "HmacSHA1"
            "SHA256", "HMACSHA256" -> "HmacSHA256"
            "SHA512", "HMACSHA512" -> "HmacSHA512"
            else -> throw IllegalArgumentException("Unsupported TOTP algorithm: $key")
        }
    }

    /** 位数只接受 6/8，其余回退 6。 */
    fun effectiveDigits(digits: Int): Int =
        if (digits == DIGITS_SIX || digits == DIGITS_EIGHT) digits else DIGITS_SIX

    /** 周期仅接受正数，其余回退 30。 */
    internal fun effectivePeriod(period: Int): Int = if (period > 0) period else PERIOD_SECONDS
}
