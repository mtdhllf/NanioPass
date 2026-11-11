package cc.nanio.pass.autofill



import cc.nanio.pass.R
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import cc.nanio.pass.Graph
import cc.nanio.pass.data.model.PasswordEntity
import cc.nanio.pass.security.BiometricHelper
import cc.nanio.pass.ui.components.AppIconAvatar
import cc.nanio.pass.ui.components.EmptyView
import cc.nanio.pass.ui.components.NanioCard
import cc.nanio.pass.ui.components.TopBar
import cc.nanio.pass.ui.theme.NanioTheme
import cc.nanio.pass.ui.util.rememberAppIcon
import kotlinx.coroutines.runBlocking

/**
 * 自动填充「使用其他密码…」选择页（FillResponse/Dataset 级认证）。
 *
 * 选择一条密码后构造 Dataset，通过 [AutofillManager.EXTRA_AUTHENTICATION_RESULT] 回传给系统完成填充。
 */
class AutofillPickerActivity : FragmentActivity() {

    private var usernameId: AutofillId? = null
    private var passwordId: AutofillId? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        usernameId = intent.getParcelableExtra(NanioAutofillService.EXTRA_USERNAME_ID)
        passwordId = intent.getParcelableExtra(NanioAutofillService.EXTRA_PASSWORD_ID)
        if (usernameId == null && passwordId == null) {
            finishAsCancelled()
            return
        }
        val entries = runCatching { runBlocking { Graph.repository.listActive() } }.getOrDefault(emptyList())
            .sortedWith(compareByDescending<PasswordEntity> { it.lastUsedAt }.thenBy { it.label })

        setContent {
            NanioTheme {
                val colors = NanioTheme.colors
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.pageBg)
                        .statusBarsPadding(),
                ) {
                    TopBar(title = Graph.appContext.getString(R.string.s_select_a_password_to_fill), onBack = { finishAsCancelled() })
                    if (entries.isEmpty()) {
                        EmptyView(
                            title = Graph.appContext.getString(R.string.s_no_passwords_available_to_fill_yet),
                            subtitle = Graph.appContext.getString(R.string.s_please_add_a_password_in_naniopass_first),
                            icon = Icons.Filled.VpnKey,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                top = 4.dp,
                                bottom = 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(entries, key = { it.id }) { entry ->
                                PickerRow(entry = entry, onClick = { onEntrySelected(entry) })
                            }
                        }
                        Text(
                            text = Graph.appContext.getString(R.string.s_your_selection_will_be_filled_into_the_current_f),
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    private fun onEntrySelected(entry: PasswordEntity) {
        if (biometricEnabled() && runCatching { BiometricHelper.canAuthenticate(this) }.getOrDefault(false)) {
            BiometricHelper.authenticate(
                activity = this,
                title = Graph.appContext.getString(R.string.s_verify_to_fill_the_password),
                subtitle = entry.label,
                onSuccess = { finishWith(entry) },
                onError = { finishAsCancelled() },
            )
        } else {
            finishWith(entry)
        }
    }

    private fun finishWith(entry: PasswordEntity) {
        val view = RemoteViews(packageName, cc.nanio.pass.R.layout.autofill_dataset_item).apply {
            setTextViewText(cc.nanio.pass.R.id.autofill_item_title, entry.label)
            setTextViewText(
                cc.nanio.pass.R.id.autofill_item_subtitle,
                entry.account.ifEmpty { Graph.appContext.getString(R.string.s_tap_to_fill_a_password) },
            )
        }
        val builder = Dataset.Builder(view)
        usernameId?.let { builder.setValue(it, AutofillValue.forText(entry.account)) }
        passwordId?.let { builder.setValue(it, AutofillValue.forText(entry.password)) }
        val dataset = runCatching { builder.build() }.getOrNull()
        if (dataset == null) {
            finishAsCancelled()
            return
        }
        val result = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun finishAsCancelled() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun biometricEnabled(): Boolean =
        getSharedPreferences("settings", 0).getBoolean("biometric_enabled", false)
}

@Composable
private fun PickerRow(entry: PasswordEntity, onClick: () -> Unit) {
    val colors = NanioTheme.colors
    val icon = rememberAppIcon(entry.packageName)
    NanioCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconAvatar(drawable = icon, initial = entry.initial, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    color = colors.textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.account.ifEmpty { Graph.appContext.getString(R.string.s_no_account) },
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
