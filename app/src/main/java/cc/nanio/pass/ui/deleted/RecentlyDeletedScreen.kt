package cc.nanio.pass.ui.deleted



import cc.nanio.pass.R
import cc.nanio.pass.Graph
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.components.AppIconAvatar
import cc.nanio.pass.ui.components.ConfirmDialog
import cc.nanio.pass.ui.components.EmptyView
import cc.nanio.pass.ui.components.PageScaffold
import cc.nanio.pass.ui.components.TopBar
import cc.nanio.pass.ui.theme.NanioTheme
import cc.nanio.pass.ui.util.rememberAppIcon

private const val RETENTION_DAYS = 30L
private const val DAY_MS = 24 * 60 * 60 * 1000L

@Composable
fun RecentlyDeletedScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onRestore: (Long) -> Unit,
    onOpen: (Long) -> Unit = {},
) {
    val colors = NanioTheme.colors
    var confirmClear by remember { mutableStateOf(false) }

    PageScaffold {
        TopBar(
            title = Graph.appContext.getString(R.string.s_recently_deleted),
            onBack = onBack,
            actions = {
                if (vm.deleted.isNotEmpty()) {
                    Text(
                        text = Graph.appContext.getString(R.string.s_clear_all),
                        color = colors.brand,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clickable { confirmClear = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            },
        )
        if (vm.deleted.isEmpty()) {
            EmptyView(title = Graph.appContext.getString(R.string.s_recently_deleted_is_empty), icon = Icons.Filled.Delete, subtitle = Graph.appContext.getString(R.string.s_deleted_passwords_will_appear_here))
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
                items(vm.deleted, key = { it.id }) { item ->
                    val icon = rememberAppIcon(item.packageName)
                    val deletedAt = item.deletedAt ?: 0L
                    val usedDays = ((System.currentTimeMillis() - deletedAt) / DAY_MS).coerceAtLeast(0)
                    val leftDays = (RETENTION_DAYS - usedDays).coerceAtLeast(0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.cardBg)
                            .clickable { onOpen(item.id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIconAvatar(drawable = icon, initial = item.initial, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.label,
                                color = colors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = Graph.appContext.getString(R.string.s_restore),
                            color = colors.brand,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clickable { onRestore(item.id) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = Graph.appContext.getString(R.string.s_clear_recently_deleted),
            message = Graph.appContext.getString(R.string.s_this_cannot_be_undone_once_cleared),
            confirmText = Graph.appContext.getString(R.string.s_clear_all),
            danger = true,
            onConfirm = {
                confirmClear = false
                vm.purgeAllDeleted()
            },
            onDismiss = { confirmClear = false },
        )
    }
}
