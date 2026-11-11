package cc.nanio.pass.ui.security



import cc.nanio.pass.R
import cc.nanio.pass.Graph
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.nanio.pass.data.model.PasswordEntity
import cc.nanio.pass.security.SecurityChecks
import cc.nanio.pass.ui.AppViewModel
import cc.nanio.pass.ui.components.AppIconAvatar
import cc.nanio.pass.ui.components.PageScaffold
import cc.nanio.pass.ui.components.TopBar
import cc.nanio.pass.ui.theme.NanioTheme
import cc.nanio.pass.ui.util.rememberAppIcon

/**
 * 安全性页：纯本地的弱密码 / 重复使用提示（应用不联网）。
 *
 * 不做分组展示，每条风险直接以「名称 + 描述」的列表项呈现；只提示密码本身的问题。
 */
@Composable
fun SecurityScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    val colors = NanioTheme.colors
    val passwords = vm.passwords

    val riskyItems = remember(passwords) {
        SecurityChecks.weakEntries(passwords).map { item ->
            val hint = when (SecurityChecks.weakReason(item.password)) {
                SecurityChecks.WeakReason.EASILY_GUESSED -> Graph.appContext.getString(R.string.s_easily_guessed)
                else -> Graph.appContext.getString(R.string.s_weak_password)
            }
            item to hint
        }
    }

    PageScaffold {
        TopBar(title = Graph.appContext.getString(R.string.s_security), onBack = onBack)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
        ) {
            items(riskyItems, key = { it.first.id }) { (item, hint) ->
                RiskRow(item = item, hint = hint, onClick = { onOpen(item.id) })
            }
            if (riskyItems.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colors.success,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(text = Graph.appContext.getString(R.string.s_no_security_issues_found), color = colors.textSecondary, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun RiskRow(item: PasswordEntity, hint: String, onClick: () -> Unit) {
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
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = hint,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(18.dp),
        )
    }
}
