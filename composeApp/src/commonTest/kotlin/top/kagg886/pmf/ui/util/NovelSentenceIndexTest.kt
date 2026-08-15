package top.kagg886.pmf.ui.util

import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals

class NovelSentenceIndexTest {
    private val nodes = listOf(
        NovelNodeElement.Title("第一章。"),
        NovelNodeElement.Plain("第一句。第二句！"),
        NovelNodeElement.NewPage(1),
        NovelNodeElement.UploadImage("https://example.com/a.png", Size(100f, 50f)),
        NovelNodeElement.Plain("第三句"),
    )

    @Test
    fun testBuildNovelSentenceIndexNumbersOnlyTextNodes() {
        val index = buildNovelSentenceIndex(nodes)
        assertEquals(
            listOf(
                NovelSentenceSpan(0, 0, 0, "第一章。"),
                NovelSentenceSpan(1, 1, 0, "第一句。"),
                NovelSentenceSpan(2, 1, 1, "第二句！"),
                NovelSentenceSpan(3, 4, 0, "第三句"),
            ),
            index,
        )
    }

    @Test
    fun testPositionNovelSentencesPreservesWhitespaceBetweenSentences() {
        val text = "第一句。\n第二句！ 结尾"
        val index = buildNovelSentenceIndex(listOf(NovelNodeElement.Plain(text)))
        val positions = positionNovelSentences(text, index)
        assertEquals(listOf(0, 5, 10), positions.map { it.start })
        assertEquals(listOf(4, 9, 12), positions.map { it.end })
        assertEquals(listOf("第一句。", "第二句！", "结尾"), positions.map { it.sentence.original })
    }

    @Test
    fun testLightNovelPunctuationIsSplitLocallyAndReattached() {
        val span = NovelSentenceSpan(0, 0, 0, "「こんにちは……⌋」")
        assertEquals("「", span.leadingPunctuation)
        assertEquals("こんにちは", span.translationSource)
        assertEquals("……⌋」", span.trailingPunctuation)
        assertEquals("「Hello……⌋」", reattachNovelSentencePunctuation(span, "Hello。"))

        val comma = NovelSentenceSpan(1, 0, 1, "、こんにちは、")
        assertEquals("、", comma.leadingPunctuation)
        assertEquals("こんにちは", comma.translationSource)
        assertEquals("、", comma.trailingPunctuation)
        assertEquals("、Hello、", reattachNovelSentencePunctuation(comma, "Hello"))
    }

    @Test
    fun testPunctuationOnlySegmentIsNotSentToAi() {
        val index = buildNovelSentenceIndex(
            listOf(
                NovelNodeElement.Plain("第一句。\n……⌋\n第二句！"),
            ),
        )
        assertEquals(listOf("第一句。", "第二句！"), index.map { it.original })
        assertEquals(listOf("第一句", "第二句"), index.map { it.translationSource })
    }

    @Test
    fun testPositionNovelSentencesKeepsDoubleNewlineParagraphBreak() {
        val text = "第一段。\n\n第二段！"
        val index = buildNovelSentenceIndex(listOf(NovelNodeElement.Plain(text)))
        val positions = positionNovelSentences(text, index)
        assertEquals(listOf(0, 6), positions.map { it.start })
        assertEquals(listOf(4, 10), positions.map { it.end })
        assertEquals("\n\n", text.substring(positions[0].end, positions[1].start))
    }

    @Test
    fun testBuildNovelSentenceChunksKeepsReadingOrderAndLimitsSize() {
        val index = buildNovelSentenceIndex(nodes)
        val chunks = buildNovelSentenceChunks(index, setOf(3, 0, 2), maxSentencesPerChunk = 2)
        assertEquals(2, chunks.size)
        assertEquals(listOf(0, 2), chunks[0].sentenceIds)
        assertEquals("第一章\n第二句", chunks[0].sourceText)
        assertEquals(listOf(3), chunks[1].sentenceIds)
        assertEquals("第三句", chunks[1].sourceText)
    }
}
