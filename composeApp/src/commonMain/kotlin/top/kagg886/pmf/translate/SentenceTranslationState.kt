package top.kagg886.pmf.translate

/**
 * 小说正文单个句子的翻译状态。
 *
 * 句子由 [SentenceSegmenter] 从 Plain/Title 节点切分，并在 [RichText] 中逐句渲染；
 * 点击句子可在译文与原文之间切换。
 */
sealed interface SentenceTranslationState {
    data object Pending : SentenceTranslationState

    /** 流式翻译中，[translatedText] 为当前已闭合行（可能为空，此时显示原文占位）。 */
    data class Translating(val translatedText: String) : SentenceTranslationState

    data class Complete(val translatedText: String) : SentenceTranslationState

    data object Failed : SentenceTranslationState
}
