package cc.nanio.pass.autofill


import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.content.ComponentName
import android.content.Intent
import android.os.CancellationSignal
import android.util.Log
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import cc.nanio.pass.Graph
import cc.nanio.pass.R
import cc.nanio.pass.data.model.PasswordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 自动填充服务。
 *
 * 流程：
 * 1. onFillRequest：解析用户名/密码字段 → 按绑定应用 / 网站域名匹配条目 → 生成候选 Dataset；
 *    若开启生物识别，Dataset 带认证 PendingIntent（AutofillAuthActivity 验证后回传）。
 * 2. onSaveRequest：把用户刚输入的新账号密码写入本地库（去重）。
 *
 * 需在系统设置中把本应用设为默认自动填充服务后生效。
 */
class NanioAutofillService : AutofillService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var iconCache: android.graphics.Bitmap? = null

    private val TAG = "NanioAutofill"

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val target = AutofillFieldParser.parse(structure)
        Log.d(TAG, "onFillRequest pkg=" + target.packageName + " domain=" + target.webDomain + " user=" + (target.usernameId != null) + " pwd=" + (target.passwordId != null))
        // 本应用自身的输入框（如导出/导入口令）不参与自动填充与保存提示
        if (target.packageName == packageName) return callback.onSuccess(null)
        if (!target.isFillable) return callback.onSuccess(null)

        scope.launch {
            val entries = runCatching { loadMatches(target) }.getOrDefault(emptyList())
            Log.d(TAG, "matches=" + entries.size + " biometric=" + biometricEnabled())
            val response = FillResponse.Builder()
            var datasetCount = 0
            if (!cancellationSignal.isCanceled) {
                entries.take(MAX_DATASETS).forEach { entry ->
                    val dataset = buildDataset(entry, target) ?: return@forEach
                    response.addDataset(dataset)
                    datasetCount++
                }
            }
            // 「使用其他密码…」：目标应用没有绑定条目、或用户想换一个账号时，仍可从全部密码里选择。
            // 通过 FillResponse 级认证：点击后打开选择页，选完由选择页回传 Dataset。
            if (target.passwordId != null) {
                val ids = listOfNotNull(target.usernameId, target.passwordId).toTypedArray()
                // 不要加 NEW_TASK/CLEAR_TASK：认证结果需要通过 Activity 结果回传给系统，
                // 新任务栈会导致结果无法回传（CLEAR_TASK 还会清掉目标 App 的任务栈）。
                val pickerIntent = Intent(this@NanioAutofillService, AutofillPickerActivity::class.java).apply {
                    putExtra(EXTRA_USERNAME_ID, target.usernameId)
                    putExtra(EXTRA_PASSWORD_ID, target.passwordId)
                }
                val pickerPending = PendingIntent.getActivity(
                    this@NanioAutofillService,
                    PICKER_REQUEST_CODE,
                    pickerIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                if (pickerPending != null && ids.isNotEmpty()) {
                    val pickerView = RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
                        setTextViewText(R.id.autofill_item_title, Graph.appContext.getString(R.string.s_use_another_password))
                        setTextViewText(R.id.autofill_item_subtitle, Graph.appContext.getString(R.string.s_choose_a_password_to_fill_from_naniopass))
                        appIconBitmap()?.let { runCatching { setImageViewBitmap(R.id.autofill_item_icon, it) } }
                    }
                    // 用“带认证的 Dataset”实现：点击后打开选择页，选择页通过 EXTRA_AUTHENTICATION_RESULT 回传 Dataset。
                    // （FillResponse 级认证的回传类型在部分 ROM 上不生效，Dataset 级是标准且已验证的路径。）
                    runCatching {
                        val pickerDataset = Dataset.Builder(pickerView).apply {
                            @Suppress("DEPRECATION")
                            target.usernameId?.let { setValue(it, null) }
                            @Suppress("DEPRECATION")
                            target.passwordId?.let { setValue(it, null) }
                            setAuthentication(pickerPending.intentSender)
                        }.build()
                        response.addDataset(pickerDataset)
                        datasetCount++
                    }
                }
            }
            // 关键：即使没有任何候选（例如首次使用、库为空），只要识别到密码字段也要设置 SaveInfo，
            // 这样用户提交登录表单后系统才会回调 onSaveRequest，实现“通过登录流程添加第一条密码”。
            if (target.passwordId != null) {
                val required = listOfNotNull(target.passwordId).toTypedArray()
                val optional = listOfNotNull(target.usernameId).toTypedArray()
                val saveInfo = SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_PASSWORD, required).apply {
                    if (optional.isNotEmpty()) setOptionalIds(optional)
                }
                response.setSaveInfo(saveInfo.build())
            }
            Log.d(TAG, "datasets=" + datasetCount + " saveInfo=" + (target.passwordId != null))
            callback.onSuccess(response.build())
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) return AutofillCompat.success(callback)
        val target = AutofillFieldParser.parse(structure)
        // 忽略本应用自身提交的保存请求（导出/导入口令等）
        if (target.packageName == packageName) return AutofillCompat.success(callback)
        val (username, password) = AutofillFieldParser.readValues(structure)
        Log.d(TAG, "onSaveRequest userPrefix=" + username.take(3) + " pwdLen=" + password.length)
        if (password.isBlank()) return AutofillCompat.success(callback)
        scope.launch {
            runCatching {
                // 同一账号（同一 App/网站）再次保存时更新密码，而不是新增重复条目
                val updated = Graph.repository.upsertByAccount(
                    PasswordEntity(
                        label = labelFor(target),
                        account = username,
                        password = password,
                        packageName = target.packageName,
                        appLabel = target.packageName?.let {
                            runCatching { Graph.appRepository.loadLabel(it) }.getOrNull()
                        },
                        domain = normalizeDomain(target.webDomain),
                        note = Graph.appContext.getString(R.string.s_saved_by_autofill),
                    ),
                )
                Log.d(TAG, "onSaveRequest " + if (updated) "updated account" else "inserted account")
            }
            AutofillCompat.success(callback)
        }
    }

    // ---------------------------------------------------------------- 匹配

    private suspend fun loadMatches(target: AutofillTarget): List<PasswordEntity> {
        val all = Graph.repository.listActive().filter { it.password.isNotEmpty() }
        val domain = normalizeDomain(target.webDomain)
        val pkg = target.packageName
        val matched = all.filter { entry ->
            (pkg != null && entry.packageName == pkg) ||
                (domain != null && domainsMatch(entry.domain, domain))
        }
        // 最近使用过的条目排在前面，便于快速选择
        val byRecent = compareByDescending<PasswordEntity> { it.lastUsedAt }.thenBy { it.label }
        if (matched.isNotEmpty()) return matched.sortedWith(byRecent)
        // 兜底：未绑定应用/网站的密码，允许用户手动选择
        return all.filter { it.packageName.isNullOrEmpty() && it.domain.isNullOrEmpty() }
            .sortedWith(byRecent)
            .take(3)
    }

    private fun labelFor(target: AutofillTarget): String {
        val pkg = target.packageName
        val appLabel = pkg?.let { runCatching { Graph.appRepository.loadLabel(it) }.getOrNull() }
        return appLabel?.takeIf { it.isNotBlank() }
            ?: normalizeDomain(target.webDomain)
            ?: Graph.appContext.getString(R.string.s_new_password_2)
    }

    // ---------------------------------------------------------------- Dataset

    private fun buildDataset(entry: PasswordEntity, target: AutofillTarget): Dataset? {
        val presentation = presentation(entry)
        val builder = Dataset.Builder(presentation)
        val biometric = biometricEnabled()
        if (biometric && target.passwordId != null) {
            // 需要生物识别：由 AutofillAuthActivity 验证后回传 Dataset
            val intent = Intent(appContext(), AutofillAuthActivity::class.java).apply {
                putExtra(EXTRA_USERNAME_ID, target.usernameId)
                putExtra(EXTRA_PASSWORD_ID, target.passwordId)
                putExtra(EXTRA_ACCOUNT, entry.account)
                putExtra(EXTRA_PASSWORD, entry.password)
                putExtra(EXTRA_TITLE, entry.label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            val pending = PendingIntent.getActivity(
                this,
                entry.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            ) ?: return null
            val sender = pending.intentSender ?: return null
            // 带认证的 Dataset 必须先用 setValue(id, null) 声明将要填充的字段，否则 build() 抛异常
            @Suppress("DEPRECATION")
            target.usernameId?.let { builder.setValue(it, null) }
            @Suppress("DEPRECATION")
            target.passwordId?.let { builder.setValue(it, null) }
            builder.setAuthentication(sender)
        } else {
            target.usernameId?.let { builder.setValue(it, AutofillValue.forText(entry.account)) }
            target.passwordId?.let { builder.setValue(it, AutofillValue.forText(entry.password)) }
        }
        return runCatching { builder.build() }.getOrNull()
    }

    fun presentation(entry: PasswordEntity): RemoteViews =
        RemoteViews(packageName, R.layout.autofill_dataset_item).apply {
            setTextViewText(R.id.autofill_item_title, entry.label)
            setTextViewText(
                R.id.autofill_item_subtitle,
                entry.account.ifEmpty { Graph.appContext.getString(R.string.s_tap_to_fill_a_password) },
            )
            appIconBitmap()?.let { runCatching { setImageViewBitmap(R.id.autofill_item_icon, it) } }
        }

    /** 系统建议列表中展示本应用图标，便于识别候选来源。 */
    private fun appIconBitmap(): android.graphics.Bitmap? = iconCache ?: runCatching {
        val size = (36 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val drawable = packageManager.getApplicationIcon(packageName)
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        bitmap
    }.getOrNull().also { iconCache = it }

    private fun biometricEnabled(): Boolean =
        getSharedPreferences("settings", 0).getBoolean("biometric_enabled", false)

    private fun appContext() = applicationContext

    companion object {
        private const val MAX_DATASETS = 5
        private const val PICKER_REQUEST_CODE = 0x5CA5
        const val EXTRA_USERNAME_ID = "autofill_username_id"
        const val EXTRA_PASSWORD_ID = "autofill_password_id"
        const val EXTRA_ACCOUNT = "autofill_account"
        const val EXTRA_PASSWORD = "autofill_password"
        const val EXTRA_TITLE = "autofill_title"

        fun normalizeDomain(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            var d = raw.trim().lowercase()
            d = d.substringAfter("://", d)
            d = d.substringBefore('/')
            d = d.substringBefore('?')
            d = d.removePrefix("www.")
            return d.ifBlank { null }
        }

        fun domainsMatch(a: String?, b: String?): Boolean {
            val x = normalizeDomain(a) ?: return false
            val y = normalizeDomain(b) ?: return false
            return x == y || x.endsWith(".$y") || y.endsWith(".$x")
        }
    }
}
