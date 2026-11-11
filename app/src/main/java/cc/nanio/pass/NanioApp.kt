package cc.nanio.pass

import android.app.Application

/**
 * 应用级单例入口。数据库 / 加密 / 仓库由 [Graph] 惰性初始化。
 */
class NanioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Graph.init(this)
    }

    companion object {
        lateinit var instance: NanioApp
            private set
    }
}
