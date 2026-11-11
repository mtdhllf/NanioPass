package cc.nanio.pass.ui.totp



import cc.nanio.pass.R
import cc.nanio.pass.Graph
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.journeyapps.barcodescanner.ScanContract
import cc.nanio.pass.data.model.PasswordEntity
import cc.nanio.pass.totp.OtpauthParser
import cc.nanio.pass.totp.TotpConfig
import cc.nanio.pass.totp.TotpParseResult
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.components.AddFab
import cc.nanio.pass.ui.components.AppIconAvatar
import cc.nanio.pass.ui.components.EmptyView
import cc.nanio.pass.ui.components.NanioTextField
import cc.nanio.pass.ui.components.PageScaffold
import cc.nanio.pass.ui.components.TopBar
import cc.nanio.pass.ui.components.shortToast
import cc.nanio.pass.ui.theme.NanioTheme
import cc.nanio.pass.ui.util.rememberAppIcon
import android.Manifest

@Composable
fun TotpListScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpenPassword: (Long) -> Unit,
    onAddPassword: () -> Unit,
) {
    val colors = NanioTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showAddDialog by remember { mutableStateOf(false) }
    var secretInput by remember { mutableStateOf("") }
    var pendingConfig by remember { mutableStateOf<TotpConfig?>(null) }
    var showSelectPassword by remember { mutableStateOf(false) }

    val totpItems = vm.passwords.filter { it.hasTotp }

    fun handleParsed(raw: String?) {
        when (val result = OtpauthParser.parse(raw)) {
            is TotpParseResult.Success -> {
                pendingConfig = result.config
                if (vm.passwords.isEmpty()) {
                    // 没有任何密码时直接进入「新建密码」
                    vm.requestPendingTotp(result.config)
                    pendingConfig = null
                    onAddPassword()
                } else {
                    showSelectPassword = true
                }
            }
            is TotpParseResult.Failure -> {
                context.shortToast(
                    context.getString(result.reasonRes, *result.args.toTypedArray()),
                )
            }
        }
    }

    val activity = context as? androidx.fragment.app.FragmentActivity
    fun startScan() {
        val act = activity
        if (act == null) {
            context.shortToast(Graph.appContext.getString(R.string.s_cannot_start_the_scanner))
            return
        }
        cc.nanio.pass.ui.scan.ScanLauncher.launch(act) { contents ->
            // 用户主动取消/返回时静默处理；只有真正带回内容才解析
            if (contents != null) handleParsed(contents)
        }
    }
    fun requestScan() {
        val act = activity ?: return
        if (context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScan()
        } else {
            cc.nanio.pass.ui.util.PermissionBridge.request(act, Manifest.permission.CAMERA) { granted ->
                if (granted) startScan() else context.shortToast(Graph.appContext.getString(R.string.s_camera_permission_is_required_to_scan_qr_codes))
            }
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            cc.nanio.pass.ui.scan.ScanLauncher.clear()
            cc.nanio.pass.ui.util.PermissionBridge.clear()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PageScaffold {
            TopBar(title = Graph.appContext.getString(R.string.s_authenticator), onBack = onBack)
            if (totpItems.isEmpty()) {
                EmptyView(title = Graph.appContext.getString(R.string.s_no_authenticators_yet), subtitle = Graph.appContext.getString(R.string.s_tap_the_bottom_right_button_to_add), icon = Icons.Filled.VpnKey)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 4.dp,
                        bottom = 96.dp,
                    ),
                ) {
                    items(totpItems, key = { it.id }) { item ->
                        androidx.compose.foundation.layout.Box(modifier = Modifier.animateItem()) {
                            TotpRow(
                                item = item,
                                onClick = {
                                    val config = item.toTotpConfig()
                                    if (config != null) {
                                        val code = runCatching { cc.nanio.pass.totp.TotpEngine.generate(config) }.getOrNull()
                                        if (code != null) {
                                            clipboard.setText(AnnotatedString(code))
                                            context.shortToast(Graph.appContext.getString(R.string.s_authenticator_code_copied))
                                            vm.touchLastUsed(item.id)
                                        }
                                    }
                                },
                                onLongClick = { onOpenPassword(item.id) },
                            )
                        }
                    }
                }
            }
        }
        AddFab(
            onClick = {
                showAddDialog = true
                secretInput = ""
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp)
                .navigationBarsPadding(),
            contentDescription = Graph.appContext.getString(R.string.s_tap_to_add_an_authenticator),
        )
    }

    if (showAddDialog) {
        AddTotpDialog(
            secret = secretInput,
            onSecretChange = { secretInput = it },
            onDismiss = { showAddDialog = false },
            onUseSecret = {
                showAddDialog = false
                handleParsed(secretInput)
            },
            onScan = {
                showAddDialog = false
                requestScan()
            },
        )
    }

    if (showSelectPassword) {
        val config = pendingConfig
        if (config != null) {
            SelectPasswordDialog(
                candidates = vm.passwords.filter { !it.hasTotp },
                onDismiss = {
                    showSelectPassword = false
                    pendingConfig = null
                },
                onAddNew = {
                    showSelectPassword = false
                    pendingConfig = null
                    vm.requestPendingTotp(config)
                    onAddPassword()
                },
                onSelect = { item ->
                    vm.attachTotp(item.id, config) {
                        context.shortToast(Graph.appContext.getString(R.string.s_authenticator_added))
                    }
                    showSelectPassword = false
                    pendingConfig = null
                },
            )
        }
    }
}

@Composable
private fun TotpRow(
    item: PasswordEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    val colors = NanioTheme.colors
    val icon = rememberAppIcon(item.packageName)
    val config = item.toTotpConfig() ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconAvatar(drawable = icon, initial = item.initial, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 副标题优先显示账号；与标题相同时再退回 issuer，避免重复
            val subtitle = config.account?.takeIf { it.isNotBlank() && it != item.label }
                ?: config.issuer?.takeIf { it.isNotBlank() && it != item.label }
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        TotpCode(config = config, colors = colors, codeFontSize = 28, ringSize = 24)
    }
}

@Composable
private fun AddTotpDialog(
    secret: String,
    onSecretChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onUseSecret: () -> Unit,
    onScan: () -> Unit,
) {
    val colors = NanioTheme.colors
    // 底部弹出、内容自适应高度、四周留边距 + 圆角
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.cardBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            ) {
                Text(
                    text = Graph.appContext.getString(R.string.s_add_authenticator),
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(16.dp))
                NanioTextField(
                    value = secret,
                    onValueChange = onSecretChange,
                    label = Graph.appContext.getString(R.string.s_secret_key),
                    placeholder = Graph.appContext.getString(R.string.s_enter_the_secret_key),
                    onDone = { if (secret.isNotBlank()) onUseSecret() },
                )
                Spacer(Modifier.height(8.dp))
                // 纵向按钮：使用密钥 / 扫描二维码 / 取消
                val enabled = secret.isNotBlank()
                Text(
                    text = Graph.appContext.getString(R.string.s_use_secret_key),
                    color = if (enabled) colors.brand else colors.textTertiary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = enabled) { onUseSecret() }
                        .padding(vertical = 14.dp),
                )
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.divider))
                Text(
                    text = Graph.appContext.getString(R.string.s_scan_qr_code),
                    color = colors.brand,
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onScan() }
                        .padding(vertical = 14.dp),
                )
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.divider))
                Text(
                    text = Graph.appContext.getString(R.string.s_cancel),
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 14.dp),
                )
            }
        }
    }
}

/** 选择关联密码（只列出尚无有效令牌的密码，首项「添加新密码」）。 */
@Composable
private fun SelectPasswordDialog(
    candidates: List<PasswordEntity>,
    onDismiss: () -> Unit,
    onAddNew: () -> Unit,
    onSelect: (PasswordEntity) -> Unit,
) {
    val colors = NanioTheme.colors
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(colors.pageBg),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = Graph.appContext.getString(R.string.s_close), tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                }
                Text(
                    text = Graph.appContext.getString(R.string.s_select_password),
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.size(48.dp))
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "add_new") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAddNew() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.cardBgSecondary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, tint = colors.brand, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(Graph.appContext.getString(R.string.s_add_new_password), color = colors.brand, fontSize = 16.sp)
                    }
                }
                item(key = "group_all") {
                    Text(
                        text = Graph.appContext.getString(R.string.s_all_passwords),
                        color = colors.textSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                items(candidates, key = { it.id }) { item ->
                    val icon = rememberAppIcon(item.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(item) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconAvatar(drawable = icon, initial = item.initial, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.label,
                                color = colors.textPrimary,
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.account.isNotEmpty()) {
                                Text(
                                    text = item.account,
                                    color = colors.textSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
