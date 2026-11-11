package cc.nanio.pass.credential



import cc.nanio.pass.R
import android.content.Intent
import android.os.Bundle
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CreatePasswordResponse
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderCreateCredentialRequest
import androidx.fragment.app.FragmentActivity
import cc.nanio.pass.Graph
import cc.nanio.pass.data.model.PasswordEntity
import cc.nanio.pass.ui.components.AppIconAvatar
import cc.nanio.pass.ui.components.NanioCard
import cc.nanio.pass.ui.components.NanioTextField
import cc.nanio.pass.ui.components.TopBar
import cc.nanio.pass.ui.theme.NanioTheme
import kotlinx.coroutines.runBlocking

/**
 * 凭据服务「创建/更新凭据」页面：调用方 App 通过 CredentialManager.createCredential 拉起，
 * 预填账号/密码，确认后写入 NanioPass 并回传 [CreatePasswordResponse]。
 * 不区分“新创建/更新”，界面只标明来源应用。
 */
class CredentialCreateActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val request = runCatching {
            PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        }.getOrNull()
        val debuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // 凭据请求里带有调用方包名；直接调试启动时允许通过 intent extra 指定，便于预览真实应用名/图标
        val callingPackage = request?.callingAppInfo?.packageName
            ?: if (debuggable) intent.getStringExtra("nanio_debug_package") else null
        val passwordRequest = request?.callingRequest as? CreatePasswordRequest
        val appLabel = callingPackage?.let { pkg ->
            runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull()
        } ?: Graph.appContext.getString(R.string.s_unknown_app)
        val appIcon: Drawable? = callingPackage?.let { pkg ->
            runCatching { packageManager.getApplicationIcon(pkg) }.getOrNull()
        }

        setContent {
            NanioTheme {
                CredentialCreateScreen(
                    appLabel = appLabel,
                    appIcon = appIcon,
                    initialAccount = passwordRequest?.id.orEmpty(),
                    initialPassword = passwordRequest?.password.orEmpty(),
                    canSave = passwordRequest != null,
                    onCancel = { finishAsCancelled() },
                    onSave = { name, account, password ->
                        val result = runCatching {
                            runBlocking {
                                Graph.repository.upsertByAccount(
                                    PasswordEntity(
                                        label = name.ifBlank { appLabel },
                                        account = account,
                                        password = password,
                                        packageName = callingPackage,
                                        appLabel = appLabel,
                                    ),
                                )
                            }
                        }
                        result.onSuccess { updated ->
                            val message = if (updated) Graph.appContext.getString(R.string.s_password_updated_for_this_account) else Graph.appContext.getString(R.string.s_saved_to_naniopass)
                            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                            finishWithSuccess()
                        }.onFailure {
                            Toast.makeText(this, Graph.appContext.getString(R.string.s_save_failed), Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            }
        }
    }

    private fun finishWithSuccess() {
        val result = Intent()
        PendingIntentHandler.setCreateCredentialResponse(result, CreatePasswordResponse())
        setResult(RESULT_OK, result)
        finish()
    }

    private fun finishAsCancelled() {
        setResult(RESULT_CANCELED)
        finish()
    }
}

@Composable
private fun CredentialCreateScreen(
    appLabel: String,
    appIcon: Drawable?,
    initialAccount: String,
    initialPassword: String,
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: (name: String, account: String, password: String) -> Unit,
) {
    val colors = NanioTheme.colors
    var name by remember { mutableStateOf(appLabel) }
    var account by remember { mutableStateOf(initialAccount) }
    var password by remember { mutableStateOf(initialPassword) }
    var passwordError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBg)
            .statusBarsPadding(),
    ) {
        TopBar(title = Graph.appContext.getString(R.string.s_save_to_naniopass), onBack = onCancel)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            NanioCard {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIconAvatar(drawable = appIcon, initial = appLabel.trim().take(1).uppercase(), size = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Graph.appContext.getString(R.string.s_from_s, appLabel),
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = Graph.appContext.getString(R.string.s_once_confirmed_it_will_be_saved_on_this_device_a),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            NanioTextField(
                value = name,
                onValueChange = { name = it },
                label = Graph.appContext.getString(R.string.s_name),
                placeholder = Graph.appContext.getString(R.string.s_enter_a_name),
            )
            Spacer(Modifier.height(12.dp))
            NanioTextField(
                value = account,
                onValueChange = { account = it },
                label = Graph.appContext.getString(R.string.s_account_2),
                placeholder = Graph.appContext.getString(R.string.s_enter_an_account),
            )
            Spacer(Modifier.height(12.dp))
            NanioTextField(
                value = password,
                onValueChange = { password = it; passwordError = false },
                label = Graph.appContext.getString(R.string.s_password),
                placeholder = Graph.appContext.getString(R.string.s_enter_password),
                isError = passwordError,
            )
            if (!canSave) {
                Text(
                    text = Graph.appContext.getString(R.string.s_this_request_doesn_t_contain_a_password_to_save),
                    color = colors.danger,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = Graph.appContext.getString(R.string.s_cancel),
                color = colors.textPrimary,
                fontSize = 16.sp,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.cardBgSecondary)
                    .clickable { onCancel() }
                    .padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                text = Graph.appContext.getString(R.string.s_save),
                color = colors.onBrand,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (canSave) colors.brand else colors.divider)
                    .clickable(enabled = canSave) {
                        if (password.isEmpty()) {
                            passwordError = true
                        } else {
                            onSave(name, account, password)
                        }
                    }
                    .padding(vertical = 14.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
