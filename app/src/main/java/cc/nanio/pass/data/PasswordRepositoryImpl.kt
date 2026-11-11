package cc.nanio.pass.data




import cc.nanio.pass.Graph
import cc.nanio.pass.R
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import cc.nanio.pass.crypto.CryptoManager
import cc.nanio.pass.data.model.PasswordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PasswordRepository] 的 SQLite 实现。
 *
 * 职责：
 * 1. 所有数据库访问切到 [Dispatchers.IO]（契约是 suspend，调用方不必自己切线程）。
 * 2. 敏感字段落库前加密、读出后解密：
 *    - `password` 必加密；
 *    - `totp_secret` 非 null 时加密，null 保持 null（`hasTotp` 语义不受影响）。
 *    加解密失败由 [cc.nanio.pass.crypto.CryptoManager] 抛 `CryptoException`，本层不捕获，
 *    让调用方明确感知数据读/写失败，避免静默写坏数据。
 * 3. 软删除（`deleted_at`）与物理删除（DELETE），最近删除保留期清理。
 *
 * 说明：契约里的写操作（softDelete/restore/purge/purgeAllDeleted/touchLastUsed/attachTotp/
 * detachTotp/clearAll）全部是单条 SQL，SQLite 单语句本身即原子，因此不需要显式事务；
 * 后续若出现多表/多语句写入，应使用 `beginTransaction()/setTransactionSuccessful()/endTransaction()` 包裹。
 */
class PasswordRepositoryImpl(
    context: Context,
    private val crypto: CryptoManager,
    /** 允许注入共享实例（[DataGraphFactory] 会传入同一个库对象）；单独使用时自动新建。 */
    private val database: PasswordDatabase = PasswordDatabase(context),
) : PasswordRepository {

    override suspend fun listActive(): List<PasswordEntity> = withContext(Dispatchers.IO) {
        queryEntities(
            selection = "$COL_DELETED_AT IS NULL",
            orderBy = "$COL_UPDATED_AT DESC",
        )
    }

    override suspend fun listDeleted(): List<PasswordEntity> = withContext(Dispatchers.IO) {
        queryEntities(
            selection = "$COL_DELETED_AT IS NOT NULL",
            orderBy = "$COL_DELETED_AT DESC",
        )
    }

    override suspend fun getById(id: Long): PasswordEntity? = withContext(Dispatchers.IO) {
        queryEntities(
            selection = "$COL_ID = ?",
            selectionArgs = arrayOf(id.toString()),
            limit = "1",
        ).firstOrNull()
    }

    override suspend fun insert(entity: PasswordEntity): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // 契约：忽略传入 id；createdAt/updatedAt 用当前时间，但显式传入（>0）的值保留（导入场景）。
        val values = entity.toInsertValues(now)
        database.writable().insertOrThrow(TABLE_PASSWORD_RECORD, null, values)
    }

    override suspend fun upsertByAccount(entity: PasswordEntity): Boolean = withContext(Dispatchers.IO) {
        val accountKey = entity.account.trim().lowercase()
        if (accountKey.isEmpty()) {
            insert(entity)
            return@withContext false
        }
        val candidates = queryEntities(
            selection = "$COL_DELETED_AT IS NULL",
            orderBy = "$COL_UPDATED_AT DESC",
        )
        val existing = candidates.firstOrNull { it ->
            if (it.account.trim().lowercase() != accountKey) return@firstOrNull false
            val samePackage = !entity.packageName.isNullOrEmpty() &&
                entity.packageName == it.packageName
            val bothUnbound = entity.packageName.isNullOrEmpty() && it.packageName.isNullOrEmpty()
            val sameDomain = bothUnbound &&
                !entity.domain.isNullOrEmpty() &&
                !it.domain.isNullOrEmpty() &&
                entity.domain.trim().equals(it.domain.trim(), ignoreCase = true)
            val sameLabel = bothUnbound &&
                entity.domain.isNullOrEmpty() &&
                it.domain.isNullOrEmpty() &&
                entity.label.trim().equals(it.label.trim(), ignoreCase = true)
            samePackage || sameDomain || sameLabel
        }
        if (existing == null) {
            insert(entity)
            false
        } else {
            update(
                existing.copy(
                    password = entity.password,
                    appLabel = entity.appLabel ?: existing.appLabel,
                    packageName = entity.packageName ?: existing.packageName,
                    note = existing.note ?: entity.note,
                ),
            )
            true
        }
    }

    override suspend fun update(entity: PasswordEntity) {
        withContext(Dispatchers.IO) {
            // 全字段覆盖，保留 createdAt，updatedAt 强制刷新为 now。
            val values = entity.toUpdateValues(System.currentTimeMillis())
            database.writable().update(
                TABLE_PASSWORD_RECORD,
                values,
                "$COL_ID = ?",
                arrayOf(entity.id.toString()),
            )
        }
    }

    override suspend fun softDelete(id: Long) {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put(COL_DELETED_AT, now)
                put(COL_UPDATED_AT, now)
            }
            database.writable().update(
                TABLE_PASSWORD_RECORD,
                values,
                "$COL_ID = ?",
                arrayOf(id.toString()),
            )
        }
    }

    override suspend fun restore(id: Long) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                putNull(COL_DELETED_AT)
                put(COL_UPDATED_AT, System.currentTimeMillis())
            }
            database.writable().update(
                TABLE_PASSWORD_RECORD,
                values,
                "$COL_ID = ?",
                arrayOf(id.toString()),
            )
        }
    }

    override suspend fun purge(id: Long) {
        withContext(Dispatchers.IO) {
            database.writable().delete(
                TABLE_PASSWORD_RECORD,
                "$COL_ID = ?",
                arrayOf(id.toString()),
            )
        }
    }

    override suspend fun purgeAllDeleted() {
        withContext(Dispatchers.IO) {
            database.writable().delete(TABLE_PASSWORD_RECORD, "$COL_DELETED_AT IS NOT NULL", null)
        }
    }

    override suspend fun purgeExpired(retentionDays: Int): Int = withContext(Dispatchers.IO) {
        val cutoff = purgeCutoffMillis(System.currentTimeMillis(), retentionDays)
        database.writable().delete(
            TABLE_PASSWORD_RECORD,
            "$COL_DELETED_AT IS NOT NULL AND $COL_DELETED_AT < ?",
            arrayOf(cutoff.toString()),
        )
    }

    override suspend fun touchLastUsed(id: Long) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(COL_LAST_USED_AT, System.currentTimeMillis())
            }
            database.writable().update(
                TABLE_PASSWORD_RECORD,
                values,
                "$COL_ID = ?",
                arrayOf(id.toString()),
            )
        }
    }

    override suspend fun attachTotp(
        id: Long,
        secret: String,
        issuer: String?,
        totpAccount: String?,
        algorithm: String,
        digits: Int,
    ) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(COL_TOTP_SECRET, crypto.encrypt(secret))
                put(COL_TOTP_ISSUER, issuer)
                put(COL_TOTP_ACCOUNT, totpAccount)
                put(COL_TOTP_ALGORITHM, algorithm)
                put(COL_TOTP_DIGITS, digits)
                put(COL_UPDATED_AT, System.currentTimeMillis())
            }
            database.writable().update(
                TABLE_PASSWORD_RECORD,
                values,
                "$COL_ID = ?",
                arrayOf(id.toString()),
            )
        }
    }

    override suspend fun detachTotp(id: Long) {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                putNull(COL_TOTP_SECRET)
                putNull(COL_TOTP_ISSUER)
                putNull(COL_TOTP_ACCOUNT)
                // algorithm/digits 是 NOT NULL DEFAULT，无法置 NULL，归一为建表默认值。
                put(COL_TOTP_ALGORITHM, DEFAULT_TOTP_ALGORITHM)
                put(COL_TOTP_DIGITS, DEFAULT_TOTP_DIGITS)
                put(COL_UPDATED_AT, System.currentTimeMillis())
            }
            database.writable().update(
                TABLE_PASSWORD_RECORD,
                values,
                "$COL_ID = ?",
                arrayOf(id.toString()),
            )
        }
    }

    override suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            database.writable().delete(TABLE_PASSWORD_RECORD, null, null)
        }
    }

    // -----------------------------------------------------------------------
    // 内部工具
    // -----------------------------------------------------------------------

    private fun queryEntities(
        selection: String?,
        selectionArgs: Array<String>? = null,
        orderBy: String? = null,
        limit: String? = null,
    ): List<PasswordEntity> {
        val cursor = database.readable().query(
            TABLE_PASSWORD_RECORD,
            ALL_COLUMNS,
            selection,
            selectionArgs,
            null,
            null,
            orderBy,
            limit,
        )
        return cursor.use { c ->
            val result = ArrayList<PasswordEntity>()
            while (c.moveToNext()) {
                result.add(c.toEntity())
            }
            result
        }
    }

    private fun Cursor.toEntity(): PasswordEntity {
        val encryptedPassword: String = getString(getColumnIndexOrThrow(COL_PASSWORD))
        val encryptedTotpSecret = stringOrNull(COL_TOTP_SECRET)
        return PasswordEntity(
            id = getLong(getColumnIndexOrThrow(COL_ID)),
            label = getString(getColumnIndexOrThrow(COL_LABEL)),
            account = getString(getColumnIndexOrThrow(COL_ACCOUNT)),
            password = crypto.decrypt(encryptedPassword),
            domain = stringOrNull(COL_DOMAIN),
            note = stringOrNull(COL_NOTE),
            packageName = stringOrNull(COL_PACKAGE_NAME),
            appLabel = stringOrNull(COL_APP_LABEL),
            totpSecret = encryptedTotpSecret?.let { crypto.decrypt(it) },
            totpIssuer = stringOrNull(COL_TOTP_ISSUER),
            totpAccount = stringOrNull(COL_TOTP_ACCOUNT),
            totpAlgorithm = stringOrNull(COL_TOTP_ALGORITHM) ?: DEFAULT_TOTP_ALGORITHM,
            totpDigits = getInt(getColumnIndexOrThrow(COL_TOTP_DIGITS)),
            createdAt = getLong(getColumnIndexOrThrow(COL_CREATED_AT)),
            updatedAt = getLong(getColumnIndexOrThrow(COL_UPDATED_AT)),
            lastUsedAt = getLong(getColumnIndexOrThrow(COL_LAST_USED_AT)),
            deletedAt = longOrNull(COL_DELETED_AT),
        )
    }

    private fun PasswordEntity.toInsertValues(now: Long): ContentValues {
        val createdAtValue = if (this.createdAt > 0L) this.createdAt else now
        val updatedAtValue = if (this.updatedAt > 0L) this.updatedAt else now
        return ContentValues().apply {
            put(COL_LABEL, label)
            put(COL_ACCOUNT, account)
            put(COL_PASSWORD, crypto.encrypt(password))
            put(COL_DOMAIN, domain)
            put(COL_NOTE, note)
            put(COL_PACKAGE_NAME, packageName)
            put(COL_APP_LABEL, appLabel)
            put(COL_TOTP_SECRET, totpSecret?.let { crypto.encrypt(it) })
            put(COL_TOTP_ISSUER, totpIssuer)
            put(COL_TOTP_ACCOUNT, totpAccount)
            put(COL_TOTP_ALGORITHM, totpAlgorithm)
            put(COL_TOTP_DIGITS, totpDigits)
            put(COL_CREATED_AT, createdAtValue)
            put(COL_UPDATED_AT, updatedAtValue)
            put(COL_LAST_USED_AT, lastUsedAt)
            put(COL_DELETED_AT, deletedAt)
        }
    }

    private fun PasswordEntity.toUpdateValues(now: Long): ContentValues = ContentValues().apply {
        put(COL_LABEL, label)
        put(COL_ACCOUNT, account)
        put(COL_PASSWORD, crypto.encrypt(password))
        put(COL_DOMAIN, domain)
        put(COL_NOTE, note)
        put(COL_PACKAGE_NAME, packageName)
        put(COL_APP_LABEL, appLabel)
        put(COL_TOTP_SECRET, totpSecret?.let { crypto.encrypt(it) })
        put(COL_TOTP_ISSUER, totpIssuer)
        put(COL_TOTP_ACCOUNT, totpAccount)
        put(COL_TOTP_ALGORITHM, totpAlgorithm)
        put(COL_TOTP_DIGITS, totpDigits)
        put(COL_LAST_USED_AT, lastUsedAt)
        put(COL_DELETED_AT, deletedAt)
        put(COL_UPDATED_AT, now)
        // COL_CREATED_AT 不写入：更新永远保留创建时间。
    }

    private fun Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private companion object {
        const val DEFAULT_TOTP_ALGORITHM = "SHA1"
        const val DEFAULT_TOTP_DIGITS = 6
        const val MILLIS_PER_DAY = 86_400_000L

        val ALL_COLUMNS = arrayOf(
            COL_ID,
            COL_LABEL,
            COL_ACCOUNT,
            COL_PASSWORD,
            COL_DOMAIN,
            COL_NOTE,
            COL_PACKAGE_NAME,
            COL_APP_LABEL,
            COL_TOTP_SECRET,
            COL_TOTP_ISSUER,
            COL_TOTP_ACCOUNT,
            COL_TOTP_ALGORITHM,
            COL_TOTP_DIGITS,
            COL_CREATED_AT,
            COL_UPDATED_AT,
            COL_LAST_USED_AT,
            COL_DELETED_AT,
        )

        /** 保留期截止时间：`now - retentionDays * 24h`；负数按 0 天处理（立即过期）。 */
        fun purgeCutoffMillis(nowMillis: Long, retentionDays: Int): Long =
            nowMillis - retentionDays.coerceAtLeast(0).toLong() * MILLIS_PER_DAY
    }
}
