package cc.nanio.pass.ui.util

import java.text.Normalizer
import java.util.Locale

/**
 * 列表拼音分组工具。
 * 使用系统 ICU 的 Han-Latin 转写生成拼音排序键与 A–Z 分组，无需打包字典。
 */
object PinyinUtil {

    private const val OTHER = "#"

    private val transliterator: android.icu.text.Transliterator? by lazy {
        runCatching {
            android.icu.text.Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")
        }.getOrNull()
    }

    /** 拼音首字母（A–Z），非字母返回 # */
    fun initialOf(text: String): String {
        val first = text.trim().firstOrNull() ?: return OTHER
        if (first.isLetter() && first.code < 128) {
            return first.uppercaseChar().toString()
        }
        val pinyin = toPinyin(text.trim())
        val c = pinyin.firstOrNull()?.uppercaseChar()
        return if (c != null && c in 'A'..'Z') c.toString() else OTHER
    }

    /** 转成去声调的拼音（用于排序） */
    fun toPinyin(text: String): String {
        val t = transliterator ?: return text
        val result = runCatching { t.transliterate(text) }.getOrNull() ?: return text
        // 去掉残留声调符号
        val normalized = Normalizer.normalize(result, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return normalized.lowercase(Locale.ROOT)
    }

    /** 分组键排序：A-Z 在前，# 最后 */
    fun sortKey(group: String): Int = if (group == OTHER) 26 else (group.firstOrNull()?.code ?: 26) - 'A'.code
}
