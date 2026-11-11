package cc.nanio.pass.ui.search



import cc.nanio.pass.R
import cc.nanio.pass.Graph
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.nanio.pass.data.model.PasswordEntity
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.components.AppIconAvatar
import cc.nanio.pass.ui.components.EmptyView
import cc.nanio.pass.ui.components.PageScaffold
import cc.nanio.pass.ui.theme.NanioTheme
import cc.nanio.pass.ui.util.PinyinUtil
import cc.nanio.pass.ui.util.rememberAppIcon

/**
 * 独立搜索页：顶部搜索栏 + 退出按钮，下方实时匹配的密码列表。
 */
@Composable
fun SearchScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val colors = NanioTheme.colors
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val results = remember(vm.passwords, query) {
        val q = query.trim()
        val base = if (q.isEmpty()) {
            vm.passwords
        } else {
            vm.passwords.filter {
                it.label.contains(q, ignoreCase = true) ||
                    it.account.contains(q, ignoreCase = true) ||
                    (it.domain?.contains(q, ignoreCase = true) ?: false)
            }
        }
        base.sortedWith(compareBy({ PinyinUtil.sortKey(it.label) }, { it.label.lowercase() }))
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    PageScaffold {
        // 顶部：搜索栏 + 退出按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(text = Graph.appContext.getString(R.string.s_search), color = colors.textTertiary, fontSize = 17.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.textPrimary, fontSize = 17.sp),
                    cursorBrush = SolidColor(colors.brand),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = Graph.appContext.getString(R.string.s_clear),
                    tint = colors.textTertiary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { query = "" },
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = Graph.appContext.getString(R.string.s_exit),
                color = colors.brand,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        keyboard?.hide()
                        onBack()
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }

        if (query.isNotBlank() && results.isEmpty()) {
            EmptyView(title = Graph.appContext.getString(R.string.s_no_matching_passwords_found))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
            ) {
                items(results, key = { it.id }) { item ->
                    SearchRow(item = item, onClick = { onOpen(item.id) })
                }
            }
        }
    }
}

@Composable
private fun SearchRow(item: PasswordEntity, onClick: () -> Unit) {
    val colors = NanioTheme.colors
    val icon = rememberAppIcon(item.packageName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.cardBg)
            .clickable { onClick() }
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
