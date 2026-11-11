package cc.nanio.pass.ui.util

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.fragment.app.FragmentActivity

/**
 * 文件选择桥（导出/导入备份用）。
 *
 * 与 [cc.nanio.pass.ui.scan.ScanLauncher] 同因：主 Activity 是 FragmentActivity，
 * androidx.activity 的 ActivityResultRegistry 生成的 requestCode 超过 16 位会被
 * FragmentActivity.startActivityForResult 拒绝，所以这里同样用固定小 requestCode
 * 直接 startActivityForResult，并在 Activity.onActivityResult 里转发结果。
 */
object PickerBridge {

    const val REQ_OPEN = 0x5CA2
    const val REQ_CREATE = 0x5CA3

    private var openCallback: ((Uri?) -> Unit)? = null
    private var createCallback: ((Uri?) -> Unit)? = null

    fun open(activity: FragmentActivity, mime: String, onResult: (Uri?) -> Unit) {
        openCallback = onResult
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
        }
        activity.startActivityForResult(intent, REQ_OPEN)
    }

    fun create(activity: FragmentActivity, fileName: String, mime: String, onResult: (Uri?) -> Unit) {
        createCallback = onResult
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        activity.startActivityForResult(intent, REQ_CREATE)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val uri = if (resultCode == Activity.RESULT_OK) data?.data else null
        return when (requestCode) {
            REQ_OPEN -> {
                val cb = openCallback; openCallback = null; cb?.invoke(uri); true
            }
            REQ_CREATE -> {
                val cb = createCallback; createCallback = null; cb?.invoke(uri); true
            }
            else -> false
        }
    }

    fun clear() {
        openCallback = null
        createCallback = null
    }
}
