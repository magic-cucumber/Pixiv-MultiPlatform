package top.kagg886.pmf.translate

import kotlin.test.Test
import kotlin.test.assertEquals

/** 分句器对日本轻小说排版的适配用例。 */
class SentenceSegmenterTest {

    @Test
    fun testCommaDoesNotSplitSentence() {
        // 、是语块分隔符而非句末：整句保留内部逗号，避免碎片化翻译
        assertEquals(
            listOf("こんにちは、元気ですか。"),
            SentenceSegmenter.split("こんにちは、元気ですか。"),
        )
        assertEquals(
            listOf("あのね、これ、それ。", "次は？"),
            SentenceSegmenter.split("あのね、これ、それ。次は？"),
        )
    }

    @Test
    fun testSemicolonDoesNotSplitSentence() {
        assertEquals(
            listOf("一つ目；二つ目。"),
            SentenceSegmenter.split("一つ目；二つ目。"),
        )
    }

    @Test
    fun testDialogueAttributionStaysTogether() {
        // 对话 + 陈述在同一句内，避免 "と彼は言った。" 被单独翻译
        assertEquals(
            listOf("「こんにちは」と彼は言った。"),
            SentenceSegmenter.split("「こんにちは」と彼は言った。"),
        )
    }

    @Test
    fun testMultipleSentencesInsideDialogue() {
        // 括号内的句末标点不切句：整段对话作为一句翻译，避免对话被拆碎
        assertEquals(
            listOf("「行け！止まるな！」"),
            SentenceSegmenter.split("「行け！止まるな！」"),
        )
        assertEquals(
            listOf("「こんにちは。」と彼は言った。"),
            SentenceSegmenter.split("「こんにちは。」と彼は言った。"),
        )
    }

    @Test
    fun testEllipsisInsideBracketDoesNotSplit() {
        // ⌈……⌋ 的省略号在括号内，不产生句边界
        assertEquals(
            listOf("彼女は⌈……⌋と呟いた。"),
            SentenceSegmenter.split("彼女は⌈……⌋と呟いた。"),
        )
    }

    @Test
    fun testEllipsisAndFloorBracketsMergeIntoSentence() {
        assertEquals(
            listOf("⌈こんにちは……⌋"),
            SentenceSegmenter.split("⌈こんにちは……⌋"),
        )
        assertEquals(
            listOf("はい……", "いいえ。"),
            SentenceSegmenter.split("はい……\nいいえ。"),
        )
    }

    @Test
    fun testNewlineIsHardBoundary() {
        assertEquals(
            listOf("第一句。", "第二句！"),
            SentenceSegmenter.split("第一句。\n第二句！"),
        )
    }

    @Test
    fun testEnglishPeriodOnlyBoundaryAfterWhitespace() {
        assertEquals(1, SentenceSegmenter.split("3.14 is a number").size)
        assertEquals(
            listOf("Hello world.", "How are you?"),
            SentenceSegmenter.split("Hello world. How are you?"),
        )
    }
}
