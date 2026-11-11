package cc.nanio.pass.ui.passwordlist



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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.nanio.pass.data.model.PasswordEntity
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.components.AddFab
import cc.nanio.pass.ui.components.AlphabetIndexer
import cc.nanio.pass.ui.components.AppIconAvatar
import cc.nanio.pass.ui.components.EmptyView
import cc.nanio.pass.ui.components.PageScaffold
import cc.nanio.pass.ui.components.SectionHeader
import cc.nanio.pass.ui.components.TopBar
import cc.nanio.pass.ui.theme.NanioTheme
import cc.nanio.pass.ui.util.PinyinUtil
import cc.nanio.pass.ui.util.rememberAppIcon
import kotlinx.coroutines.launch

private data class PwdGroup(val letter: String, val items: List<PasswordEntity>)

@Composable
fun PasswordListScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: () -> Unit,
    onOpenSearch: () -> Unit = {},
) {
    val colors = NanioTheme.colors
    val groups = remember(vm.passwords) { buildGroups(vm.passwords) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 分组标题在 LazyColumn 中的下标，用于字母索引跳转
    val letterIndex: Map<String, Int> = remember(groups) {
        val map = mutableMapOf<String, Int>()
        var index = 0
        groups.forEach { group ->
            map[group.letter] = index
            index += 1 + group.items.size
        }
        map
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PageScaffold {
            TopBar(
                title = Graph.appContext.getString(R.string.s_all_passwords),
                onBack = onBack,
                actions = {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { onOpenSearch() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = Graph.appContext.getString(R.string.s_search),
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
            )
            if (vm.passwords.isEmpty()) {
                EmptyView(
                    title = Graph.appContext.getString(R.string.s_nothing_here_yet),
                    subtitle = Graph.appContext.getString(R.string.s_tap_the_bottom_right_button_to_create_your_first),
                    icon = Icons.Filled.Lock,
                )
            } else {
                LazyColumn(
                    state = listState,
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
                    groups.forEach { group ->
                        item(key = "header_${group.letter}") {
                            SectionHeader(text = group.letter)
                        }
                        items(group.items, key = { it.id }) { item ->
                            Box(modifier = Modifier.animateItem()) {
                                PasswordRow(item = item, onClick = { onOpen(item.id) })
                            }
                        }
                    }
                }
            }
        }

        if (vm.passwords.isNotEmpty()) {
            AlphabetIndexer(
                onLetterSelected = { letter ->
                    val target = groups.indexOfFirst { it.letter >= letter }
                        .let { if (it >= 0) it else groups.lastIndex }
                    if (target >= 0) {
                        val index = letterIndex[groups[target].letter] ?: return@AlphabetIndexer
                        scope.launch { listState.scrollToItem(index) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp),
            )
        }

        AddFab(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp)
                .navigationBarsPadding(),
            contentDescription = Graph.appContext.getString(R.string.s_tap_to_create_a_password),
        )
    }
}

private fun buildGroups(items: List<PasswordEntity>): List<PwdGroup> =
    items
        .sortedWith(compareBy({ PinyinUtil.sortKey(it.label) }, { it.label.lowercase() }))
        .groupBy { PinyinUtil.initialOf(it.label) }
        .toList()
        // 与索引条保持一致：A–Z 在前，# 分组放最后
        .sortedWith(compareBy({ if (it.first == "#") 1 else 0 }, { it.first }))
        .map { (letter, list) -> PwdGroup(letter, list) }

@Composable
private fun PasswordRow(item: PasswordEntity, onClick: () -> Unit) {
    val colors = NanioTheme.colors
    val icon = rememberAppIcon(item.packageName)
    val risky = item.password.length < 8 || item.password.all { it.isDigit() }
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
        if (risky) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.danger),
            )
        }
    }
}
