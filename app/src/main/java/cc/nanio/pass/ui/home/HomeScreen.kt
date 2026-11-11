package cc.nanio.pass.ui.home



import cc.nanio.pass.R
import cc.nanio.pass.Graph
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.components.AddFab
import cc.nanio.pass.ui.components.EntranceCard
import cc.nanio.pass.ui.components.PageScaffold
import cc.nanio.pass.ui.components.SearchHeader
import cc.nanio.pass.ui.theme.NanioTheme

private data class HomeEntry(
    val title: String,
    val icon: ImageVector,
    val count: Int,
    val onClick: () -> Unit,
)

@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenPasswords: () -> Unit,
    onOpenTotp: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenDeleted: () -> Unit,
    onOpenSettings: () -> Unit,
    onAdd: () -> Unit,
    onOpenSearch: () -> Unit = {},
) {
    val colors = NanioTheme.colors
    val passwords = vm.passwords
    val entries = listOf(
        HomeEntry(Graph.appContext.getString(R.string.s_all_passwords), Icons.Filled.Lock, passwords.size, onOpenPasswords),
        HomeEntry(Graph.appContext.getString(R.string.s_authenticator), Icons.Filled.VpnKey, passwords.count { it.hasTotp }, onOpenTotp),
        HomeEntry(
            Graph.appContext.getString(R.string.s_security),
            Icons.Filled.GppGood,
            cc.nanio.pass.security.SecurityChecks.riskCount(passwords),
            onOpenSecurity,
        ),
        HomeEntry(Graph.appContext.getString(R.string.s_recently_deleted), Icons.Filled.DeleteOutline, vm.deleted.size, onOpenDeleted),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        PageScaffold {
            // 顶部标题栏：标题 + 设置入口
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Graph.appContext.getString(R.string.s_password),
                    color = colors.textPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = Graph.appContext.getString(R.string.s_settings),
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            SearchHeader(hint = Graph.appContext.getString(R.string.s_search_passwords), onClick = onOpenSearch)
            Spacer(Modifier.height(4.dp))
            androidx.compose.foundation.layout.BoxWithConstraints {
                // 横屏/平板：宽度足够时一排 3 张卡片
                val columns = if (maxWidth >= 600.dp) 3 else 2
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(entries, key = { it.title }) { entry ->
                        EntranceCard(
                            icon = entry.icon,
                            title = entry.title,
                            count = entry.count,
                            onClick = entry.onClick,
                        )
                    }
                }
            }
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
