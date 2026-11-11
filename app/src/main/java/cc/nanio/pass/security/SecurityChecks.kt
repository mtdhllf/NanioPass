package cc.nanio.pass.security


import cc.nanio.pass.data.model.PasswordEntity

/**
 * 纯本地的密码安全性检查（不联网）。
 *
 * 只做简单、克制的提示，不输出冗长的改进建议：
 * 1. 只有一种字符类型（大写/小写/数字/符号）→ 「安全强度低」；
 * 2. 长度小于 8 且字符类型少于 2 种 → 「安全强度低」；
 * 3. 只由 1 种字符组成 → 「安全强度低」；
 * 4. 只使用 2 种以内字符（如 66668888、77777777）→ 「容易被猜到」。
 *
 * 不再提示“同一密码用于多个账号”，只针对密码本身给出简短提示。
 */
object SecurityChecks {

    /** 弱密码原因（UI 负责映射为本地化文案）。 */
    enum class WeakReason { WEAK_PASSWORD, EASILY_GUESSED }

    private val LOWER = Regex("[a-z]")
    private val UPPER = Regex("[A-Z]")
    private val DIGIT = Regex("[0-9]")
    private val SYMBOL = Regex("[^A-Za-z0-9]")

    /** 字符类型数量：大写/小写/数字/符号，最多 4。 */
    fun characterClassCount(password: String): Int {
        if (password.isEmpty()) return 0
        var count = 0
        if (LOWER.containsMatchIn(password)) count++
        if (UPPER.containsMatchIn(password)) count++
        if (DIGIT.containsMatchIn(password)) count++
        if (SYMBOL.containsMatchIn(password)) count++
        return count
    }

    /** 不同字符的个数（区分大小写）。 */
    fun distinctCharacterCount(password: String): Int =
        password.toSet().size

    /**
     * 返回需要提示的文案；不需要提示时返回 null。
     * 「容易被猜到」优先于「安全强度低」（如 66668888 同时满足两条）。
     */
    fun weakReason(password: String): WeakReason? {
        if (password.isEmpty()) return null
        val classes = characterClassCount(password)
        val distinct = distinctCharacterCount(password)
        return when {
            distinct <= 2 -> WeakReason.EASILY_GUESSED
            classes == 1 -> WeakReason.WEAK_PASSWORD
            password.length < 8 && classes < 2 -> WeakReason.WEAK_PASSWORD
            else -> null
        }
    }

    fun isWeak(password: String): Boolean = weakReason(password) != null

    fun weakEntries(entries: List<PasswordEntity>): List<PasswordEntity> =
        entries.filter { it.password.isNotEmpty() && isWeak(it.password) }

    /** 首页「安全性」卡片计数：弱密码条目数。 */
    fun riskCount(entries: List<PasswordEntity>): Int =
        weakEntries(entries).size
}
