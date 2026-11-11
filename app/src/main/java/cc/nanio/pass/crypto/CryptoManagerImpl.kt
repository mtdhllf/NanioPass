package cc.nanio.pass.crypto



import cc.nanio.pass.R
import cc.nanio.pass.Graph
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [CryptoManager] 的 Android Keystore 实现。
 *
 * - 密钥：Keystore alias `nanio_password_field_v1`，AES-256，GCM/NoPadding，不可导出，
 *   不要求用户认证（避免每次读写列表都弹指纹；指纹解锁由 UI 层单独把关）。
 * - 落库格式：`"v1:" + Base64(iv || ciphertext)`，其中 iv 固定 12 字节、Base64 使用 `NO_WRAP`；
 *   GCM 的 16 字节认证 tag 已包含在 ciphertext 尾部，因此无需额外存储。
 * - 空串直接返回空串（密码/令牌列可能为空，避免把空串加密成一段无意义密文）。
 * - 任何异常统一转成 [CryptoException]，绝不静默返回空串，避免解密失败被当成空密码写回库。
 *
 * 线程安全：Keystore 的取键/建键通过 [KEY_LOCK] 串行化；[keyStore] 用 `by lazy` 初始化。
 */
class CryptoManagerImpl : CryptoManager {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    override fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv: ByteArray = cipher.iv ?: throw CryptoException(Graph.appContext.getString(R.string.s_encryption_failed_keystore_did_not_return_a_gcm_))
            if (iv.size != IV_SIZE) {
                throw CryptoException(Graph.appContext.getString(R.string.s_encryption_failed_invalid_gcm_iv_length_expected, IV_SIZE, iv.size))
            }
            val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val payload = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, payload, 0, iv.size)
            System.arraycopy(cipherText, 0, payload, iv.size, cipherText.size)
            PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        } catch (e: CryptoException) {
            throw e
        } catch (e: Exception) {
            throw CryptoException(Graph.appContext.getString(R.string.s_encryption_failed_s, e.javaClass.simpleName), e)
        }
    }

    override fun decrypt(cipher: String): String {
        if (cipher.isEmpty()) return ""
        if (!cipher.startsWith(PREFIX)) {
            throw CryptoException(Graph.appContext.getString(R.string.s_decryption_failed_unsupported_ciphertext_format_, PREFIX))
        }
        return try {
            val payload = Base64.decode(cipher.substring(PREFIX.length), Base64.DEFAULT)
            if (payload.size <= IV_SIZE) {
                throw CryptoException(Graph.appContext.getString(R.string.s_decryption_failed_invalid_ciphertext_length_s_by, payload.size))
            }
            val iv = payload.copyOfRange(0, IV_SIZE)
            val cipherText = payload.copyOfRange(IV_SIZE, payload.size)
            val c = Cipher.getInstance(TRANSFORMATION)
            c.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(c.doFinal(cipherText), Charsets.UTF_8)
        } catch (e: CryptoException) {
            throw e
        } catch (e: Exception) {
            // 认证失败（数据被篡改/密钥已被 resetKey 删除）都会走到这里，不能静默吞掉。
            throw CryptoException(Graph.appContext.getString(R.string.s_decryption_failed_s, e.javaClass.simpleName), e)
        }
    }

    override fun isAvailable(): Boolean = try {
        getOrCreateKey()
        true
    } catch (e: Exception) {
        false
    }

    override fun resetKey() {
        try {
            synchronized(KEY_LOCK) {
                if (keyStore.containsAlias(KEY_ALIAS)) {
                    keyStore.deleteEntry(KEY_ALIAS)
                }
            }
        } catch (e: Exception) {
            throw CryptoException(Graph.appContext.getString(R.string.s_failed_to_clear_the_keystore_key_s, e.javaClass.simpleName), e)
        }
    }

    /** 取已有密钥；不存在则创建（首次加密/isAvailable 时惰性建键）。 */
    private fun getOrCreateKey(): SecretKey = synchronized(KEY_LOCK) {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            // 不绑生物识别：仓库在列表/详情里需要随时解密，指纹解锁由 UI 层统一把关。
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nanio_password_field_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_SIZE = 12
        const val TAG_LENGTH_BITS = 128
        const val PREFIX = "v1:"

        val KEY_LOCK = Any()
    }
}
