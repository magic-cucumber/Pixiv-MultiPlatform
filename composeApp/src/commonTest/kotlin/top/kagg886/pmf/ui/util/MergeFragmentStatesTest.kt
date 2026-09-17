package top.kagg886.pmf.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import top.kagg886.pmf.translate.SentenceTranslationState

/** [mergeFragmentStates] 的用例：按行号对齐、回显视为成功、行数不匹配全失败、流式中间态、防降级。 */
class MergeFragmentStatesTest {
    private val chunk =
        NovelSentenceChunk(
            fragmentIds = listOf(0, 1),
            sourceText = "marker",
            fragments =
            listOf(
                NovelFragmentSpan(0, 0, 0, "しろは", "「", ""),
                NovelFragmentSpan(1, 0, 0, "お早う～", "", "」"),
            ),
        )

    @Test
    fun testFinalMarksCompleteWithPunctuationReattached() {
        val merged =
            mergeFragmentStates(
                emptyMap(),
                chunk,
                listOf("白叶", "早上好"),
                final = true,
            )
        assertEquals(
            mapOf(
                0 to SentenceTranslationState.Complete("白叶"),
                1 to SentenceTranslationState.Complete("早上好～"),
            ),
            merged,
        )
    }

    @Test
    fun testFinalEchoShowsOriginalWithoutRed() {
        // 专名回显：译文与片段核心相同 → 显示原文片段，不标红
        val merged =
            mergeFragmentStates(
                emptyMap(),
                chunk,
                listOf("しろは", "早上好"),
                final = true,
            )
        assertEquals(SentenceTranslationState.Complete("しろは"), merged[0])
        assertEquals(SentenceTranslationState.Complete("早上好～"), merged[1])
    }

    @Test
    fun testFinalLineCountMismatchMarksWholeChunkFailed() {
        // 模型合并/漏行：非空行数与片段数不一致 → 整 chunk 失败，绝不按索引硬配对
        val missing = mergeFragmentStates(emptyMap(), chunk, listOf("白叶"), final = true)
        assertEquals(
            mapOf(
                0 to SentenceTranslationState.Failed,
                1 to SentenceTranslationState.Failed,
            ),
            missing,
        )
        val extra = mergeFragmentStates(emptyMap(), chunk, listOf("白叶", "早上好", "多余"), final = true)
        assertEquals(
            mapOf(
                0 to SentenceTranslationState.Failed,
                1 to SentenceTranslationState.Failed,
            ),
            extra,
        )
    }

    @Test
    fun testFinalPreserveKeepsPreviouslyCompleteOnFailure() {
        // 整句重译失败时，preserve 内的已 Complete 片段不被连坐标红
        val old =
            mapOf(
                0 to SentenceTranslationState.Complete("已有译文"),
                1 to SentenceTranslationState.Failed,
            )
        val merged =
            mergeFragmentStates(
                old,
                chunk,
                listOf("只有一行"), // 行数不匹配 → 整 chunk 失败
                final = true,
                preserve = setOf(0),
            )
        assertEquals(SentenceTranslationState.Complete("已有译文"), merged[0], "preserve 内已成功片段应保留")
        assertEquals(SentenceTranslationState.Failed, merged[1])
    }

    @Test
    fun testFinalBlankLineIsForgivenWhenCountMatchesAfterFiltering() {
        // 模型插入装饰性空行：过滤后行数一致，仍按位置对齐
        val merged =
            mergeFragmentStates(
                emptyMap(),
                chunk,
                listOf("白叶", "", "早上好"),
                final = true,
            )
        assertEquals(
            mapOf(
                0 to SentenceTranslationState.Complete("白叶"),
                1 to SentenceTranslationState.Complete("早上好～"),
            ),
            merged,
        )
    }

    @Test
    fun testFinalNullLinesMarksAllFailed() {
        val merged = mergeFragmentStates(emptyMap(), chunk, null, final = true)
        assertEquals(
            mapOf(
                0 to SentenceTranslationState.Failed,
                1 to SentenceTranslationState.Failed,
            ),
            merged,
        )
    }

    @Test
    fun testIntermediateMapsClosedLinesToTranslating() {
        val merged =
            mergeFragmentStates(
                emptyMap(),
                chunk,
                listOf("白叶"),
                final = false,
            )
        assertEquals(
            mapOf(
                0 to SentenceTranslationState.Translating("白叶"),
                // 尚无闭合行的片段转为空占位
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
        val merged = mergeFragmentStates(old, chunk, listOf("白叶"), final = false)
        assertEquals(SentenceTranslationState.Translating("白叶"), merged[0])
        assertEquals(SentenceTranslationState.Translating(""), merged[1])
    }

    @Test
    fun testIntermediateEchoNotShownAsTranslating() {
        val old = mapOf(0 to SentenceTranslationState.Pending)
        val merged = mergeFragmentStates(old, chunk, listOf("しろは"), final = false)
        // 回显不展示，Pending 转为空占位
        assertEquals(SentenceTranslationState.Translating(""), merged[0])
    }

    @Test
    fun testIntermediateDoesNotDemoteComplete() {
        val old = mapOf(0 to SentenceTranslationState.Complete("完成"))
        val merged = mergeFragmentStates(old, chunk, listOf("白叶"), final = false)
        assertEquals(SentenceTranslationState.Complete("完成"), merged[0])
    }

    @Test
    fun testEmptyChunkReturnsOldMap() {
        val old = mapOf(5 to SentenceTranslationState.Complete("x"))
        val emptyChunk = NovelSentenceChunk(emptyList(), "")
        assertEquals(old, mergeFragmentStates(old, emptyChunk, listOf("a"), final = true))
        assertEquals(old, mergeFragmentStates(old, emptyChunk, null, final = true))
    }

    @Test
    fun testMergePreservesUnrelatedEntries() {
        val old = mapOf(99 to SentenceTranslationState.Complete("其它"))
        val merged = mergeFragmentStates(old, chunk, listOf("白叶", "早上好"), final = true)
        assertEquals(SentenceTranslationState.Complete("其它"), merged[99])
        assertEquals(SentenceTranslationState.Complete("白叶"), merged[0])
    }
}
