package cc.nanio.pass.ui.util

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cc.nanio.pass.Graph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 按包名加载应用图标；应用不存在（已卸载）返回 null，由调用方回退首字母头像。
 * 能取到 ApplicationInfo 就用应用图标，否则绘制首字母头像。
 */
@Composable
fun rememberAppIcon(packageName: String?): Drawable? {
    var icon by remember(packageName) { mutableStateOf<Drawable?>(null) }
    LaunchedEffect(packageName) {
        icon = if (packageName.isNullOrEmpty()) {
            null
        } else {
            runCatching {
                withContext(Dispatchers.IO) { Graph.appRepository.loadIcon(packageName) }
            }.getOrNull()
        }
    }
    return icon
}
