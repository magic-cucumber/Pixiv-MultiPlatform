package top.kagg886.pmf.translate

import androidx.compose.ui.text.intl.Locale
import top.kagg886.pmf.backend.AppConfig

/**
 * 基于应用 `language` 设置的文本语种检测。
 *
 * 中文 UI 下会把假名、韩文、拉丁、西里尔、阿拉伯等非汉字文字视为外语；
 * 同时维护日文专用符号/和制汉字表，避免纯汉字日文（或仅夹杂日文符号的文本）被误判为中文。
 */
object LanguageDetector {
    private fun currentLocale(): Locale = AppConfig.locale.locale

    /** 文本是否属于非当前 UI 语言的"外语"内容。 */
    fun isForeign(text: String, locale: Locale = currentLocale()): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        // 纯标点 / 纯数字等无字母内容不参与判断
        if (trimmed.none { it.isLetter() }) return false

        return if (locale.language.equals("zh", ignoreCase = true)) {
            trimmed.any { char ->
                (char.isLetter() && (!isHan(char) || isJapaneseKokuji(char))) ||
                    isJapaneseSpecificNonLetter(char)
            }
        } else {
            trimmed.any(::isCjk)
        }
    }

    /** 当前语言设置对应的目标语言展示名。 */
    fun targetLanguageName(locale: Locale = currentLocale()): String = when {
        locale.language.equals("zh", ignoreCase = true) &&
            locale.region.equals("TW", ignoreCase = true) -> "繁體中文"

        locale.language.equals("zh", ignoreCase = true) -> "中文"

        else -> "English"
    }

    private fun isKana(c: Char): Boolean {
        val code = c.code
        return code in 0x3040..0x30FF ||
            code in 0x31F0..0x31FF ||
            code in 0xFF66..0xFF9F
    }

    private fun isHan(c: Char): Boolean {
        val code = c.code
        return code in 0x3400..0x4DBF ||
            code in 0x4E00..0x9FFF ||
            code in 0x20000..0x2A6DF ||
            code in 0x2A700..0x2B73F ||
            code in 0x2B740..0x2B81F ||
            code in 0x2B820..0x2CEAF ||
            code in 0x2CEB0..0x2EBEF ||
            code in 0x30000..0x3134F ||
            code in 0xF900..0xFAFF ||
            code in 0x2F800..0x2FA1F
    }

    private fun isHangul(c: Char): Boolean = c.code in 0xAC00..0xD7AF

    private fun isCjk(c: Char): Boolean {
        val code = c.code
        return isHan(c) || isKana(c) || isHangul(c) ||
            code in 0x3000..0x303F || code in 0xFF00..0xFFEF
    }

    /** 日文专用非汉字符号：出现这些符号时，中文 UI 下不应把整段当成纯中文。 */
    private fun isJapaneseSpecificNonLetter(c: Char): Boolean = c in JAPANESE_SYMBOLS

    private fun isJapaneseKokuji(c: Char): Boolean = c in JAPANESE_KOKUJI

    private val JAPANESE_SYMBOLS =
        setOf(
            '々', '〆', '〒', '〠', 'ヶ', 'ヵ', 'ゕ', 'ゖ',
            'ゝ', 'ゞ', 'ヽ', 'ヾ', '・', 'ｰ',
        )

    /** 常见和制汉字/日文专用汉字表，用于中文 UI 下的日文识别。 */
    private val JAPANESE_KOKUJI =
        setOf(
            '畑', '働', '峠', '辻', '込', '匁', '榊', '凧', '凩',
            '柾', '枡', '枠', '栃', '畠', '雫', '塰', '鮨', '鯱',
            '鱚', '麿', '鴫', '鰯', '鱈', '鶇', '鵺', '鳰', '鯰',
            '鱒', '鰺', '鰍', '鮗', '鯒', '鯑', '鯣', '鯔', '鰕',
            '鰆', '鰤', '鰰', '鮃', '鮱', '鯲', '杣', '杤', '圷',
            '垰', '椛', '橅', '粂', '裃', '袰', '襷', '躾', '軅',
        )
}
