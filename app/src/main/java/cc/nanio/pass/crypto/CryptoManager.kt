package cc.nanio.pass.crypto


/**
 * 敏感字段加密（Android Keystore 硬件-backed AES-256-GCM，密钥不可导出）。
 *
 * 对 `password` 与 `totpSecret` 两列加密后入库。
 *
 * 加密失败（如 Keystore 不可用）抛 [CryptoException]，由仓库层上抛给 UI 提示。
 */
interface CryptoManager {

    /** 加密为可存库的字符串（Base64）；空串返回空串 */
    fun encrypt(plain: String): String

    /** 解密；遇到非密文/损坏数据抛 [CryptoException] */
    fun decrypt(cipher: String): String

    /** Keystore 是否可用（首次加密时自动建密钥） */
    fun isAvailable(): Boolean

    /** 清除密钥（用于重置应用数据；会导致已加密数据不可读） */
    fun resetKey()
}

class CryptoException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
