package cc.nanio.pass.credential


import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.graphics.drawable.Icon
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import cc.nanio.pass.Graph
import cc.nanio.pass.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Android 14+ Credential Provider：让系统 Credential Manager（以及支持它的应用）把本应用当密码来源。
 *
 * 本轮实现取密码（password credential）：
 * - 系统调用 onBeginGetCredentialRequestRequest 时返回候选条目；
 * - 用户点选后启动 [CredentialAuthActivity]，验证身份后回传 GetCredentialResponse。
 * Passkey（PublicKeyCredential/WebAuthn）未实现。
 */
class NanioCredentialProviderService : CredentialProviderService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        android.util.Log.d("NanioCredential", "onBeginGetCredentialRequest options=" + request.beginGetCredentialOptions.size)
        val option = request.beginGetCredentialOptions
            .filterIsInstance<BeginGetPasswordOption>()
            .firstOrNull()
        if (option == null) {
            callback.onResult(BeginGetCredentialResponse())
            return
        }
        val callingPackage = request.callingAppInfo?.packageName
        scope.launch {
            val entries = runCatching { loadCandidates(callingPackage) }.getOrDefault(emptyList())
            android.util.Log.d("NanioCredential", "candidates=" + entries.size + " calling=" + callingPackage)
            val builder = BeginGetCredentialResponse.Builder()
            var added = 0
            entries.forEach { entity ->
                val intent = Intent(this@NanioCredentialProviderService, CredentialAuthActivity::class.java).apply {
                    putExtra(CredentialAuthActivity.EXTRA_ENTRY_ID, entity.id)
                }
                val pending = PendingIntent.getActivity(
                    this@NanioCredentialProviderService,
                    entity.id.toInt(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                ) ?: run {
                    android.util.Log.w("NanioCredential", "pendingIntent null for id=" + entity.id)
                    return@forEach
                }
                val entry = PasswordCredentialEntry.Builder(
                    this@NanioCredentialProviderService,
                    entity.account.ifEmpty { entity.label },
                    pending,
                    option,
                ).setDisplayName(entity.label).build()
                builder.addCredentialEntry(entry)
                added++
            }
            android.util.Log.d("NanioCredential", "response entries added=" + added)
            callback.onResult(builder.build())
        }
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        // 仅支持“密码类凭据”的创建请求；passkey / 其他类型返回空响应
        if (request !is BeginCreatePasswordCredentialRequest) {
            callback.onResult(BeginCreateCredentialResponse())
            return
        }
        // 注意：不要加 FLAG_ACTIVITY_NEW_TASK，否则系统无法从 Activity 结果里拿到
        // CreateCredentialResponse，创建弹窗会一直停在原地。
        val intent = Intent(this, CredentialCreateActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            CREATE_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        if (pending == null) {
            callback.onResult(BeginCreateCredentialResponse())
            return
        }
        val entry = CreateEntry.Builder("NanioPass", pending)
            .setDescription(Graph.appContext.getString(R.string.s_save_to_naniopass))
            .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
            .setPasswordCredentialCount(1)
            .build()
        callback.onResult(
            BeginCreateCredentialResponse.Builder()
                .addCreateEntry(entry)
                .build(),
        )
    }

    override fun onClearCredentialStateRequest(
        request: androidx.credentials.provider.ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, androidx.credentials.exceptions.ClearCredentialException>,
    ) {
        callback.onResult(null)
    }

    private companion object {
        private const val CREATE_REQUEST_CODE = 0x5CA6
    }

    private suspend fun loadCandidates(callingPackage: String?): List<cc.nanio.pass.data.model.PasswordEntity> {
        val all = Graph.repository.listActive().filter { it.password.isNotEmpty() }
        if (callingPackage.isNullOrBlank()) return all.take(10)
        val bound = all.filter { it.packageName == callingPackage }
        return (if (bound.isNotEmpty()) bound else all).take(10)
    }
}
