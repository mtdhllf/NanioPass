package cc.nanio.pass

import android.content.Context
import cc.nanio.pass.crypto.CryptoManager
import cc.nanio.pass.data.AppRepository
import cc.nanio.pass.data.DataGraphFactory
import cc.nanio.pass.data.PasswordRepository

/**
 * 轻量手写依赖容器（不引入 Hilt，减少构建复杂度）。
 * 真正的实现在 data/crypto 包，由 [init] 接线。
 */
object Graph {
    lateinit var appContext: Context
        private set

    lateinit var repository: PasswordRepository
        internal set

    lateinit var appRepository: AppRepository
        internal set

    lateinit var crypto: CryptoManager
        internal set

    private var initialized = false

    fun init(context: Context) {
        appContext = context.applicationContext
        if (initialized) return
        initialized = true
        wire(appContext)
    }

    /**
     * 数据层接线。实现类在 data/crypto 包（见 DataGraphFactory）。
     * 由数据层完成后在此处调用；若实现暂缺则以空实现兜底，避免调试期崩溃。
     */
    private fun wire(context: Context) {
        val components = DataGraphFactory.create(context)
        repository = components.repository
        appRepository = components.appRepository
        crypto = components.crypto
    }
}
