package top.kagg886.pmf.translate

import androidx.compose.ui.text.intl.Locale
import top.kagg886.pmf.util.ComposeI18N

/**
 * 基于当前 UI 语言的简单文本语种检测。
 *
 * 规则：
 * - 中文 UI：出现任何非汉字表意文字的字母（假名、韩文、拉丁、西里尔、阿拉伯等）即视为外语；
 * - 其他 UI（默认英文规则）：出现 CJK 字符即视为外语。
 */
object LanguageDetector {
    private fun currentLocale(): Locale = ComposeI18N.locale.value ?: Locale.current

    /** 文本是否属于非当前 UI 语言的"外语"内容。 */
    fun isForeign(text: String, locale: Locale = currentLocale()): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        // 纯标点 / 纯数字等无字母内容不参与判断
        if (trimmed.none { it.isLetter() }) return false

        return if (locale.language.equals("zh", ignoreCase = true)) {
            trimmed.any { it.isLetter() && !isHan(it) }
        } else {
            trimmed.any(::isCjk)
        }
    }

    /** 当前 UI 语言对应的目标语言展示名。 */
    fun targetLanguageName(locale: Locale = currentLocale()): String = when {
        locale.language.equals("zh", ignoreCase = true) &&
            locale.region.equals("TW", ignoreCase = true) -> "繁體中文"

        locale.language.equals("zh", ignoreCase = true) -> "中文"

        else -> "English"
    }

    private fun isKana(c: Char): Boolean {
        val code = c.code
        return code in 0x3040..0x30FF || code in 0x31F0..0x31FF || code in 0xFF66..0xFF9F
    }

    private fun isHan(c: Char): Boolean {
        val code = c.code
        return code in 0x3400..0x4DBF || code in 0x4E00..0x9FFF || code in 0xF900..0xFAFF
    }

    private fun isHangul(c: Char): Boolean = c.code in 0xAC00..0xD7AF

    private fun isCjk(c: Char): Boolean {
        val code = c.code
        return isHan(c) || isKana(c) || isHangul(c) ||
            code in 0x3000..0x303F || code in 0xFF00..0xFFEF
    }
}
