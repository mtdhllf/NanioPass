package cc.nanio.pass.ui.util

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.fragment.app.FragmentActivity

/**
 * 运行时权限请求桥。
 *
 * 与 [ScanLauncher]/[PickerBridge] 同因：`FragmentActivity` 对
 * `startActivityForResult` 与 `requestPermissions` 都会校验 requestCode 必须落在低 16 位，
 * 而 androidx.activity 的 ActivityResultRegistry 生成的是 32 位码，会抛
 * "Can only use lower 16 bits for requestCode"。
 * 因此这里用固定小 requestCode 直接调用 requestPermissions，并在
 * Activity.onRequestPermissionsResult 中转发。
 */
object PermissionBridge {

    const val REQUEST_CODE = 0x5CA4

    private var pendingCallback: ((Boolean) -> Unit)? = null
    private var pendingPermission: String? = null

    fun request(activity: FragmentActivity, permission: String, onResult: (Boolean) -> Unit) {
        pendingCallback = onResult
        pendingPermission = permission
        activity.requestPermissions(arrayOf(permission), REQUEST_CODE)
    }

    fun handleActivityResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ): Boolean {
        if (requestCode != REQUEST_CODE) return false
        val callback = pendingCallback
        val permission = pendingPermission
        pendingCallback = null
        pendingPermission = null
        val index = permissions.indexOfFirst { it == permission }
        val granted = index >= 0 && grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
        callback?.invoke(granted)
        return true
    }

    fun clear() {
        pendingCallback = null
        pendingPermission = null
    }
}
