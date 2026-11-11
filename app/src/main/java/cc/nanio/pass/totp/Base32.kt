package cc.nanio.pass.totp


import java.util.Locale

/**
 * RFC 4648 Base32 编解码。
 *
 * 解码前先统一规范化：去掉空白字符与 `-`、去掉尾部 `=`、转大写，
 * 便于用户从聊天/文档里粘贴密钥：
 * - 字母表固定 `ABCDEFGHIJKLMNOPQRSTUVWXYZ234567`（不含 0/1/8/9）；
 * - 非法字符抛 [IllegalArgumentException]，消息包含字符与位置；
 * - 按 5-bit 位流重组，允许长度不是 8 的倍数（尾部残余位丢弃）。
 */
object Base32 {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /** 字符 → 5-bit 值，-1 表示非法字符。 */
    private val DECODE_TABLE = IntArray(128) { -1 }.also { table ->
        ALPHABET.forEachIndexed { index, char -> table[char.code] = index }
    }

    /**
     * 规范化：删除所有空白字符与 `-`、去掉尾部 `=`、转大写。
     *
     * 返回的字符串是 [decode] 实际处理的形态；可用于展示/入库前清洗密钥。
     */
    fun normalize(input: String): String {
        val builder = StringBuilder(input.length)
        for (char in input) {
            if (char.isWhitespace() || char == '-') continue
            builder.append(char)
        }
        var end = builder.length
        while (end > 0 && builder[end - 1] == '=') {
            end--
        }
        return builder.substring(0, end).uppercase(Locale.US)
    }

    /**
     * 解码 Base32 字符串。
     *
     * @throws IllegalArgumentException 含有字母表之外的字符（如 `0`、`1`、`8`、`9`、`!`）。
     */
    fun decode(input: String): ByteArray {
        val normalized = normalize(input)
        if (normalized.isEmpty()) return ByteArray(0)

        val output = ByteArray(normalized.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var written = 0

        normalized.forEachIndexed { position, char ->
            val value = if (char.code < DECODE_TABLE.size) DECODE_TABLE[char.code] else -1
            if (value < 0) {
                throw IllegalArgumentException("Invalid Base32 character '$char' (at position $position)")
            }
            buffer = (buffer shl 5) or value
            bits += 5
            if (bits >= 8) {
                output[written++] = ((buffer shr (bits - 8)) and 0xFF).toByte()
                bits -= 8
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return output
    }

    /** 编码为带 `=` padding 的大写 Base32（[decode] 可逆）。 */
    fun encode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""

        val builder = StringBuilder((bytes.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0

        for (byte in bytes) {
            buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                builder.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        if (bits > 0) {
            builder.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        }
        while (builder.length % 8 != 0) {
            builder.append('=')
        }
        return builder.toString()
    }
}
