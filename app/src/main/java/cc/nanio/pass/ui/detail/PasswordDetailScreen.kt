package cc.nanio.pass.ui.detail



import cc.nanio.pass.R
import cc.nanio.pass.Graph
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.nanio.pass.data.model.PasswordEntity
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.components.AppIconAvatar
import cc.nanio.pass.ui.components.ConfirmDialog
import cc.nanio.pass.ui.components.EmptyView
import cc.nanio.pass.ui.components.NanioTextField
import cc.nanio.pass.ui.components.PageScaffold
import cc.nanio.pass.ui.components.TopBar
import cc.nanio.pass.ui.components.shortToast
import cc.nanio.pass.ui.theme.NanioTheme
import cc.nanio.pass.ui.totp.TotpCode
import cc.nanio.pass.ui.totp.toTotpConfig
import cc.nanio.pass.ui.totp.rememberNowTicker
import cc.nanio.pass.ui.util.rememberAppIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PasswordDetailScreen(
    vm: AppViewModel,
    id: Long,
    onBack: () -> Unit,
) {
    val colors = NanioTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val item: PasswordEntity? = vm.passwords.firstOrNull { it.id == id }
        ?: vm.deleted.firstOrNull { it.id == id }
    var showPassword by remember { mutableStateOf(false) }
    var confirmPurge by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    if (item == null) {
        PageScaffold {
            TopBar(title = Graph.appContext.getString(R.string.s_password_details), onBack = onBack)
            EmptyView(title = Graph.appContext.getString(R.string.s_this_password_no_longer_exists))
        }
        return
    }

    // 行内编辑态：左上 ✕ 取消、右上 ✓ 保存
    if (editing && !item.isDeleted) {
        PasswordDetailEditScreen(
            item = item,
            vm = vm,
            onCancel = { editing = false },
            onDeleted = {
                editing = false
                onBack()
            },
            onSave = { label, account, password, domain, note ->
                vm.save(
                    item.copy(
                        label = label,
                        account = account,
                        password = password,
                        domain = domain.ifBlank { null },
                        note = note.ifBlank { null },
                    ),
                ) {
                    editing = false
                    context.shortToast(Graph.appContext.getString(R.string.s_saved))
                }
            },
        )
        return
    }

    val icon = rememberAppIcon(item.packageName)
    val clipboardCopy: (String, String) -> Unit = { text, hint ->
        clipboard.setText(AnnotatedString(text))
        context.shortToast(hint)
    }

    PageScaffold {
        TopBar(
            title = Graph.appContext.getString(R.string.s_password_details),
            onBack = onBack,
            actions = {
                if (!item.isDeleted) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { editing = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = Graph.appContext.getString(R.string.s_edit),
                            tint = colors.brand,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            // 头部：水平居中大图标（68dp）+ 名称 18sp + 上次修改时间（避免与下方账号项重复）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIconAvatar(drawable = icon, initial = item.initial, size = 68.dp, corner = 20.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = item.label,
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val modifiedAt = remember(item.updatedAt) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(item.updatedAt))
                }
                Text(
                    text = Graph.appContext.getString(R.string.s_last_modified_s, modifiedAt),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))

            // 账号
            NanioCard {
                DetailItemRow(
                    title = Graph.appContext.getString(R.string.s_account_2),
                    value = item.account.ifEmpty { Graph.appContext.getString(R.string.s_none) },
                    onClick = if (item.account.isNotEmpty()) {
                        { clipboardCopy(item.account, Graph.appContext.getString(R.string.s_account_copied)) }
                    } else null,
                )
                DividerLine()
                // 密码：默认 6 个点；点击整行复制并直接显示明文（再次点击恢复掩码）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            clipboardCopy(item.password, Graph.appContext.getString(R.string.s_password_copied))
                            vm.touchLastUsed(item.id)
                            showPassword = !showPassword
                        }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = Graph.appContext.getString(R.string.s_password),
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (showPassword) item.password else "•".repeat(6),
                        color = if (showPassword) colors.textPrimary else colors.textSecondary,
                        fontSize = if (showPassword) 15.sp else 16.sp,
                        fontFamily = if (showPassword) FontFamily.Monospace else FontFamily.Default,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
                if (item.hasTotp) {
                    DividerLine()
                    // 动态令牌（17sp 码 + 24dp 进度环，点整行复制）
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val config = item.toTotpConfig()
                                if (config != null) {
                                    val code = runCatching {
                                        cc.nanio.pass.totp.TotpEngine.generate(config)
                                    }.getOrNull()
                                    if (code != null) {
                                        clipboardCopy(code, Graph.appContext.getString(R.string.s_authenticator_code_copied))
                                        vm.touchLastUsed(item.id)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = Graph.appContext.getString(R.string.s_authenticator),
                            color = colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.weight(1f))
                        item.toTotpConfig()?.let { config ->
                            TotpCode(config = config, colors = colors, codeFontSize = 17)
                        }
                    }
                }
                if (!item.domain.isNullOrEmpty()) {
                    DividerLine()
                    DetailItemRow(
                        title = Graph.appContext.getString(R.string.s_website),
                        value = item.domain,
                        onClick = { clipboardCopy(item.domain, Graph.appContext.getString(R.string.s_website_copied)) },
                    )
                }
                if (!item.note.isNullOrEmpty()) {
                    DividerLine()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = Graph.appContext.getString(R.string.s_notes),
                            color = colors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(text = item.note, color = colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            if (item.isDeleted) {
                // 最近删除：只有恢复
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.cardBg)
                        .clickable { vm.restore(item.id); onBack() }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = Graph.appContext.getString(R.string.s_restore_this_password), color = colors.brand, fontSize = 16.sp)
                }
            }
            if (item.isDeleted) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.cardBg)
                        .clickable { confirmPurge = true }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = Graph.appContext.getString(R.string.s_delete_permanently), color = colors.danger, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmPurge) {
        ConfirmDialog(
            title = Graph.appContext.getString(R.string.s_permanently_delete_this_password),
            message = Graph.appContext.getString(R.string.s_this_cannot_be_undone_after_deletion),
            confirmText = Graph.appContext.getString(R.string.s_delete),
            cancelText = Graph.appContext.getString(R.string.s_cancel),
            danger = true,
            onConfirm = {
                confirmPurge = false
                vm.purge(item.id)
                onBack()
            },
            onDismiss = { confirmPurge = false },
        )
    }

}

@Composable
private fun NanioCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val colors = NanioTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBg),
        content = content,
    )
}

@Composable
private fun DividerLine() {
    val colors = NanioTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(colors.divider),
    )
}

@Composable
private fun DetailItemRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    val colors = NanioTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            color = colors.textSecondary,
            fontSize = 14.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

/** 详情页行内编辑（ActionMode 风格）：左上 ✕ 取消、右上 ✓ 保存。 */
@Composable
private fun PasswordDetailEditScreen(
    item: PasswordEntity,
    vm: AppViewModel,
    onCancel: () -> Unit,
    onDeleted: () -> Unit,
    onSave: (label: String, account: String, password: String, domain: String, note: String) -> Unit,
) {
    val colors = NanioTheme.colors
    val context = LocalContext.current
    var label by remember(item.id) { mutableStateOf(item.label) }
    var account by remember(item.id) { mutableStateOf(item.account) }
    var password by remember(item.id) { mutableStateOf(item.password) }
    var domain by remember(item.id) { mutableStateOf(item.domain.orEmpty()) }
    var note by remember(item.id) { mutableStateOf(item.note.orEmpty()) }
    var labelError by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var confirmDeletePwd by remember { mutableStateOf(false) }
    var confirmDetachTotp by remember { mutableStateOf(false) }

    PageScaffold {
        TopBar(
            title = Graph.appContext.getString(R.string.s_password_details),
            onBack = onCancel,
            leadingIcon = Icons.Filled.Close,
            actions = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            labelError = label.isBlank()
                            accountError = account.isBlank()
                            passwordError = password.isBlank()
                            if (!labelError && !accountError && !passwordError) {
                                onSave(label.trim(), account.trim(), password, domain.trim(), note.trim())
                            } else {
                                context.shortToast(Graph.appContext.getString(R.string.s_please_fill_in_the_name_account_and_password))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = Graph.appContext.getString(R.string.s_save),
                        tint = colors.brand,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            if (item.packageName.isNullOrEmpty()) {
                NanioTextField(
                    value = label,
                    onValueChange = { label = it; labelError = false },
                    label = Graph.appContext.getString(R.string.s_name),
                    placeholder = Graph.appContext.getString(R.string.s_enter_a_name),
                    isError = labelError,
                )
            } else {
                // 与其它输入框保持同款（12dp 圆角 / cardBgSecondary / 14×10 内边距），仅不可编辑
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardBgSecondary)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(text = Graph.appContext.getString(R.string.s_name), color = colors.textSecondary, fontSize = 12.sp)
                    Text(
                        text = item.label,
                        color = colors.textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            NanioTextField(
                value = account,
                onValueChange = { account = it; accountError = false },
                label = Graph.appContext.getString(R.string.s_account_2),
                placeholder = Graph.appContext.getString(R.string.s_enter_an_account),
                isError = accountError,
            )
            Spacer(Modifier.height(12.dp))
            NanioTextField(
                value = password,
                onValueChange = { password = it; passwordError = false },
                label = Graph.appContext.getString(R.string.s_password),
                placeholder = Graph.appContext.getString(R.string.s_enter_password),
                isError = passwordError,
            )
            if (item.hasTotp) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.cardBgSecondary)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = Graph.appContext.getString(R.string.s_authenticator), color = colors.textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        item.toTotpConfig()?.let { config ->
                            TotpCode(config = config, colors = colors, codeFontSize = 17)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(colors.divider),
                    )
                    Text(
                        text = Graph.appContext.getString(R.string.s_delete_authenticator),
                        color = colors.danger,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { confirmDetachTotp = true }
                            .padding(vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            NanioTextField(
                value = domain,
                onValueChange = { domain = it },
                label = Graph.appContext.getString(R.string.s_website_optional),
                placeholder = Graph.appContext.getString(R.string.s_enter_a_website_url),
            )
            Spacer(Modifier.height(12.dp))
            NanioTextField(
                value = note,
                onValueChange = { note = it },
                label = Graph.appContext.getString(R.string.s_notes_optional),
                placeholder = Graph.appContext.getString(R.string.s_notes),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardBg)
                    .clickable { confirmDeletePwd = true }
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(text = Graph.appContext.getString(R.string.s_delete_password), color = colors.danger, fontSize = 16.sp)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDeletePwd) {
        ConfirmDialog(
            title = if (item.hasTotp) Graph.appContext.getString(R.string.s_delete_password_and_authenticator) else Graph.appContext.getString(R.string.s_delete_password_2),
            message = Graph.appContext.getString(R.string.s_you_can_go_to_recently_deleted_to_restore_or_per),
            confirmText = Graph.appContext.getString(R.string.s_delete),
            cancelText = Graph.appContext.getString(R.string.s_cancel),
            danger = true,
            onConfirm = {
                confirmDeletePwd = false
                vm.delete(item.id)
                onDeleted()
            },
            onDismiss = { confirmDeletePwd = false },
        )
    }

    if (confirmDetachTotp) {
        ConfirmDialog(
            title = Graph.appContext.getString(R.string.s_delete_authenticator_2),
            message = Graph.appContext.getString(R.string.s_deleting_the_authenticator_may_prevent_you_from_),
            confirmText = Graph.appContext.getString(R.string.s_delete),
            cancelText = Graph.appContext.getString(R.string.s_cancel),
            danger = true,
            onConfirm = {
                confirmDetachTotp = false
                vm.detachTotp(item.id)
            },
            onDismiss = { confirmDetachTotp = false },
        )
    }
}