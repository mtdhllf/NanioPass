package cc.nanio.pass.data

import cc.nanio.pass.data.model.PasswordEntity

/**
 * 密码仓库契约。实现类负责 SQLite 读写与敏感字段加解密。
 * 所有方法均为挂起函数，调用方在 IO 线程执行（仓库内部自行切线程）。
 */
interface PasswordRepository {

    /** 正常条目（deletedAt == null），按 updatedAt 倒序 */
    suspend fun listActive(): List<PasswordEntity>

    /** 最近删除条目，按 deletedAt 倒序 */
    suspend fun listDeleted(): List<PasswordEntity>

    suspend fun getById(id: Long): PasswordEntity?

    /** 插入并返回新 id */
    suspend fun insert(entity: PasswordEntity): Long

    /**
     * 按「账号 + 绑定应用/网站」做 upsert：
     * 已有的同账号条目更新密码（返回 true），否则插入新条目（返回 false）。
     * 用于自动填充保存、凭据服务创建/更新等“同一账号只应有一条”的场景。
     */
    suspend fun upsertByAccount(entity: PasswordEntity): Boolean

    /** 按 id 更新（保留 createdAt） */
    suspend fun update(entity: PasswordEntity)

    /** 软删除：写入 deletedAt = now */
    suspend fun softDelete(id: Long)

    /** 从最近删除恢复 */
    suspend fun restore(id: Long)

    /** 彻底删除 */
    suspend fun purge(id: Long)

    /** 清空最近删除 */
    suspend fun purgeAllDeleted()

    /** 清理超过保留期的最近删除条目，返回删除条数 */
    suspend fun purgeExpired(retentionDays: Int): Int

    /** 更新最近使用时间（复制/填充后调用） */
    suspend fun touchLastUsed(id: Long)

    /** 给指定密码挂载/更新动态令牌 */
    suspend fun attachTotp(
        id: Long,
        secret: String,
        issuer: String?,
        totpAccount: String?,
        algorithm: String = "SHA1",
        digits: Int = 6,
    )

    /** 移除动态令牌 */
    suspend fun detachTotp(id: Long)

    /** 仅测试/开发用：清空数据库 */
    suspend fun clearAll()
}
