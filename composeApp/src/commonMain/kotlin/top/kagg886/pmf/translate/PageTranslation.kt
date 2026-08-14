package top.kagg886.pmf.translate

/**
 * 小说正文某一页的翻译状态。
 *
 * [Pending] 原文未翻译；[Translating] 流式译文中（灰色显示）；
 * [Complete] 句对句对齐完成（正常色，可点击句子切换原文）；[Failed] 回退原文并以淡红显示。
 */
sealed interface PageTranslationState {
    data object Pending : PageTranslationState

    data class Translating(val streamedText: String) : PageTranslationState

    data class Complete(val pairs: List<SentencePair>) : PageTranslationState

    data object Failed : PageTranslationState
}
