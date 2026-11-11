package cc.nanio.pass.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

// ---------------------------------------------------------------------------
// 表结构常量：仓库层与测试共用。均为包内可见，避免污染对外 API。
// ---------------------------------------------------------------------------

internal const val DATABASE_NAME = "nanio_password.db"
internal const val DATABASE_VERSION = 1

internal const val TABLE_PASSWORD_RECORD = "password_record"
internal const val INDEX_PASSWORD_DELETED_AT = "idx_pwd_deleted_at"

internal const val COL_ID = "id"
internal const val COL_LABEL = "label"
internal const val COL_ACCOUNT = "account"
internal const val COL_PASSWORD = "password"
internal const val COL_DOMAIN = "domain"
internal const val COL_NOTE = "note"
internal const val COL_PACKAGE_NAME = "package_name"
internal const val COL_APP_LABEL = "app_label"
internal const val COL_TOTP_SECRET = "totp_secret"
internal const val COL_TOTP_ISSUER = "totp_issuer"
internal const val COL_TOTP_ACCOUNT = "totp_account"
internal const val COL_TOTP_ALGORITHM = "totp_algorithm"
internal const val COL_TOTP_DIGITS = "totp_digits"
internal const val COL_CREATED_AT = "created_at"
internal const val COL_UPDATED_AT = "updated_at"
internal const val COL_LAST_USED_AT = "last_used_at"
internal const val COL_DELETED_AT = "deleted_at"

/**
 * 本地密码库（手写 SQLite，不引入 Room/KSP）。
 *
 * 库名 `nanio_password.db`，当前版本 1。主表 [TABLE_PASSWORD_RECORD]：
 * 一行 = 一条密码记录，TOTP 的 5 个 `totp_*` 列内联在同一条记录上。
 *
 * 注意：`password` 与 `totp_secret` 两列存的是 [cc.nanio.pass.crypto.CryptoManager]
 * 加密后的字符串（`"v1:"` 前缀），加解密只在仓库层做，本类不感知。
 */
class PasswordDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {

    init {
        // 必须在使用数据库前设置；WAL 让 UI 读与后台写互不阻塞。
        setWriteAheadLoggingEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PASSWORD_RECORD (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_LABEL TEXT NOT NULL,
                $COL_ACCOUNT TEXT NOT NULL,
                $COL_PASSWORD TEXT NOT NULL,
                $COL_DOMAIN TEXT,
                $COL_NOTE TEXT,
                $COL_PACKAGE_NAME TEXT,
                $COL_APP_LABEL TEXT,
                $COL_TOTP_SECRET TEXT,
                $COL_TOTP_ISSUER TEXT,
                $COL_TOTP_ACCOUNT TEXT,
                $COL_TOTP_ALGORITHM TEXT NOT NULL DEFAULT 'SHA1',
                $COL_TOTP_DIGITS INTEGER NOT NULL DEFAULT 6,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                $COL_LAST_USED_AT INTEGER NOT NULL DEFAULT -1,
                $COL_DELETED_AT INTEGER
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS $INDEX_PASSWORD_DELETED_AT " +
                "ON $TABLE_PASSWORD_RECORD ($COL_DELETED_AT)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 版本 1 是首个版本，暂无迁移。后续版本在此按 oldVersion 递增迁移，
        // 只允许 ALTER TABLE ADD COLUMN / 数据搬运，禁止 DROP 掉用户数据。
    }

    /** 可写库（SQLiteOpenHelper 内部有缓存，重复调用开销可忽略）。 */
    fun writable(): SQLiteDatabase = writableDatabase

    /** 只读库（WAL 下读不阻塞写）。 */
    fun readable(): SQLiteDatabase = readableDatabase
}
