package cc.nanio.pass.data



import cc.nanio.pass.R
import cc.nanio.pass.Graph
import java.util.Base64
import cc.nanio.pass.data.model.PasswordEntity
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 加密导入/导出（用于本地或跨设备手动迁移）。
 *
 * 格式（JSON）：
 * ```
 * { "format":"nanio-export", "version":1, "kdf":"PBKDF2WithHmacSHA256",
 *   "iterations":120000, "salt":b64, "iv":b64, "ciphertext":b64 }
 * ```
 * 明文是条目数组 JSON；口令不落盘。AES-256-GCM，口令经 PBKDF2-HMAC-SHA256 派生。
 */
object ExportManager {

    const val FORMAT = "nanio-export"
    private const val VERSION = 1
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val MAX_ENTRIES = 10_000

    class ExportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

    /** 导入结果：成功写入条数与因重复被跳过的条数。 */
    data class ImportSummary(val imported: Int, val skipped: Int)

    fun export(passwords: List<PasswordEntity>, passphrase: String): String {
        require(passphrase.length >= 6) { Graph.appContext.getString(R.string.s_export_password_must_be_at_least_6_characters) }
        val arr = JSONArray()
        passwords.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("label", p.label)
                    put("account", p.account)
                    put("password", p.password)
                    put("domain", p.domain ?: "")
                    put("note", p.note ?: "")
                    put("packageName", p.packageName ?: "")
                    put("appLabel", p.appLabel ?: "")
                    put("totpSecret", p.totpSecret ?: "")
                    put("totpIssuer", p.totpIssuer ?: "")
                    put("totpAccount", p.totpAccount ?: "")
                    put("totpAlgorithm", p.totpAlgorithm)
                    put("totpDigits", p.totpDigits)
                },
            )
        }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val key = deriveKey(passphrase, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(arr.toString().toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("kdf", KDF)
            put("exportedAt", System.currentTimeMillis())
            put("iterations", ITERATIONS)
            put("salt", b64(salt))
            put("iv", b64(iv))
            put("ciphertext", b64(ciphertext))
        }.toString(2)
    }

    fun import(bytes: ByteArray, passphrase: String): List<PasswordEntity> {
        val root = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw ExportException(Graph.appContext.getString(R.string.s_backup_file_is_not_valid_json_text), e)
        }
        if (root.optString("format") != FORMAT) throw ExportException(Graph.appContext.getString(R.string.s_not_a_naniopass_backup_file))
        if (root.optInt("version", 0) != VERSION) throw ExportException(Graph.appContext.getString(R.string.s_unsupported_backup_file_version_only_v_s_is_supp, VERSION))
        if (root.optString("kdf", "") != KDF) throw ExportException(Graph.appContext.getString(R.string.s_unsupported_key_derivation_algorithm))
        val iterations = root.optInt("iterations", 0)
        if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) throw ExportException(Graph.appContext.getString(R.string.s_invalid_backup_file_parameter_iteration_count))
        val salt = runCatching { unb64(root.optString("salt")) }.getOrElse { throw ExportException(Graph.appContext.getString(R.string.s_invalid_backup_file_parameter_salt)) }
        val iv = runCatching { unb64(root.optString("iv")) }.getOrElse { throw ExportException(Graph.appContext.getString(R.string.s_invalid_backup_file_parameter_iv)) }
        val ciphertext = runCatching { unb64(root.optString("ciphertext")) }.getOrElse { throw ExportException(Graph.appContext.getString(R.string.s_backup_file_contents_are_corrupted)) }
        if (salt.size !in 8..64) throw ExportException(Graph.appContext.getString(R.string.s_invalid_backup_file_parameter_salt_length))
        if (iv.size != IV_BYTES) throw ExportException(Graph.appContext.getString(R.string.s_invalid_backup_file_parameter_iv_length))
        if (ciphertext.isEmpty()) throw ExportException(Graph.appContext.getString(R.string.s_backup_file_is_empty))

        val plain = try {
            val key = deriveKey(passphrase, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            throw ExportException(Graph.appContext.getString(R.string.s_incorrect_backup_passphrase_or_the_file_has_been), e)
        } catch (e: Exception) {
            throw ExportException(Graph.appContext.getString(R.string.s_backup_file_is_corrupted_or_the_passphrase_is_in), e)
        }

        val arr = try {
            JSONArray(plain)
        } catch (e: Exception) {
            throw ExportException(Graph.appContext.getString(R.string.s_backup_data_is_corrupted), e)
        }
        if (arr.length() > MAX_ENTRIES) throw ExportException(Graph.appContext.getString(R.string.s_too_many_backup_entries_limit_s, MAX_ENTRIES))

        val out = ArrayList<PasswordEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = try {
                arr.getJSONObject(i)
            } catch (e: Exception) {
                throw ExportException(Graph.appContext.getString(R.string.s_backup_entry_s_has_an_invalid_format, i + 1), e)
            }
            val label = o.optString("label").trim()
            val account = o.optString("account").trim()
            val password = o.optString("password")
            if (label.isEmpty() && account.isEmpty()) {
                throw ExportException(Graph.appContext.getString(R.string.s_backup_entry_s_is_missing_its_name_and_account, i + 1))
            }
            val digits = o.optInt("totpDigits", 6).let { if (it in 6..8) it else 6 }
            val algorithm = o.optString("totpAlgorithm").uppercase()
                .takeIf { it == "SHA1" || it == "SHA256" || it == "SHA512" } ?: "SHA1"
            out.add(
                PasswordEntity(
                    label = label,
                    account = account,
                    password = password,
                    domain = o.optString("domain").ifBlank { null },
                    note = o.optString("note").ifBlank { null },
                    packageName = o.optString("packageName").ifBlank { null },
                    appLabel = o.optString("appLabel").ifBlank { null },
                    totpSecret = o.optString("totpSecret").ifBlank { null },
                    totpIssuer = o.optString("totpIssuer").ifBlank { null },
                    totpAccount = o.optString("totpAccount").ifBlank { null },
                    totpAlgorithm = algorithm,
                    totpDigits = digits,
                ),
            )
        }
        return out
    }

    private fun deriveKey(passphrase: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    private fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
}
