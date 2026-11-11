package cc.nanio.pass.totp


import cc.nanio.pass.R
import java.io.ByteArrayOutputStream
import java.util.Locale

/** otpauth:// 解析结果（成功携带配置，失败携带可展示的原因）。 */
sealed interface TotpParseResult {
    data class Success(val config: TotpConfig) : TotpParseResult
    data class Failure(
        @androidx.annotation.StringRes val reasonRes: Int,
        val args: List<Any?> = emptyList(),
    ) : TotpParseResult
}

/**
 * `otpauth://totp/<label>?secret=...` 解析。
 *
 * 解析规则：
 * - 只接受 `otpauth://totp/...`；`scheme` 忽略大小写，`authority` 必须严格等于 `totp`
 *   （`otpauth://hotp/...` 拒绝）；
 * - `label` 按**第一个** `:` 拆成 `issuer:account`；account 为冒号后全部内容；
 * - `issuer` 查询参数非空时优先于 label 里的 issuer；
 * - `secret` 取查询参数；
 * - `algorithm` 映射 SHA1/SHA256/SHA512（缺省 SHA1，未知算法视为非法）；
 * - `digits` 参数会被解析但**丢弃**（配置恒为 6）；`period` 参数**完全不读**（恒 30）；
 * - 不是 otpauth 的字符串整体当作裸 Base32 密钥（issuer/account 为 null）。
 *
 * 手写解析（不依赖 `android.net.Uri`），便于在 JVM 单元测试中直接运行；
 * 解析失败统一转成 [TotpParseResult.Failure] 并附带原因。
 */
object OtpauthParser {

    private val EMPTY_REASON = R.string.s_empty_secret_key
    private val MISSING_PATH_REASON = R.string.s_qr_code_is_missing_account_information
    private val MISSING_SECRET_REASON = R.string.s_missing_secret_parameter
    private const val DEFAULT_ALGORITHM = "SHA1"

    private val SCHEME_REGEX = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
    private val SUPPORTED_ALGORITHMS = setOf("SHA1", "SHA256", "SHA512")

    /**
     * 解析扫码结果或用户输入。
     *
     * @param raw 二维码/手输内容，允许首尾空白；null 或空白视为非法输入。
     */
    fun parse(raw: String?): TotpParseResult {
        val content = raw?.trim().orEmpty()
        if (content.isEmpty()) return TotpParseResult.Failure(EMPTY_REASON)

        val scheme = SCHEME_REGEX.find(content)?.groupValues?.get(1)
        if (scheme == null || !scheme.equals("otpauth", ignoreCase = true)) {
            return parseBareSecret(content)
        }
        return parseOtpauth(content)
    }

    /** 非 otpauth：整串当裸 Base32 密钥，issuer/account 为 null。 */
    private fun parseBareSecret(secret: String): TotpParseResult {
        val config = TotpConfig(
            secret = secret,
            issuer = null,
            account = null,
            algorithm = DEFAULT_ALGORITHM,
            digits = 6,
            period = TotpEngine.PERIOD_SECONDS,
        )
        return validate(config)
    }

    private fun parseOtpauth(content: String): TotpParseResult {
        val schemeMatch = SCHEME_REGEX.find(content)
            ?: return TotpParseResult.Failure(R.string.s_not_a_valid_otpauth_link)
        val rest = content.substring(schemeMatch.range.last + 1)
        if (!rest.startsWith("//")) {
            return TotpParseResult.Failure(R.string.s_not_a_valid_otpauth_link_missing)
        }

        var body = rest.substring(2)
        val authorityEnd = body.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (authorityEnd < 0) body else body.substring(0, authorityEnd)
        if (authority.isEmpty()) {
            return TotpParseResult.Failure(R.string.s_otpauth_link_is_missing_an_authority)
        }
        if (authority != "totp") {
            return TotpParseResult.Failure(R.string.s_only_otpauth_totp_is_supported_unsupported_s, listOf(authority))
        }
        if (authorityEnd < 0) {
            return TotpParseResult.Failure(MISSING_PATH_REASON)
        }
        body = body.substring(authorityEnd)

        // fragment 不参与 path/query 解析（与 Uri.getPath/getQuery 一致）。
        val fragmentStart = body.indexOf('#')
        if (fragmentStart >= 0) {
            body = body.substring(0, fragmentStart)
        }
        val queryStart = body.indexOf('?')
        val rawPath = if (queryStart >= 0) body.substring(0, queryStart) else body
        val rawQuery = if (queryStart >= 0) body.substring(queryStart + 1) else ""

        if (!rawPath.startsWith("/")) {
            return TotpParseResult.Failure(MISSING_PATH_REASON)
        }
        val label = percentDecode(rawPath.substring(1), plusAsSpace = false).trim()
        if (label.isEmpty()) {
            return TotpParseResult.Failure(MISSING_PATH_REASON)
        }

        // 按第一个 ':' 拆分：冒号前 issuer，冒号后 account。
        val colonIndex = label.indexOf(':')
        val issuerFromLabel = if (colonIndex >= 0) {
            label.substring(0, colonIndex).trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
        val account = if (colonIndex >= 0) label.substring(colonIndex + 1).trim() else label
        if (account.isEmpty()) {
            return TotpParseResult.Failure(MISSING_PATH_REASON)
        }

        val params = parseQuery(rawQuery)
        val secret = params["secret"]?.trim()
        if (secret.isNullOrEmpty()) {
            return TotpParseResult.Failure(MISSING_SECRET_REASON)
        }

        // issuer 查询参数优先；为空则回退到 label 里的 issuer。
        val issuerParam = params["issuer"]?.trim()?.takeIf { it.isNotEmpty() }
        val issuer = issuerParam ?: issuerFromLabel

        val algorithmRaw = params["algorithm"]?.trim()?.takeIf { it.isNotEmpty() }
        val algorithm = try {
            normalizeAlgorithm(algorithmRaw)
        } catch (error: IllegalArgumentException) {
            return TotpParseResult.Failure(R.string.s_unsupported_algorithm_s, listOf(algorithmRaw.orEmpty()))
        }

        // digits 只做解析校验，结果被丢弃（落库恒为 6）；
        // 甚至解析失败也不影响导入（保持 6）。period 参数完全不读。
        params["digits"]?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()

        val config = TotpConfig(
            secret = secret,
            issuer = issuer,
            account = account,
            algorithm = algorithm,
            digits = 6,
            period = TotpEngine.PERIOD_SECONDS,
        )
        return validate(config)
    }

    /** 配置可用性校验：Base32 解码 + Mac 初始化 + 试算一次验证码。 */
    private fun validate(config: TotpConfig): TotpParseResult {
        if (config.secret.isBlank()) {
            return TotpParseResult.Failure(MISSING_SECRET_REASON)
        }
        return try {
            TotpEngine.generate(config)
            TotpParseResult.Success(config)
        } catch (error: IllegalArgumentException) {
            TotpParseResult.Failure(R.string.s_invalid_secret_key_s, listOf(error.message ?: "Unable to parse"))
        } catch (error: Exception) {
            TotpParseResult.Failure(R.string.s_invalid_secret_key_s, listOf(error.message ?: error.javaClass.simpleName))
        }
    }

    private fun normalizeAlgorithm(raw: String?): String {
        if (raw.isNullOrBlank()) return DEFAULT_ALGORITHM
        val key = raw.trim().uppercase(Locale.US)
        if (key !in SUPPORTED_ALGORITHMS) {
            throw IllegalArgumentException("Unsupported algorithm: $raw")
        }
        return key
    }

    /** 解析 query 为 Map；同名参数取第一个（与 `Uri.getQueryParameter` 一致）。 */
    private fun parseQuery(query: String): Map<String, String> {
        if (query.isEmpty()) return emptyMap()
        val params = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val equalsIndex = pair.indexOf('=')
            val rawName = if (equalsIndex >= 0) pair.substring(0, equalsIndex) else pair
            val rawValue = if (equalsIndex >= 0) pair.substring(equalsIndex + 1) else ""
            val name = percentDecode(rawName, plusAsSpace = true)
            if (!params.containsKey(name)) {
                params[name] = percentDecode(rawValue, plusAsSpace = true)
            }
        }
        return params
    }

    /**
     * 百分号解码（UTF-8）。
     *
     * query 中 `+` 视为空格（同 Android `Uri.getQueryParameter`），path 中 `+` 原样保留。
     * 非法的 `%` 序列原样保留，避免因个别脏数据整体失败。
     */
    private fun percentDecode(input: String, plusAsSpace: Boolean): String {
        if ('%' !in input && !(plusAsSpace && '+' in input)) return input

        val output = ByteArrayOutputStream(input.length)
        val literal = StringBuilder()
        fun flushLiteral() {
            if (literal.isNotEmpty()) {
                output.write(literal.toString().toByteArray(Charsets.UTF_8))
                literal.setLength(0)
            }
        }

        var index = 0
        while (index < input.length) {
            val char = input[index]
            if (char == '%' && index + 2 < input.length && isHexDigit(input[index + 1]) && isHexDigit(input[index + 2])) {
                flushLiteral()
                val high = hexValue(input[index + 1])
                val low = hexValue(input[index + 2])
                output.write((high shl 4) or low)
                index += 3
                continue
            }
            if (char == '+' && plusAsSpace) {
                flushLiteral()
                output.write(' '.code)
                index++
                continue
            }
            literal.append(char)
            index++
        }
        flushLiteral()
        return output.toString(Charsets.UTF_8.name())
    }

    private fun isHexDigit(char: Char): Boolean =
        char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

    private fun hexValue(char: Char): Int = when (char) {
        in '0'..'9' -> char.code - '0'.code
        in 'a'..'f' -> char.code - 'a'.code + 10
        else -> char.code - 'A'.code + 10
    }
}
