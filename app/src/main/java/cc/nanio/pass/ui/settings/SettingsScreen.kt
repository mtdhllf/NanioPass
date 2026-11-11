package cc.nanio.pass.ui.settings



import cc.nanio.pass.R
import cc.nanio.pass.Graph
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.components.PageScaffold
import cc.nanio.pass.ui.components.NanioTextField
import cc.nanio.pass.ui.components.SettingRow
import cc.nanio.pass.ui.components.TopBar
import cc.nanio.pass.ui.components.shortToast
import cc.nanio.pass.ui.theme.NanioTheme
import cc.nanio.pass.ui.util.PickerBridge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenSecurity: () -> Unit,
) {
    val colors = NanioTheme.colors
    val context = LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "1.0"

    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showAutofillGuide by remember { mutableStateOf(false) }
    var autofillEnabled by remember { mutableStateOf(isAutofillEnabled(context) || isOurCredentialService(context)) }

    // 从系统设置返回后刷新自动填充状态
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                autofillEnabled = isAutofillEnabled(context) || isOurCredentialService(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { PickerBridge.clear() }
    }

    fun writeBackup(text: String) {
        val act = activity ?: return
        val name = "nanio-backup-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date()) + ".json"
        PickerBridge.create(act, name, "application/json") { uri ->
            if (uri == null) return@create
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess { context.shortToast(Graph.appContext.getString(R.string.s_backup_exported)) }
                .onFailure { context.shortToast(Graph.appContext.getString(R.string.s_export_failed_s, it.message)) }
        }
    }

    fun readBackup(passphrase: String) {
        val act = activity ?: return
        PickerBridge.open(act, "application/json") { uri ->
            if (uri == null) return@open
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                context.shortToast(Graph.appContext.getString(R.string.s_failed_to_read_file))
                return@open
            }
            vm.importBackup(bytes, passphrase) { result ->
                result.onSuccess { summary ->
                            val skipped = if (summary.skipped > 0) Graph.appContext.getString(R.string.s_s_duplicates_skipped, summary.skipped) else ""
                            context.shortToast(Graph.appContext.getString(R.string.s_imported_s_passwords_s, summary.imported, skipped))
                        }
                    .onFailure { context.shortToast(Graph.appContext.getString(R.string.s_import_failed_s, it.message)) }
            }
        }
    }

    PageScaffold {
        TopBar(title = Graph.appContext.getString(R.string.s_settings), onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardBg),
            ) {
                SettingRow(
                    title = Graph.appContext.getString(R.string.s_security_settings),
                    summary = if (vm.biometricEnabled) Graph.appContext.getString(R.string.s_biometric_unlock_is_on) else Graph.appContext.getString(R.string.s_app_lock_is_off),
                    onClick = onOpenSecurity,
                    trailing = { Chevron() },
                )
                SettingRow(
                    title = Graph.appContext.getString(R.string.s_autofill_and_credential_service),
                    summary = if (autofillEnabled) {
                        Graph.appContext.getString(R.string.s_enabled)
                    } else {
                        Graph.appContext.getString(R.string.s_not_enabled_tap_to_open_system_settings_and_sele)
                    },
                    showDivider = false,
                    onClick = {
                        // 部分系统把这两个能力放在同一个「密码管理」入口，跳转优先用自动填充入口
                        if (!tryOpenAutofillSettings(context) && !tryOpenCredentialProviderSettings(context)) {
                            showAutofillGuide = true
                        }
                    },
                    trailing = { Chevron() },
                )
            }
            Spacer(Modifier.height(12.dp))
            // P1：加密备份
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardBg),
            ) {
                SettingRow(
                    title = Graph.appContext.getString(R.string.s_export_encrypted_backup),
                    summary = Graph.appContext.getString(R.string.s_export_as_encrypted_file),
                    onClick = { showExport = true },
                )
                SettingRow(
                    title = Graph.appContext.getString(R.string.s_import_encrypted_backup),
                    summary = Graph.appContext.getString(R.string.s_restore_data_from_a_file),
                    showDivider = false,
                    onClick = { showImport = true },
                )
            }
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardBg),
            ) {
                SettingRow(
                    title = Graph.appContext.getString(R.string.s_version),
                    summary = context.getString(cc.nanio.pass.R.string.app_name) + " " + versionName,
                    showDivider = false,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showExport) {
        PassphraseDialog(
            title = Graph.appContext.getString(R.string.s_export_encrypted_backup),
            confirmText = Graph.appContext.getString(R.string.s_export),
            requireConfirm = true,
            onDismiss = { showExport = false },
            onConfirm = { passphrase ->
                showExport = false
                vm.exportBackup(passphrase) { result ->
                    result.onSuccess { writeBackup(it) }
                        .onFailure { context.shortToast(Graph.appContext.getString(R.string.s_export_failed_s, it.message)) }
                }
            },
        )
    }

    if (showImport) {
        PassphraseDialog(
            title = Graph.appContext.getString(R.string.s_import_encrypted_backup),
            confirmText = Graph.appContext.getString(R.string.s_choose_file),
            requireConfirm = false,
            onDismiss = { showImport = false },
            onConfirm = { passphrase ->
                showImport = false
                readBackup(passphrase)
            },
        )
    }

    if (showAutofillGuide) {
        AutofillGuideDialog(
            enabled = autofillEnabled,
            onDismiss = { showAutofillGuide = false },
            onOpenSettings = {
                showAutofillGuide = false
                if (!tryOpenAutofillSettings(context)) {
                    context.shortToast(Graph.appContext.getString(R.string.s_no_direct_shortcut_on_this_system_follow_the_ins))
                }
            },
        )
    }

}

/** 尝试直达系统「凭据提供程序」设置（Android 14+）；不可达返回 false 让调用方展示指引。 */
private fun tryOpenCredentialProviderSettings(context: android.content.Context): Boolean {
    val pkgUri = android.net.Uri.parse("package:" + context.packageName)
    val intents = buildList {
        // AOSP：无 data 的凭据提供程序设置页
        add(Intent("android.settings.CREDENTIAL_PROVIDER"))
        // 部分系统需要 package data，落到「账号 → 首选服务/密码管理」页
        add(Intent("android.settings.CREDENTIAL_PROVIDER", pkgUri))
        add(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
    }
    return startFirstResolvable(context, intents)
}

/** 凭据服务是否已指向本应用。 */
private fun isOurCredentialService(context: android.content.Context): Boolean {
    val ours = context.packageName + "/" + context.packageName + ".credential.NanioCredentialProviderService"
    val cr = context.contentResolver
    return runCatching {
        android.provider.Settings.Secure.getString(cr, "credential_service") == ours ||
            android.provider.Settings.Secure.getString(cr, "credential_service_primary") == ours
    }.getOrDefault(false)
}

/** 判断并打开第一个可解析的 Intent。 */
private fun startFirstResolvable(context: android.content.Context, intents: List<Intent>): Boolean {
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val resolved = runCatching {
            context.packageManager.resolveActivity(intent, 0)
        }.getOrNull()
        if (resolved == null) continue
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
    }
    return false
}

@Composable
private fun SimpleGuideDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = NanioTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardBg)
                .padding(20.dp),
        ) {
            Text(text = title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Text(text = body, color = colors.textSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = Graph.appContext.getString(R.string.s_cancel),
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = Graph.appContext.getString(R.string.s_go_to_settings),
                    color = colors.brand,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onOpenSettings() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 判断当前默认自动填充服务是否为本应用。 */
private fun isAutofillEnabled(context: android.content.Context): Boolean {
    val manager = context.getSystemService(android.view.autofill.AutofillManager::class.java) ?: return false
    val component = manager.autofillServiceComponentName ?: return false
    return component.packageName == context.packageName
}

/**
 * 尝试直达系统「默认自动填充服务」选择页。
 * 先带 package 用官方 ACTION_REQUEST_SET_AUTOFILL_SERVICE，失败再回退到安全设置/设置首页。
 */
private fun tryOpenAutofillSettings(context: android.content.Context): Boolean {
    val pkgUri = android.net.Uri.parse("package:" + context.packageName)
    val intents = buildList {
        add(Intent(android.provider.Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, pkgUri))
        add(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
        add(Intent(android.provider.Settings.ACTION_SETTINGS))
    }
    return startFirstResolvable(context, intents)
}

@Composable
private fun AutofillGuideDialog(
    enabled: Boolean,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = NanioTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardBg)
                .padding(20.dp),
        ) {
            Text(
                text = if (enabled) Graph.appContext.getString(R.string.s_autofill_and_credential_service_are_on) else Graph.appContext.getString(R.string.s_turn_on_autofill_and_credential_service),
                color = colors.textPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (enabled) {
                    Graph.appContext.getString(R.string.s_on_the_sign_in_page_of_any_app_or_browser_tap_th)
                } else {
                    Graph.appContext.getString(R.string.s_on_some_systems_autofill_and_credential_service_) +
                        Graph.appContext.getString(R.string.s_s_1_tap_go_to_system_settings_below_then_open_pa) +
                        Graph.appContext.getString(R.string.s_s_2_under_add_service_preferred_service_select_n) +
                        Graph.appContext.getString(R.string.s_s_3_return_to_this_page_it_will_show_as_enabled_) +
                        Graph.appContext.getString(R.string.s_stock_android_autofill_is_configured_in_autofill)
                },
                color = colors.textSecondary,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (enabled) Graph.appContext.getString(R.string.s_close) else Graph.appContext.getString(R.string.s_cancel),
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                if (!enabled) {
                    Text(
                        text = Graph.appContext.getString(R.string.s_go_to_system_settings),
                        color = colors.brand,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenSettings() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.Rounded.KeyboardArrowLeft,
        contentDescription = null,
        tint = NanioTheme.colors.textTertiary,
        modifier = Modifier
            .rotate(180f)
            .height(20.dp),
    )
}

/** 口令输入弹窗（导出要求二次确认，导入只输一次）。 */
@Composable
private fun PassphraseDialog(
    title: String,
    confirmText: String,
    requireConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = NanioTheme.colors
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = requireConfirm && confirm.isNotEmpty() && confirm != passphrase
    val valid = passphrase.length >= 6 && (!requireConfirm || passphrase == confirm)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardBg)
                .padding(20.dp),
        ) {
            Text(text = title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = Graph.appContext.getString(R.string.s_passphrase_must_be_at_least_6_characters_remembe),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(14.dp))
            NanioTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = Graph.appContext.getString(R.string.s_backup_passphrase),
                placeholder = Graph.appContext.getString(R.string.s_enter_passphrase),
            )
            if (requireConfirm) {
                Spacer(Modifier.height(10.dp))
                NanioTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = Graph.appContext.getString(R.string.s_confirm_passphrase),
                    placeholder = Graph.appContext.getString(R.string.s_enter_passphrase_again),
                    isError = mismatch,
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = Graph.appContext.getString(R.string.s_cancel),
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = confirmText,
                    color = if (valid) colors.brand else colors.textTertiary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = valid) { onConfirm(passphrase) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
