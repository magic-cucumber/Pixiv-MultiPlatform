package top.kagg886.pmf.translate

import kotlinx.coroutines.flow.Flow

/**
 * 文本翻译引擎抽象。
 *
 * [targetLang] 为翻译目标语言的展示名（如 中文 / English），
 * 由 [LanguageDetector.targetLanguageName] 提供。
 */
interface Translator {
    /** 一次性翻译，返回完整译文；异常由调用方处理。 */
    suspend fun translate(text: String, targetLang: String): String

    /** 流式翻译，逐次 emit 本次新增译文片段（由 [TranslateScheduler] 累积）。 */
    fun translateStream(text: String, targetLang: String): Flow<String>
}
