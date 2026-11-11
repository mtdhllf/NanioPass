package cc.nanio.pass




import cc.nanio.pass.Graph
import cc.nanio.pass.R
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.lock.LockScreen
import cc.nanio.pass.ui.navigation.AppNav
import cc.nanio.pass.ui.theme.NanioTheme

class MainActivity : FragmentActivity() {

    /**
     * 扫码结果走这里：ZXing 的 ActivityResultContract 在 FragmentActivity 下会因
     * requestCode 超过 16 位崩溃，因此改用固定小 requestCode 直接 startActivityForResult。
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (cc.nanio.pass.ui.scan.ScanLauncher.handleActivityResult(requestCode, resultCode, data)) {
            return
        }
        if (cc.nanio.pass.ui.util.PickerBridge.handleActivityResult(requestCode, resultCode, data)) {
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        if (cc.nanio.pass.ui.util.PermissionBridge.handleActivityResult(requestCode, permissions, grantResults)) {
            return
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 仅 debug 构建：允许通过 adb 注入演示数据，便于自动填充/凭据联调。
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debuggable && intent?.getBooleanExtra("nanio_debug_seed", false) == true) {
            runCatching {
                kotlinx.coroutines.runBlocking {
                    cc.nanio.pass.Graph.repository.insert(
                        cc.nanio.pass.data.model.PasswordEntity(
                            label = "WeChat",
                            account = "alice@example.com",
                            password = "Passw0rd!",
                        ),
                    )
                    cc.nanio.pass.Graph.repository.insert(
                        cc.nanio.pass.data.model.PasswordEntity(
                            label = "Taobao",
                            account = "bob@example.com",
                            password = "Passw0rd!",
                        ),
                    )
                    cc.nanio.pass.Graph.repository.insert(
                        cc.nanio.pass.data.model.PasswordEntity(
                            label = "Mail",
                            account = "weak-a@example.com",
                            password = "12345678",
                        ),
                    )
                    cc.nanio.pass.Graph.repository.insert(
                        cc.nanio.pass.data.model.PasswordEntity(
                            label = "Forum",
                            account = "weak-b@example.com",
                            password = "66668888",
                        ),
                    )
                    cc.nanio.pass.Graph.repository.insert(
                        cc.nanio.pass.data.model.PasswordEntity(
                            label = "GitHub",
                            account = "dev@nanio.cc",
                            password = "Xk9#mQ2vL7pR4tWz",
                            totpSecret = "JBSWY3DPEHPK3PXP",
                            totpIssuer = "GitHub",
                            totpAccount = "dev@nanio.cc",
                        ),
                    )
                    cc.nanio.pass.Graph.repository.insert(
                        cc.nanio.pass.data.model.PasswordEntity(
                            label = "123 Bank",
                            account = "bank@example.com",
                            password = "BankPass123!",
                        ),
                    )
                    cc.nanio.pass.Graph.repository.insert(
                        cc.nanio.pass.data.model.PasswordEntity(
                            label = "NanioPass Demo",
                            account = "bound@example.com",
                            password = "BoundPass123!",
                            packageName = "cc.nanio.autofilltest",
                            appLabel = "NanioPass Demo",
                        ),
                    )
                }
            }
            android.widget.Toast.makeText(this, Graph.appContext.getString(R.string.s_demo_data_inserted), android.widget.Toast.LENGTH_SHORT).show()
            intent.removeExtra("nanio_debug_seed")
        }
        enableEdgeToEdge()
        setContent {
            NanioTheme {
                val vm: AppViewModel = viewModel()
                var locked by remember { mutableStateOf(vm.biometricEnabled) }
                var pausedAt by remember { mutableLongStateOf(0L) }

                // 自动锁定：从后台返回时按设置的超时时间上锁
                DisposableEffect(vm.biometricEnabled, vm.autoLockMinutes) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_PAUSE -> pausedAt = System.currentTimeMillis()
                            Lifecycle.Event.ON_RESUME -> {
                                if (pausedAt > 0L && vm.biometricEnabled) {
                                    val timeoutMs = if (vm.autoLockMinutes <= 0) 0L
                                    else vm.autoLockMinutes * 60_000L
                                    if (System.currentTimeMillis() - pausedAt >= timeoutMs) {
                                        locked = true
                                    }
                                }
                            }
                            else -> Unit
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AppNav(vm = vm)
                    if (locked) {
                        LockScreen(activity = this@MainActivity, onUnlock = { locked = false })
                    }
                }
            }
        }
    }
}
