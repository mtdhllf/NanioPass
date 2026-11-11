package cc.nanio.pass.ui.totp

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.nanio.pass.data.model.PasswordEntity
import cc.nanio.pass.totp.TotpConfig
import cc.nanio.pass.totp.TotpEngine
import cc.nanio.pass.ui.components.TotpRing
import cc.nanio.pass.ui.theme.NanioColors
import kotlinx.coroutines.delay

/** 250ms 刷新的当前时间：验证码按秒变，进度环按毫秒平滑走。 */
@Composable
fun rememberNowTicker(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250L)
        }
    }
    return now
}

fun PasswordEntity.toTotpConfig(): TotpConfig? {
    val secret = totpSecret ?: return null
    return TotpConfig(
        secret = secret,
        issuer = totpIssuer,
        account = totpAccount,
        algorithm = totpAlgorithm,
        digits = totpDigits,
    )
}

private fun formatCode(code: String): String {
    if (code.length != 6) return code
    return "${code.substring(0, 3)} ${code.substring(3)}"
}

/** 验证码 + 进度环，列表与详情复用。 */
@Composable
fun TotpCode(
    config: TotpConfig,
    colors: NanioColors,
    modifier: Modifier = Modifier,
    codeFontSize: Int = 22,
    ringSize: Int = 22,
    showRing: Boolean = true,
) {
    val now = rememberNowTicker()
    val code = remember(config.secret, config.algorithm, config.digits, now / 1000) {
        runCatching { TotpEngine.generate(config, now) }.getOrNull()
    }
    val remaining = remember(now / 1000) { runCatching { TotpEngine.remainingSeconds(now) }.getOrDefault(30) }
    val progress = remember(now / 250) { runCatching { TotpEngine.progress(now) }.getOrDefault(0f) }

    androidx.compose.foundation.layout.Row(modifier = modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            text = code?.let { formatCode(it) } ?: "-- ---",
            color = colors.textPrimary,
            fontSize = codeFontSize.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
        )
        if (showRing) {
            Spacer(Modifier.width(8.dp))
            TotpRing(remainingSeconds = remaining, progress = progress, size = ringSize.dp)
        }
    }
}
