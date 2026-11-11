package cc.nanio.pass.data



import cc.nanio.pass.R
import cc.nanio.pass.Graph
/**
 * 已安装应用信息（用于绑定应用选择器）。
 * 图标不放进实体，按包名惰性加载，见 AppRepository。
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
)

/**
 * 已安装应用仓库：列表 + 名称/图标读取 + 卸载后的首字母头像回退。
 */
interface AppRepository {

    /** 有启动入口的已安装应用，按名称排序 */
    suspend fun loadLaunchableApps(): List<AppInfo>

    /** 读取应用名；应用不存在（已卸载）返回 null */
    fun loadLabel(packageName: String): String?

    /** 读取应用图标；应用不存在或取不到返回 null（调用方回退到首字母头像） */
    fun loadIcon(packageName: String): android.graphics.drawable.Drawable?
}
