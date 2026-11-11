package cc.nanio.pass.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/**
 * 从 AssistStructure 中提取的用户名/密码字段。
 * 先按 autofillHints、再按 hint/inputType 判断用户名与密码字段。
 */
data class AutofillTarget(
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    /** 当前页面所属包名（App 内填充时使用） */
    val packageName: String?,
    /** 当前 WebView 页面域名（浏览器内填充时使用） */
    val webDomain: String?,
) {
    val isFillable: Boolean get() = passwordId != null || usernameId != null
}

object AutofillFieldParser {

    fun parse(structure: AssistStructure): AutofillTarget {
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var webDomain: String? = null
        val appPackage = structure.activityComponent?.packageName

        fun walk(node: AssistStructure.ViewNode) {
            if (node.autofillId != null) {
                if (webDomain.isNullOrEmpty() && !node.webDomain.isNullOrEmpty()) {
                    webDomain = node.webDomain
                }
                if (isPasswordField(node) && passwordId == null) {
                    passwordId = node.autofillId
                } else if (isUsernameField(node) && usernameId == null) {
                    usernameId = node.autofillId
                }
            }
            for (i in 0 until node.childCount) {
                walk(node.getChildAt(i))
            }
        }

        for (i in 0 until structure.windowNodeCount) {
            structure.getWindowNodeAt(i).rootViewNode?.let { walk(it) }
        }
        return AutofillTarget(
            usernameId = usernameId,
            passwordId = passwordId,
            packageName = appPackage,
            webDomain = webDomain,
        )
    }

    /** 从 fillContext 结构里读取当前已输入的用户名/密码值（保存新凭据用） */
    fun readValues(structure: AssistStructure): Pair<String, String> {
        var username = ""
        var password = ""
        fun walk(node: AssistStructure.ViewNode) {
            if (node.autofillId != null) {
                val text = node.text?.toString().orEmpty()
                if (text.isNotEmpty()) {
                    if (isPasswordField(node)) password = text
                    else if (isUsernameField(node)) username = text
                }
            }
            for (i in 0 until node.childCount) walk(node.getChildAt(i))
        }
        for (i in 0 until structure.windowNodeCount) {
            structure.getWindowNodeAt(i).rootViewNode?.let { walk(it) }
        }
        return username to password
    }

    private fun hintsOf(node: AssistStructure.ViewNode): List<String> {
        val hints = node.autofillHints ?: return emptyList()
        return hints.filterNotNull().map { it.lowercase() }
    }

    private fun isPasswordField(node: AssistStructure.ViewNode): Boolean {
        val hints = hintsOf(node)
        if (hints.any { it.contains("password") || it == "newPassword" }) return true
        val hint = node.hint?.lowercase().orEmpty()
        if (hint.contains("密码") || hint.contains("password") || hint.contains("passcode") || hint.contains("pin")) {
            return true
        }
        val inputType = node.inputType
        if (inputType != 0) {
            val cls = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            if (cls == InputType.TYPE_CLASS_TEXT && (
                    variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                    )
            ) return true
            if (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return true
        }
        return false
    }

    private fun isUsernameField(node: AssistStructure.ViewNode): Boolean {
        if (node.autofillType != View.AUTOFILL_TYPE_TEXT) return false
        val hints = hintsOf(node)
        if (hints.any {
                it.contains("username") || it.contains("email") || it == "login" ||
                    it.contains("account") || it == "tel" || it.contains("phone")
            }
        ) return true
        val hint = node.hint?.lowercase().orEmpty()
        if (hint.contains("账号") || hint.contains("帐号") || hint.contains("用户名") ||
            hint.contains("邮箱") || hint.contains("手机") || hint.contains("手机号") ||
            hint.contains("account") || hint.contains("username") || hint.contains("email") ||
            hint.contains("phone") || hint.contains("手机号码")
        ) return true
        val inputType = node.inputType
        if (inputType != 0) {
            val cls = inputType and InputType.TYPE_MASK_CLASS
            val variation = inputType and InputType.TYPE_MASK_VARIATION
            if (cls == InputType.TYPE_CLASS_TEXT && (
                    variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    )
            ) return true
            if (cls == InputType.TYPE_CLASS_PHONE) return true
        }
        return false
    }
}
