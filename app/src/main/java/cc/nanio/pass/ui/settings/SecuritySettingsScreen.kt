package cc.nanio.pass.ui.settings



import cc.nanio.pass.R
import cc.nanio.pass.Graph
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.nanio.pass.security.BiometricHelper
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.components.ConfirmDialog
import cc.nanio.pass.ui.components.PageScaffold
import cc.nanio.pass.ui.components.SettingRow
import cc.nanio.pass.ui.components.TopBar
import cc.nanio.pass.ui.components.shortToast
import cc.nanio.pass.ui.theme.NanioTheme
import androidx.compose.ui.window.Dialog

private val AUTO_LOCK_OPTIONS = listOf(
    0 to Graph.appContext.getString(R.string.s_immediately),
    1 to Graph.appContext.getString(R.string.s_s_1_minute),
    5 to Graph.appContext.getString(R.string.s_s_5_minutes),
    15 to Graph.appContext.getString(R.string.s_s_15_minutes),
    30 to Graph.appContext.getString(R.string.s_s_30_minutes),
)

@Composable
fun SecuritySettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
) {
    val colors = NanioTheme.colors
    val context = LocalContext.current
    var showAutoLockPicker by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    PageScaffold {
        TopBar(title = Graph.appContext.getString(R.string.s_security_settings), onBack = onBack)
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
                    title = Graph.appContext.getString(R.string.s_biometric_unlock),
                    summary = Graph.appContext.getString(R.string.s_verify_with_fingerprint_face_or_device_password_),
                    showDivider = true,
                    trailing = {
                        Switch(
                            checked = vm.biometricEnabled,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    vm.updateBiometricEnabled(false)
                                } else {
                                    val activity = context as? androidx.fragment.app.FragmentActivity
                                    when {
                                        !BiometricHelper.canAuthenticate(context) ->
                                            context.shortToast(Graph.appContext.getString(R.string.s_biometrics_are_not_supported_or_not_set_up_on_th))
                                        activity == null ->
                                            context.shortToast(Graph.appContext.getString(R.string.s_unable_to_start_biometric_authentication))
                                        else -> BiometricHelper.authenticate(
                                            activity = activity,
                                            title = Graph.appContext.getString(R.string.s_verify_to_turn_on_app_lock),
                                            onSuccess = { vm.updateBiometricEnabled(true) },
                                            onError = { },
                                        )
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.brand),
                        )
                    },
                )
                SettingRow(
                    title = Graph.appContext.getString(R.string.s_auto_lock),
                    summary = AUTO_LOCK_OPTIONS.firstOrNull { it.first == vm.autoLockMinutes }?.second
                        ?: Graph.appContext.getString(R.string.s_s_minutes, vm.autoLockMinutes),
                    showDivider = false,
                    onClick = { showAutoLockPicker = true },
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
                    title = Graph.appContext.getString(R.string.s_clear_all_data),
                    summary = Graph.appContext.getString(R.string.s_delete_all_passwords_authenticators_and_recently),
                    showDivider = false,
                    onClick = { showClearConfirm = true },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAutoLockPicker) {
        AutoLockPicker(
            current = vm.autoLockMinutes,
            onDismiss = { showAutoLockPicker = false },
            onSelected = {
                vm.updateAutoLockMinutes(it)
                showAutoLockPicker = false
            },
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = Graph.appContext.getString(R.string.s_clear_all_data_2),
            message = Graph.appContext.getString(R.string.s_this_cannot_be_undone_proceed_with_caution),
            confirmText = Graph.appContext.getString(R.string.s_clear_all),
            danger = true,
            onConfirm = {
                showClearConfirm = false
                vm.clearAllData()
            },
            onDismiss = { showClearConfirm = false },
        )
    }
}

@Composable
private fun AutoLockPicker(
    current: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    val colors = NanioTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardBg)
                .padding(vertical = 8.dp),
        ) {
            Text(
                text = Graph.appContext.getString(R.string.s_auto_lock),
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
            AUTO_LOCK_OPTIONS.forEach { (minutes, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(minutes) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        color = if (minutes == current) colors.brand else colors.textPrimary,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (minutes == current) {
                        Text(text = "✓", color = colors.brand, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
