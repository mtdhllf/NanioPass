package cc.nanio.pass.data

import android.content.Context
import cc.nanio.pass.crypto.CryptoManager
import cc.nanio.pass.crypto.CryptoManagerImpl

/**
 * 数据层组件集合。Lead 在 [cc.nanio.pass.Graph.init] 里持有它，供 UI/ViewModel 取用。
 */
data class DataComponents(
    val database: PasswordDatabase,
    val repository: PasswordRepository,
    val appRepository: AppRepository,
    val crypto: CryptoManager,
)

/**
 * 数据层工厂：一次创建数据库、仓库、应用信息仓库与加密器，并让仓库复用同一个
 * [PasswordDatabase] 实例（避免同一进程打开两个 SQLiteOpenHelper）。
 *
 * 用法（Lead 接线）：
 * ```
 * fun init(context: Context) {
 *     appContext = context.applicationContext
 *     data = DataGraphFactory.create(appContext)
 * }
 * val repository: PasswordRepository get() = data.repository
 * ```
 */
object DataGraphFactory {

    fun create(context: Context): DataComponents {
        val appContext = context.applicationContext
        val crypto: CryptoManager = CryptoManagerImpl()
        val database = PasswordDatabase(appContext)
        val repository: PasswordRepository = PasswordRepositoryImpl(appContext, crypto, database)
        val appRepository: AppRepository = AppRepositoryImpl(appContext)
        return DataComponents(
            database = database,
            repository = repository,
            appRepository = appRepository,
            crypto = crypto,
        )
    }
}
