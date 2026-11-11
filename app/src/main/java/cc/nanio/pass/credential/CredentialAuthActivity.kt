package cc.nanio.pass.credential



import cc.nanio.pass.R
import android.app.Activity
import android.os.Bundle
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import cc.nanio.pass.Graph
import cc.nanio.pass.security.BiometricHelper
import kotlinx.coroutines.runBlocking

/**
 * 用户点选系统凭据列表中的某一项后进入本页：校验身份后把密码回传给 Credential Manager。
 * 页面透明，不展示 UI（除系统生物识别弹层）。
 */
class CredentialAuthActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)
        android.util.Log.d(
            "NanioCredential",
            "auth onCreate request=" + (request != null) + " entryId=" + entryId +
                " extras=" + intent.extras?.keySet(),
        )
        if (request == null || entryId < 0) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val entity = runBlocking { runCatching { Graph.repository.getById(entryId) }.getOrNull() }
        android.util.Log.d("NanioCredential", "auth entity=" + (entity != null))
        if (entity == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val userWantsBio = getSharedPreferences("settings", 0)
            .getBoolean("biometric_enabled", false)
        val canBio = userWantsBio &&
            runCatching { BiometricHelper.canAuthenticate(this) }.getOrDefault(false)
        android.util.Log.d("NanioCredential", "auth canAuthenticate=" + canBio + " userWantsBio=" + userWantsBio)
        if (!canBio) {
            finishWithCredential(entity.account, entity.password)
            return
        }
        BiometricHelper.authenticate(
            activity = this,
            title = Graph.appContext.getString(R.string.s_verify_to_use_passwords),
            subtitle = entity.label,
            onSuccess = { finishWithCredential(entity.account, entity.password) },
            onError = {
                setResult(Activity.RESULT_CANCELED)
                finish()
            },
        )
    }

    private fun finishWithCredential(username: String, password: String) {
        val response = GetCredentialResponse(PasswordCredential(username, password))
        val result = android.content.Intent()
        PendingIntentHandler.setGetCredentialResponse(result, response)
        android.util.Log.d("NanioCredential", "auth finishWithCredential user=" + username + " request=" + (PendingIntentHandler.retrieveProviderGetCredentialRequest(intent) != null))
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    companion object {
        const val EXTRA_ENTRY_ID = "credential_entry_id"
    }
}
