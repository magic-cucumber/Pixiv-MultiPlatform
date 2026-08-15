package top.kagg886.pmf.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import top.kagg886.pmf.translate.SentenceTranslationState

/** [mergeSentenceStates] 的用例：流式中间态/最终态合并、缺句失败、回显过滤、防降级。 */
class MergeSentenceStatesTest {
    private val chunk =
        NovelSentenceChunk(
            sentenceIds = listOf(0, 1),
            sourceText = "こんにちは。\n元気ですか。",
            sentences = listOf(
                NovelSentenceSpan(0, 0, 0, "こんにちは。"),
                NovelSentenceSpan(1, 0, 1, "元気ですか。"),
            ),
        )

    @Test
    fun testFinalMarksCompleteWithPunctuationReattached() {
        val merged = mergeSentenceStates(
            emptyMap(),
            chunk,
            listOf("Hello", "How are you"),
            final = true,
        )
        assertEquals(
            mapOf(
                0 to SentenceTranslationState.Complete("Hello。"),
                1 to SentenceTranslationState.Complete("How are you。"),
            ),
            merged,
        )
    }

    @Test
    fun testFinalMissingOrBlankLineMarksFailed() {
        val missing = mergeSentenceStates(emptyMap(), chunk, listOf("Hello"), final = true)
        assertEquals(
            mapOf(
                0 to SentenceTranslationState.Complete("Hello。"),
                1 to SentenceTranslationState.Failed,
            ),
            missing,
        )

        val blank = mergeSentenceStates(emptyMap(), chunk, listOf("Hello", "  "), final = true)
        assertEquals(SentenceTranslationState.Failed, blank[1])
    }

    @Test
    fun testFinalIdentityLineMarksFailed() {
        // 模型收到的源文本是剥离标点的 translationSource（"こんにちは"），回显也按此形式判等
        val merged = mergeSentenceStates(
            emptyMap(),
            chunk,
            listOf("こんにちは", "元気ですか"),
            final = true,
        )
        assertEquals(
            mapOf(
                0 to SentenceTranslationState.Failed,
                1 to SentenceTranslationState.Failed,
            ),
            merged,
        )
    }

    @Test
    fun testFinalNullLinesMarksAllFailed() {
        val merged = mergeSentenceStates(emptyMap(), chunk, null, final = true)
        assertEquals(
            mapOf(
                0 to SentenceTranslationState.Failed,
                1 to SentenceTranslationState.Failed,
            ),
            merged,
        )
    }

    @Test
    fun testIntermediateMapsLinesToTranslating() {
        val merged = mergeSentenceStates(
            emptyMap(),
            chunk,
            listOf("Hello"),
            final = false,
        )
        assertEquals(
            mapOf(
                0 to SentenceTranslationState.Translating("Hello。"),
                // 尚无完整行的句子转为空占位
                1 to SentenceTranslationState.Translating(""),
            ),
            merged,
        )
    }

    @Test
    fun testIntermediateKeepsExistingTranslatingForMissingLine() {
        val old =
            mapOf(
                0 to SentenceTranslationState.Translating("partial"),
                1 to SentenceTranslationState.Translating(""),
            )
        val merged = mergeSentenceStates(old, chunk, listOf("Hello"), final = false)
        assertEquals(SentenceTranslationState.Translating("Hello。"), merged[0])
        assertEquals(SentenceTranslationState.Translating(""), merged[1])
    }

    @Test
    fun testIntermediateIdentityLineNotShown() {
        val old = mapOf(0 to SentenceTranslationState.Pending)
        val merged = mergeSentenceStates(old, chunk, listOf("こんにちは"), final = false)
        // 回显原文不展示，Pending 转为空占位
        assertEquals(SentenceTranslationState.Translating(""), merged[0])
    }

    @Test
    fun testIntermediateDoesNotDemoteComplete() {
        val old = mapOf(0 to SentenceTranslationState.Complete("完成"))
        val merged = mergeSentenceStates(old, chunk, listOf("Hello"), final = false)
        assertEquals(SentenceTranslationState.Complete("完成"), merged[0])
    }

    @Test
    fun testEmptyChunkReturnsOldMap() {
        val old = mapOf(5 to SentenceTranslationState.Complete("x"))
        val emptyChunk = NovelSentenceChunk(emptyList(), "", emptyList())
        assertEquals(old, mergeSentenceStates(old, emptyChunk, listOf("a"), final = true))
        assertEquals(old, mergeSentenceStates(old, emptyChunk, null, final = true))
    }

    @Test
    fun testMergePreservesUnrelatedEntries() {
        val old = mapOf(99 to SentenceTranslationState.Complete("其它"))
        val merged = mergeSentenceStates(old, chunk, listOf("Hello", "How"), final = true)
        assertEquals(SentenceTranslationState.Complete("其它"), merged[99])
        assertEquals(SentenceTranslationState.Complete("Hello。"), merged[0])
    }
}
