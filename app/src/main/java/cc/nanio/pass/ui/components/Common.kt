package cc.nanio.pass.ui.components



import cc.nanio.pass.R
import cc.nanio.pass.Graph
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import cc.nanio.pass.ui.theme.NanioTheme

/** 页面通用背景 + 内容容器 */
@Composable
fun PageScaffold(
    modifier: Modifier = Modifier,
    statusBarPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NanioTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (statusBarPadding) Modifier.statusBarsPadding() else Modifier)
            .background(colors.pageBg),
        content = content,
    )
}

/** 白色卡片：16dp 圆角，可选点击。 */
@Composable
fun NanioCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NanioTheme.colors
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(colors.cardBg)
    Column(
        modifier = if (onClick != null) base.clickable { onClick() } else base,
        content = content,
    )
}

/** 顶部栏：返回 + 标题 + 右侧动作。 */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = NanioTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = leadingIcon ?: Icons.Rounded.KeyboardArrowLeft,
                    contentDescription = if (leadingIcon != null) Graph.appContext.getString(R.string.s_close) else Graph.appContext.getString(R.string.s_back),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(if (leadingIcon != null) 22.dp else 26.dp),
                )
            }
        }
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) 16.dp else 0.dp),
        )
        actions()
    }
}

/** 二级页标题（大标题，类似设置页风格） */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier) {
    val colors = NanioTheme.colors
    Text(
        text = title,
        color = colors.textPrimary,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp),
    )
}

/** 首页入口卡片（2 列宫格中的一格）：图标 + 标题 + 数量。 */
@Composable
fun EntranceCard(
    icon: ImageVector,
    title: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NanioTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (pressed) colors.cardBgPress else colors.cardBg)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.brand,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            color = colors.textPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = count.toString(),
            color = colors.textSecondary,
            fontSize = 12.sp,
        )
    }
}

/** 设置/列表通用行：标题 + 可选摘要 + 右侧内容（箭头/开关/文本）。 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = NanioTheme.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable { onClick() } else it }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!summary.isNullOrEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = summary,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp)
                    .height(0.5.dp)
                    .background(colors.divider),
            )
        }
    }
}

/** 分组标题（列表 A/B/C 或设置分组） */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val colors = NanioTheme.colors
    Text(
        text = text,
        color = colors.textSecondary,
        fontSize = 14.sp,
        modifier = modifier.padding(start = 28.dp, top = 18.dp, bottom = 6.dp),
    )
}

/** 应用图标 / 首字母头像。图标取不到（应用卸载或未绑定）时回退首字母。 */
@Composable
fun AppIconAvatar(
    drawable: Drawable?,
    initial: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    corner: Dp = 10.dp,
) {
    val colors = NanioTheme.colors
    val bitmap: ImageBitmap? = remember(drawable) {
        drawable?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) {
        androidx.compose.foundation.Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(corner)),
        )
    } else {
        val palette = listOf(
            Color(0xFF3482FF), Color(0xFF5B8DEF), Color(0xFF7C6BF5),
            Color(0xFFE86A6A), Color(0xFFEF9C3E), Color(0xFF37A46B),
        )
        val letter = initial.trim().take(1).uppercase()
        if (letter.isEmpty()) {
            // 名称也为空（新建表单初始态）：中性占位，不画彩色问号
            Box(
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(corner))
                    .background(colors.cardBgSecondary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.VpnKey,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(size * 0.44f),
                )
            }
        } else {
            val bg = palette[letter[0].code.mod(palette.size)]
            Box(
                modifier = modifier
                    .size(size)
                    .clip(RoundedCornerShape(corner))
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    color = Color.White,
                    fontSize = (size.value * 0.44f).sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** 搜索头：白色圆角条 + 放大镜 + hint。 */
@Composable
fun SearchHeader(
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NanioTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.searchBarBg)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = hint, color = colors.textSecondary, fontSize = 15.sp)
    }
}

/** 悬浮添加按钮：58dp 圆形 + 阴影。 */
@Composable
fun AddFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Add,
    contentDescription: String = Graph.appContext.getString(R.string.s_add),
) {
    val colors = NanioTheme.colors
    Box(
        modifier = modifier
            .size(58.dp)
            .shadow(14.dp, CircleShape, spotColor = colors.fabShadow)
            .clip(CircleShape)
            .background(colors.brand)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** TOTP 剩余时间进度环：24dp，>15s 蓝 / 6~15s 橙 / <=5s 红。 */
@Composable
fun TotpRing(
    remainingSeconds: Int,
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val colors = NanioTheme.colors
    val color = when {
        remainingSeconds > 15 -> colors.brand
        remainingSeconds > 5 -> colors.warning
        else -> colors.danger
    }
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "totpRing")
    val strokeWidth = with(LocalDensity.current) { (size * 0.1f).toPx() }
    Canvas(modifier = modifier.size(size)) {
        drawCircle(
            color = colors.ringTrack,
            radius = (this.size.minDimension - strokeWidth) / 2f,
            style = Stroke(width = strokeWidth),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = 360f * animated,
            useCenter = false,
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(this.size.width - strokeWidth, this.size.height - strokeWidth),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

/** 字母索引条（A–Z + #），拖动/点击回调字母。 */
@Composable
fun AlphabetIndexer(
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NanioTheme.colors
    // 固定展示 A-Z + #，不随分组数量变化
    val letters = remember { ('A'..'Z').map { it.toString() } + "#" }
    var activeLetter by remember { mutableStateOf<String?>(null) }
    var heightPx by remember { mutableStateOf(0f) }

    fun letterAt(y: Float): String? {
        if (heightPx <= 0f) return null
        val idx = (y / heightPx * letters.size).toInt().coerceIn(0, letters.size - 1)
        return letters[idx]
    }

    Box(modifier = modifier.fillMaxHeight(0.72f)) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(22.dp)
                .fillMaxHeight()
                .onSizeChanged { heightPx = it.height.toFloat() }
                .pointerInput(OnLetterKey) {
                    detectTapGestures { offset ->
                        letterAt(offset.y)?.let {
                            activeLetter = it
                            onLetterSelected(it)
                        }
                    }
                }
                .pointerInput(OnLetterKey) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            letterAt(offset.y)?.let {
                                activeLetter = it
                                onLetterSelected(it)
                            }
                        },
                        onDragEnd = { activeLetter = null },
                        onDragCancel = { activeLetter = null },
                    ) { change, _ ->
                        letterAt(change.position.y)?.let {
                            if (it != activeLetter) {
                                activeLetter = it
                                onLetterSelected(it)
                            }
                        }
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (activeLetter == letter) colors.brand.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter,
                        color = if (activeLetter == letter) colors.brand else colors.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = if (activeLetter == letter) FontWeight.Bold else FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // 拖动/点击时跟随的字母气泡
        val current = activeLetter
        if (current != null && heightPx > 0f) {
            val index = letters.indexOf(current).coerceAtLeast(0)
            val itemHeightPx = heightPx / letters.size
            val centerYPx = itemHeightPx * index + itemHeightPx / 2f
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        IntOffset(
                            x = (-52).dp.roundToPx(),
                            y = (centerYPx - 22.dp.toPx()).toInt(),
                        )
                    }
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.brand.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = current,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

const val OnLetterKey = "alphabet_letters"

/** 空状态 */
@Composable
fun EmptyView(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val colors = NanioTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(text = title, color = colors.textSecondary, fontSize = 15.sp)
        if (!subtitle.isNullOrEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(text = subtitle, color = colors.textTertiary, fontSize = 13.sp)
        }
    }
}

/** 输入框：卡片底 + 浮动标签 + hint，支持错误红框。 */
@Composable
fun NanioTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    minHeight: Dp = 56.dp,
    trailing: @Composable (() -> Unit)? = null,
    onDone: (() -> Unit)? = null,
) {
    val colors = NanioTheme.colors
    val border = if (isError) colors.danger else Color.Transparent
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.cardBgSecondary)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text = label, color = if (isError) colors.danger else colors.textSecondary, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(text = placeholder, color = colors.textTertiary, fontSize = 16.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 16.sp),
                    singleLine = singleLine,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                    visualTransformation = visualTransformation,
                    cursorBrush = SolidColor(colors.brand),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight - 30.dp),
                )
            }
            if (trailing != null) trailing()
        }
    }
}

/** 密码输入框（带眼睛切换） */


/**
 * 全屏抽屉弹层：底部滑入、占满高度、顶部圆角 20dp。
 * header：左关闭 / 中标题 / 右动作（如保存）。
 */
@Composable
fun DrawerDialogScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    actionEnabled: Boolean = true,
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = NanioTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
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
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = Graph.appContext.getString(R.string.s_close),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = title,
                color = colors.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (actionText != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(enabled = actionEnabled) { onAction?.invoke() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = actionText,
                        tint = if (actionEnabled) colors.brand else colors.textTertiary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }
        content()
    }
}

/** 确认弹窗 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String?,
    confirmText: String = Graph.appContext.getString(R.string.s_ok),
    cancelText: String = Graph.appContext.getString(R.string.s_cancel),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    val colors = NanioTheme.colors
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.cardBg)
                .padding(24.dp),
        ) {
            Text(text = title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            if (!message.isNullOrEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(text = message, color = colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Spacer(Modifier.height(22.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = cancelText,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = confirmText,
                    color = if (danger) colors.danger else colors.brand,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onConfirm() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 轻提示（沿用系统 Toast，无自定义 UI）。 */
fun Context.shortToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}

/** 列表行右侧箭头 */
@Composable
fun RowChevron() {
    Icon(
        imageVector = Icons.Rounded.KeyboardArrowLeft,
        contentDescription = null,
        tint = NanioTheme.colors.textTertiary,
        modifier = Modifier
            .size(20.dp)
            .rotate(180f),
    )
}
