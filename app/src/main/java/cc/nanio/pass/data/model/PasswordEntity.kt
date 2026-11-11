package cc.nanio.pass.data.model

/**
 * 一条密码记录。
 *
 * 不包含云同步字段；动态令牌内联在同一条记录上（1 条密码最多 1 个令牌）。
 *
 * 注意：仓库层负责在落库前加密 [password] 与 [totpSecret]、读库后解密；
 * UI / ViewModel 拿到的始终是明文对象。
 */
data class PasswordEntity(
    val id: Long = 0,
    /** 名称 */
    val label: String,
    /** 账号 */
    val account: String,
    /** 密码（明文，仓库层加密落库） */
    val password: String,
    /** 网站 */
    val domain: String? = null,
    /** 备注 */
    val note: String? = null,
    /** 绑定的应用包名 */
    val packageName: String? = null,
    /** 绑定时快照的应用名；应用卸载后仍可显示 */
    val appLabel: String? = null,
    /** TOTP Base32 密钥（明文，仓库层加密落库） */
    val totpSecret: String? = null,
    /** 动态令牌 issuer */
    val totpIssuer: String? = null,
    /** 动态令牌账号 */
    val totpAccount: String? = null,
    /** HMAC 算法：SHA1 / SHA256 / SHA512 */
    val totpAlgorithm: String = "SHA1",
    /** 验证码位数（6 或 8） */
    val totpDigits: Int = 6,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 最近使用时间，-1 表示未使用 */
    val lastUsedAt: Long = -1L,
    /** 非空表示在「最近删除」中 */
    val deletedAt: Long? = null,
) {
    val hasTotp: Boolean get() = !totpSecret.isNullOrEmpty()
    val isBoundApp: Boolean get() = !packageName.isNullOrEmpty()
    val isDeleted: Boolean get() = deletedAt != null

    /** 列表头像回退用的首字符 */
    val initial: String get() = label.trim().firstOrNull()?.toString()?.uppercase() ?: "#"
}
