package cc.nanio.pass.autofill




import cc.nanio.pass.Graph
import cc.nanio.pass.R
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import androidx.fragment.app.FragmentActivity
import cc.nanio.pass.security.BiometricHelper

/**
 * 自动填充认证页：开启生物识别解锁时，选择某条候选密码前先验证身份，
 * 验证通过后用 Dataset 回传给系统（Material/Android 标准做法）。
 */
class AutofillAuthActivity : FragmentActivity() {

    private var usernameId: AutofillId? = null
    private var passwordId: AutofillId? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usernameId = intent.getParcelableExtra(NanioAutofillService.EXTRA_USERNAME_ID)
        passwordId = intent.getParcelableExtra(NanioAutofillService.EXTRA_PASSWORD_ID)

        if (!BiometricHelper.canAuthenticate(this)) {
            finishWithDataset()
            return
        }
        BiometricHelper.authenticate(
            activity = this,
            title = Graph.appContext.getString(R.string.s_verify_to_fill_the_password),
            subtitle = intent.getStringExtra(NanioAutofillService.EXTRA_TITLE),
            onSuccess = {
                finishWithDataset()
            },
            onError = {
                setResult(Activity.RESULT_CANCELED)
                finish()
            },
        )
    }

    private fun finishWithDataset() {
        val account = intent.getStringExtra(NanioAutofillService.EXTRA_ACCOUNT).orEmpty()
        val password = intent.getStringExtra(NanioAutofillService.EXTRA_PASSWORD).orEmpty()
        val title = intent.getStringExtra(NanioAutofillService.EXTRA_TITLE).orEmpty()

        val view = android.widget.RemoteViews(packageName, cc.nanio.pass.R.layout.autofill_dataset_item).apply {
            setTextViewText(cc.nanio.pass.R.id.autofill_item_title, title)
            setTextViewText(cc.nanio.pass.R.id.autofill_item_subtitle, account)
        }
        val builder = Dataset.Builder(view)
        usernameId?.let { builder.setValue(it, AutofillValue.forText(account)) }
        passwordId?.let { builder.setValue(it, AutofillValue.forText(password)) }
        val dataset = runCatching { builder.build() }.getOrNull()
        if (dataset == null) {
            setResult(Activity.RESULT_CANCELED)
        } else {
            val result = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
            setResult(Activity.RESULT_OK, result)
        }
        finish()
    }
}
