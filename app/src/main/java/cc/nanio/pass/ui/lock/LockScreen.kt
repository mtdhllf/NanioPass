package cc.nanio.pass.ui.lock



import cc.nanio.pass.R
import cc.nanio.pass.Graph
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import cc.nanio.pass.security.BiometricHelper
import cc.nanio.pass.ui.theme.NanioTheme

/**
 * 应用锁：生物识别解锁。设备不支持时直接放行（不阻塞用户）。
 */
@Composable
fun LockScreen(
    activity: FragmentActivity,
    onUnlock: () -> Unit,
) {
    val colors = NanioTheme.colors

    fun tryUnlock() {
        if (!BiometricHelper.canAuthenticate(activity)) {
            onUnlock()
            return
        }
        BiometricHelper.authenticate(
            activity = activity,
            onSuccess = { onUnlock() },
            onError = { /* 用户取消或失败，停留在锁屏 */ },
        )
    }

    LaunchedEffect(Unit) { tryUnlock() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(colors.brand.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = null,
                    tint = colors.brand,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(text = "NanioPass", color = colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(text = Graph.appContext.getString(R.string.s_verify_your_identity_to_view_the_password), color = colors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(28.dp))
            Text(
                text = Graph.appContext.getString(R.string.s_unlock),
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .background(colors.brand)
                    .clickable { tryUnlock() }
                    .padding(horizontal = 40.dp, vertical = 12.dp),
            )
        }
    }
}
