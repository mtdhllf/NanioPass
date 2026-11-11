package cc.nanio.pass.totp


/**
 * 动态令牌配置。
 * 支持 HMAC SHA1/SHA256/SHA512，周期固定 30s，位数 6/8（新建恒 6）。
 */
data class TotpConfig(
    val secret: String,
    /** 服务名，如 Google */
    val issuer: String? = null,
    /** 账号，如 mtdhllf@gmail.com */
    val account: String? = null,
    /** SHA1 / SHA256 / SHA512 */
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
) {
    /** 列表/详情展示名：优先 issuer，其次账号 */
    val displayName: String get() = issuer?.takeIf { it.isNotBlank() } ?: account?.takeIf { it.isNotBlank() } ?: ""
}
