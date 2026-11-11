package cc.nanio.pass.data




import cc.nanio.pass.Graph
import cc.nanio.pass.R
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [AppRepository] 的实现：PackageManager 查询可启动应用 + 名称/图标读取（带内存缓存）。
 *
 * - Android 11+ 包可见性：manifest 已声明 `QUERY_ALL_PACKAGES`，因此可以读到全量应用。
 * - 应用已卸载的天然回退信号：`getApplicationInfo` 抛 `NameNotFoundException`（或不可见），
 *   此时 [loadLabel]/[loadIcon] 返回 null，UI 回退到 `PasswordEntity.initial` 首字头像。
 * - 图标走 64 项 [LruCache]，避免列表滚动时反复解码 drawable。
 */
class AppRepositoryImpl(context: Context) : AppRepository {

    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val iconCache = LruCache<String, Drawable>(ICON_CACHE_SIZE)

    override suspend fun loadLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val resolved = try {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
            packageManager.queryIntentActivities(intent, 0).orEmpty()
        } catch (e: Exception) {
            // 某些 ROM 在权限缺失时会抛 SecurityException，按无应用处理，由 UI 展示空态。
            emptyList()
        }

        val ownPackage = appContext.packageName
        val seenPackages = HashSet<String>(resolved.size)
        val apps = ArrayList<AppInfo>(resolved.size)
        for (resolveInfo in resolved) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val packageName = activityInfo.packageName ?: continue
            // 排除自身，避免绑定自己这种无意义选项。
            if (packageName == ownPackage) continue
            // 同一包可能有多个 LAUNCHER Activity，按包名去重（保留 query 返回的第一个）。
            if (!seenPackages.add(packageName)) continue
            val applicationInfo = activityInfo.applicationInfo ?: continue

            val label = try {
                applicationInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
            } catch (e: Exception) {
                ""
            }
            val isSystem = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            apps.add(
                AppInfo(
                    packageName = packageName,
                    label = label.ifEmpty { packageName },
                    isSystem = isSystem,
                ),
            )
        }

        // 中文排序：Collator（拼音）优于 String.compareTo；同名再按包名稳定排序。
        val collator = Collator.getInstance(Locale.CHINA)
        val comparator = object : Comparator<AppInfo> {
            override fun compare(a: AppInfo, b: AppInfo): Int {
                val byLabel = collator.compare(a.label, b.label)
                return if (byLabel != 0) byLabel else a.packageName.compareTo(b.packageName)
            }
        }
        apps.sortWith(comparator)
        apps
    }

    override fun loadLabel(packageName: String): String? = try {
        packageManager.getApplicationInfo(packageName, 0)
            .loadLabel(packageManager)
            ?.toString()
            ?.trim()
    } catch (e: PackageManager.NameNotFoundException) {
        // 已卸载 → 调用方回退到 appLabel 快照 / 首字头像。
        null
    } catch (e: Exception) {
        // SecurityException（包不可见）等，一并视为读不到。
        null
    }

    override fun loadIcon(packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        val drawable: Drawable? = try {
            packageManager.getApplicationInfo(packageName, 0).loadIcon(packageManager)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
        if (drawable == null) return null
        iconCache.put(packageName, drawable)
        return drawable
    }

    private companion object {
        const val ICON_CACHE_SIZE = 64
    }
}
