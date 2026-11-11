package cc.nanio.pass.ui.addedit



import cc.nanio.pass.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cc.nanio.pass.Graph
import cc.nanio.pass.data.AppInfo
import cc.nanio.pass.data.model.PasswordEntity
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.components.AppIconAvatar
import cc.nanio.pass.ui.components.NanioTextField
import cc.nanio.pass.ui.components.shortToast
import cc.nanio.pass.ui.theme.NanioTheme
import cc.nanio.pass.ui.util.rememberAppIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AddPasswordScreen(
    vm: AppViewModel,
    editId: Long?,
    onClose: () -> Unit,
    onSaved: () -> Unit,
    inDrawer: Boolean = false,
) {
    val colors = NanioTheme.colors
    val context = LocalContext.current
    val existing: PasswordEntity? = remember(editId, vm.passwords) {
        editId?.let { id -> vm.passwords.firstOrNull { it.id == id } }
    }

    var label by remember(existing?.id) { mutableStateOf(existing?.label.orEmpty()) }
    var account by remember(existing?.id) { mutableStateOf(existing?.account.orEmpty()) }
    var password by remember(existing?.id) { mutableStateOf(existing?.password.orEmpty()) }
    var domain by remember(existing?.id) { mutableStateOf(existing?.domain.orEmpty()) }
    var note by remember(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var boundPackage by remember(existing?.id) { mutableStateOf(existing?.packageName) }
    var boundLabel by remember(existing?.id) { mutableStateOf(existing?.appLabel) }

    var labelError by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }

    val isBound = !boundPackage.isNullOrEmpty()
    // 绑定应用时名称锁定为应用名（不可编辑）
    val displayLabel = if (isBound) boundLabel.orEmpty() else label

    fun save() {
        labelError = displayLabel.isBlank()
        accountError = account.isBlank()
        passwordError = password.isBlank()
        if (labelError || accountError || passwordError) return
        val now = System.currentTimeMillis()
        val entity = (existing ?: PasswordEntity(label = displayLabel, account = account, password = password)).copy(
            label = displayLabel,
            account = account,
            password = password,
            domain = domain.ifBlank { null },
            note = note.ifBlank { null },
            packageName = boundPackage,
            appLabel = boundLabel,
            updatedAt = now,
        )
        vm.save(entity) { newId ->
            val pending = vm.consumePendingTotp()
            if (pending != null) {
                vm.attachTotp(newId, pending) {
                    context.shortToast(Graph.appContext.getString(R.string.s_authenticator_added))
                }
            }
            context.shortToast(if (existing == null) Graph.appContext.getString(R.string.s_password_added) else Graph.appContext.getString(R.string.s_password_saved))
            onSaved()
        }
    }

    val icon = rememberAppIcon(boundPackage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (inDrawer) Modifier else Modifier.statusBarsPadding())
            .background(colors.pageBg),
    ) {
        // 顶部栏：关闭 / 标题 / 保存
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
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = Graph.appContext.getString(R.string.s_close), tint = colors.textPrimary, modifier = Modifier.size(22.dp))
            }
            Text(
                text = if (existing == null) Graph.appContext.getString(R.string.s_new_password) else Graph.appContext.getString(R.string.s_edit_password),
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { save() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = Graph.appContext.getString(R.string.s_save), tint = colors.brand, modifier = Modifier.size(24.dp))
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            // 应用绑定卡：图标 + 名称（或选择应用入口）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardBg)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconAvatar(
                    drawable = icon,
                    initial = displayLabel.trim().take(1).uppercase(),
                    size = 52.dp,
                    corner = 12.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // 统一形态：始终展示「名称」标签 + 输入框；绑定应用后仅把输入框改为不可编辑
                    Text(
                        text = Graph.appContext.getString(R.string.s_name),
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                    )
                    if (isBound) {
                        Text(
                            text = displayLabel.ifBlank { boundPackage.orEmpty() },
                            color = colors.textPrimary,
                            fontSize = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        )
                    } else {
                        BasicTextField(
                            value = label,
                            onValueChange = {
                                label = it
                                labelError = false
                            },
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 18.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(colors.brand),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                Box {
                                    if (label.isEmpty()) {
                                        Text(text = Graph.appContext.getString(R.string.s_enter_a_name), color = colors.textTertiary, fontSize = 18.sp)
                                    }
                                    inner()
                                }
                            },
                        )
                        if (labelError) {
                            Text(text = Graph.appContext.getString(R.string.s_enter_a_name), color = colors.danger, fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    text = Graph.appContext.getString(R.string.s_select_app),
                    color = colors.brand,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAppPicker = true }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            NanioTextField(
                value = account,
                onValueChange = {
                    account = it
                    accountError = false
                },
                label = Graph.appContext.getString(R.string.s_account_2),
                placeholder = Graph.appContext.getString(R.string.s_enter_an_account),
                isError = accountError,
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
            )
            Spacer(Modifier.height(12.dp))
            NanioTextField(
                value = password,
                onValueChange = {
                    password = it
                    passwordError = false
                },
                label = Graph.appContext.getString(R.string.s_password),
                placeholder = Graph.appContext.getString(R.string.s_enter_password),
                isError = passwordError,
            )
            Spacer(Modifier.height(12.dp))
            NanioTextField(
                value = domain,
                onValueChange = { domain = it },
                label = Graph.appContext.getString(R.string.s_website_optional),
                placeholder = Graph.appContext.getString(R.string.s_enter_a_website_url),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
            )
            Spacer(Modifier.height(12.dp))
            NanioTextField(
                value = note,
                onValueChange = { note = it },
                label = Graph.appContext.getString(R.string.s_notes_optional),
                placeholder = Graph.appContext.getString(R.string.s_notes),
                singleLine = false,
                minHeight = 96.dp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelected = { app ->
                if (app == null) {
                    boundPackage = null
                    boundLabel = null
                    if (label.isBlank()) label = ""
                } else {
                    boundPackage = app.packageName
                    boundLabel = app.label
                    label = app.label
                }
                showAppPicker = false
            },
        )
    }
}

/** 应用选择器：首项自定义，其余为可启动应用，单选。 */
@Composable
private fun AppPickerDialog(
    onDismiss: () -> Unit,
    onSelected: (AppInfo?) -> Unit,
) {
    val colors = NanioTheme.colors
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        apps = runCatching {
            withContext(Dispatchers.IO) { Graph.appRepository.loadLaunchableApps() }
        }.getOrDefault(emptyList())
    }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f)),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(colors.pageBg),
        ) {
            // 顶部拖拽条：与「新建密码」弹窗保持一致，标题位置对齐
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.divider),
                )
            }
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
                    text = Graph.appContext.getString(R.string.s_select_app),
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.size(48.dp))
            }
            // 搜索
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.searchBarBg)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                    singleLine = true,
                    cursorBrush = SolidColor(colors.brand),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text(Graph.appContext.getString(R.string.s_search_apps), color = colors.textSecondary, fontSize = 15.sp)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (query.isBlank()) {
                    item(key = "custom") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelected(null) }
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
                                Icon(
                                    imageVector = Icons.Filled.VpnKey,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(Graph.appContext.getString(R.string.s_custom), color = colors.textPrimary, fontSize = 16.sp)
                        }
                    }
                }
                items(filtered, key = { it.packageName }) { app ->
                    val appIcon = rememberAppIcon(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(app) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconAvatar(drawable = appIcon, initial = app.label.take(1), size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = app.label,
                            color = colors.textPrimary,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        }
    }
}
