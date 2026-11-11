package cc.nanio.pass.security


import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * 生物识别封装（androidx.biometric）。
 * 使用「生物识别 / 设备凭据」解锁应用，密钥本身仍在 Android Keystore。
 */
object BiometricHelper {

    private const val AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun canAuthenticate(context: Context): Boolean {
        return BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String? = null,
        subtitle: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val promptTitle = title ?: activity.getString(cc.nanio.pass.R.string.s_unlock_naniopass)
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
            },
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(promptTitle)
            .setAllowedAuthenticators(AUTHENTICATORS)
        if (!subtitle.isNullOrBlank()) builder.setSubtitle(subtitle)
        prompt.authenticate(builder.build())
    }
}
