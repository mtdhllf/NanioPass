package cc.nanio.pass

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager

/**
 * 应用级单例入口。数据库 / 加密 / 仓库由 [Graph] 惰性初始化。
 *
 * 所有 Activity 统一开启 [WindowManager.LayoutParams.FLAG_SECURE]：
 * 禁止系统截屏、录屏以及最近任务缩略图，避免密码/令牌内容外泄。
 */
class NanioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Graph.init(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) = Unit

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    companion object {
        lateinit var instance: NanioApp
            private set
    }
}
