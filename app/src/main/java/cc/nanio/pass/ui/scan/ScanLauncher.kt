package cc.nanio.pass.ui.scan

import android.app.Activity
import android.content.Intent
import androidx.fragment.app.FragmentActivity

/**
 * 扫码启动器。
 *
 * 为什么不用 `rememberLauncherForActivityResult(ScanContract())`：
 * 主 Activity 是 `FragmentActivity`（生物识别 `BiometricPrompt` 要求），
 * 而 FragmentActivity 会校验 `startActivityForResult` 的 requestCode 必须落在低 16 位；
 * androidx.activity 的 ActivityResultRegistry 生成 32 位码，运行时会抛
 * `IllegalArgumentException: Can only use lower 16 bits for requestCode`（部分 ROM 已复现）。
 *
 * 因此这里用固定小 requestCode 直接 `startActivityForResult`，结果通过
 * `Activity.onActivityResult` 转发；扫描页为竖屏 + 正方形取景框（NanioCaptureActivity）。
 */
object ScanLauncher {

    const val REQUEST_CODE = 0x5CA1

    private var pendingCallback: ((String?) -> Unit)? = null

    fun launch(
        activity: FragmentActivity,
        onResult: (String?) -> Unit,
    ) {
        pendingCallback = onResult
        activity.startActivityForResult(Intent(activity, NanioCaptureActivity::class.java), REQUEST_CODE)
    }

    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) return false
        val callback = pendingCallback
        pendingCallback = null
        val contents = if (resultCode == Activity.RESULT_OK) {
            data?.getStringExtra(NanioCaptureActivity.EXTRA_SCAN_RESULT)
        } else {
            null
        }
        callback?.invoke(contents)
        return true
    }

    fun clear() {
        pendingCallback = null
    }
}
