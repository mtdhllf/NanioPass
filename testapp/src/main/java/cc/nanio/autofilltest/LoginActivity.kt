package cc.nanio.autofilltest

import android.app.Activity
import android.content.ComponentName
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.autofill.AutofillManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPasswordOption
import androidx.credentials.PasswordCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 仅用于联调验证的最小登录页（原生 EditText，非 Compose）。
 *
 * 客户端行为完全依赖**系统当前实际配置的服务**：
 * - 自动填充：系统 `autofill_service` 指向谁，输入框建议就由谁提供；
 * - 保存密码：提交表单时调用 `AutofillManager.commit()`，由当前自动填充服务决定是否弹保存界面
 *   （取决于当前自动填充服务是否提供保存界面）；
 * - 凭据获取：CredentialManager 走系统当前的首选凭据服务（`credential_service_primary`）。
 *
 * 页面顶部会显示当前系统实际使用的两个服务，便于确认“测的是谁”。
 */
class LoginActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var credentialBusy = false

    private lateinit var autofillStatus: TextView
    private lateinit var credentialStatus: TextView
    private lateinit var lastResult: TextView
    private lateinit var username: EditText
    private lateinit var password: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        fun label(text: String) = TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(0, pad / 4, 0, 0)
        }

        root.addView(TextView(this).apply {
            text = "模拟登录（测试保存密码 / 自动填充 / 凭据服务）"
            textSize = 18f
            setPadding(0, 0, 0, pad / 2)
        })

        autofillStatus = TextView(this).apply {
            textSize = 12f
            setPadding(0, pad / 4, 0, 0)
        }
        credentialStatus = TextView(this).apply {
            textSize = 12f
            setPadding(0, 0, 0, pad / 2)
        }
        root.addView(autofillStatus)
        root.addView(credentialStatus)

        username = EditText(this).apply {
            hint = "账号"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setAutofillHints(View.AUTOFILL_HINT_USERNAME)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        password = EditText(this).apply {
            hint = "密码"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        root.addView(label("用户名 / 邮箱（点击输入框可测试自动填充）"))
        root.addView(username)
        root.addView(label("密码"))
        root.addView(password)

        // ① 新用户登录：填写一组新账号并提交，触发系统的“保存密码”请求
        root.addView(Button(this).apply {
            text = "① 模拟新用户登录（触发保存密码提示）"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                username.setText("newuser@nanio.dev")
                password.setText("NewPass123456")
                // 必须先让输入框获得焦点，AutofillManager 才会建立本次会话并收到 SaveInfo，
                // 否则 commit() 返回 false、系统不会回调 onSaveRequest（首次无候选时尤其明显）。
                password.requestFocus()
                password.postDelayed({
                    commitForSave("新用户登录已提交")
                }, 500)
            }
        })

        // ② 从系统凭据管理器获取密码
        root.addView(Button(this).apply {
            text = "② 从凭据管理器取密码（当前首选凭据服务）"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                if (credentialBusy) return@setOnClickListener
                credentialBusy = true
                lastResult.text = "正在请求系统凭据…"
                scope.launch {
                    try {
                        val manager = CredentialManager.create(this@LoginActivity)
                        val request = GetCredentialRequest(listOf(GetPasswordOption()))
                        val response = manager.getCredential(this@LoginActivity, request)
                        val cred = response.credential as? PasswordCredential
                        if (cred != null) {
                            username.setText(cred.id)
                            password.setText(cred.password)
                            lastResult.text = "凭据获取成功：" + cred.id
                            Toast.makeText(this@LoginActivity, "已通过凭据服务获取：" + cred.id, Toast.LENGTH_SHORT).show()
                        } else {
                            lastResult.text = "凭据类型不匹配（未返回密码凭据）"
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AutofillTest", "credential failed", e)
                        lastResult.text = "凭据获取失败：" + (e.message ?: e.javaClass.simpleName) +
                            "\n（当前系统首选凭据服务未返回凭据；可在系统设置里把 NanioPassword 设为首选服务）"
                        Toast.makeText(this@LoginActivity, "凭据获取失败：" + (e.message ?: ""), Toast.LENGTH_LONG).show()
                    } finally {
                        credentialBusy = false
                    }
                }
            }
        })

        // ③ 普通登录（已填写内容时提交，也会触发保存）
        root.addView(Button(this).apply {
            text = "③ 登录（提交表单）"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                if (username.text.isNullOrBlank() || password.text.isNullOrBlank()) {
                    lastResult.text = "请先填写账号和密码，或点按钮 ①"
                } else {
                    commitForSave("登录已提交")
                }
            }
        })

        // ④ 凭据创建：模拟注册场景，让系统 CredentialManager 把新密码保存到密码管理器
        root.addView(Button(this).apply {
            text = "④ 注册并保存密码（凭据创建）"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                lastResult.text = "正在请求凭据创建…"
                scope.launch {
                    try {
                        val manager = CredentialManager.create(this@LoginActivity)
                        val response = manager.createCredential(
                            this@LoginActivity,
                            CreatePasswordRequest("newuser@nanio.dev", "NewPass123456"),
                        )
                        lastResult.text = "凭据创建完成：" + response.javaClass.simpleName
                        Toast.makeText(this@LoginActivity, "凭据创建流程完成", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        android.util.Log.e("AutofillTest", "createCredential failed", e)
                        lastResult.text = "凭据创建失败：" + (e.message ?: e.javaClass.simpleName)
                        Toast.makeText(this@LoginActivity, "凭据创建失败：" + (e.message ?: ""), Toast.LENGTH_LONG).show()
                    }
                }
            }
        })

        lastResult = TextView(this).apply {
            text = "请选择上方按钮进行测试；顶部显示当前系统实际使用的服务。"
            textSize = 12f
            setPadding(0, pad / 2, 0, 0)
        }
        root.addView(lastResult)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshServiceStatus()
    }

    /** 提交表单，让当前自动填充服务决定是否弹出“保存密码”。 */
    private fun commitForSave(prefix: String) {
        val manager = getSystemService(AutofillManager::class.java)
        val ok = runCatching { manager?.commit() }.getOrDefault(false)
        lastResult.text = prefix + "；commit=" + ok +
            "（是否弹保存提示取决于当前自动填充服务是否支持保存界面）"
    }

    private fun refreshServiceStatus() {
        val autofill = getSystemService(AutofillManager::class.java)?.autofillServiceComponentName
        autofillStatus.text = "自动填充服务：" + (autofill?.flattenToShortString() ?: "未设置")
        // 部分系统（S+）把 credential_service* 标记为不可读，读取失败时优雅降级
        fun readSecure(key: String): String? =
            runCatching { Settings.Secure.getString(contentResolver, key) }.getOrNull()
        val primary = readSecure("credential_service_primary")
        val fallback = readSecure("credential_service")
        val cred = (primary ?: fallback)?.let {
            runCatching { ComponentName.unflattenFromString(it)?.flattenToShortString() }.getOrNull() ?: it
        }
        credentialStatus.text = "凭据服务：" + (cred ?: "系统中未公开/未设置（以系统凭据选择器为准）")
    }
}
