package top.kagg886.pmf.translate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 流式"每行一句"协议的增量提取与完整解析用例。
 *
 * 模型逐 token 输出纯文本时，只有已经出现换行的行才会被提取；
 * 未闭合的最后一行保留在缓冲区，不提前上屏。
 */
class IncrementalSentenceParserTest {

    // ---- IncrementalSentenceParser.extractSentences ----

    @Test
    fun testCompleteLines() {
        assertEquals(
            listOf("第一句", "第二句"),
            IncrementalSentenceParser.extractSentences("第一句\n第二句\n"),
        )
    }

    @Test
    fun testUnclosedLastLineIsKeptInBuffer() {
        assertEquals(
            listOf("第一句"),
            IncrementalSentenceParser.extractSentences("第一句\n第二句还没"),
        )
    }

    @Test
    fun testFirstLineIsNotEmittedBeforeNewline() {
        assertEquals(
            emptyList(),
            IncrementalSentenceParser.extractSentences("第一句还没"),
        )
    }

    @Test
    fun testTrailingNewlineClosesCurrentLine() {
        assertEquals(
            listOf("第一句"),
            IncrementalSentenceParser.extractSentences("第一句\n"),
        )
    }

    @Test
    fun testBlankLinesArePreservedForSentenceAlignment() {
        assertEquals(
            listOf("第一句", "", "第二句"),
            IncrementalSentenceParser.extractSentences("第一句\n\n第二句\n"),
        )
    }

    @Test
    fun testCodeFenceStartIsNotShown() {
        assertEquals(
            listOf("第一句"),
            IncrementalSentenceParser.extractSentences("```text\n第一句\n第二句还没"),
        )
    }

    @Test
    fun testCompleteCodeFenceWrapper() {
        assertEquals(
            listOf("第一句", "第二句"),
            IncrementalSentenceParser.extractSentences("```text\n第一句\n第二句\n```\n"),
        )
    }

    @Test
    fun testBlankInputIsEmpty() {
        assertEquals(emptyList(), IncrementalSentenceParser.extractSentences("  "))
    }

    @Test
    fun testExtractMatchesParseOnCompleteLines() {
        val full = "第一句\n第二句\n"
        assertEquals(
            SentenceTranslationParser.parse(full),
            IncrementalSentenceParser.extractSentences(full),
        )
    }

    // ---- SentenceTranslationParser.parseStrict ----

    @Test
    fun testParseStrictAcceptsPlainLines() {
        assertEquals(
            listOf("第一句", "第二句"),
            SentenceTranslationParser.parseStrict("第一句\n第二句"),
        )
    }

    @Test
    fun testParseStrictIgnoresBlankLines() {
        assertEquals(
            listOf("第一句"),
            SentenceTranslationParser.parseStrict("\n第一句\n\n"),
        )
    }

    @Test
    fun testParseStrictRejectsBlankText() {
        assertNull(SentenceTranslationParser.parseStrict(""))
        assertNull(SentenceTranslationParser.parseStrict(" \n\t"))
    }

    @Test
    fun testParseForAlignmentPreservesMissingSentenceLine() {
        assertEquals(
            listOf("第一句", "", "第三句"),
            SentenceTranslationParser.parseForAlignment("第一句\n\n第三句"),
        )
        assertNull(SentenceTranslationParser.parseForAlignment("\n\n"))
        assertNull(SentenceTranslationParser.parseForAlignment("""{"sentences":["你好"]}"""))
    }

    @Test
    fun testLegacyJsonPayloadIsRejectedInsteadOfBeingShown() {
        assertNull(SentenceTranslationParser.parseStrict("""{"sentences":["你好","世界"]}"""))
        assertNull(SentenceTranslationParser.parseStrict("""["你好","世界"]"""))
        assertEquals(emptyList(), IncrementalSentenceParser.extractSentences("""{"sentences":["你好""""))
    }

    @Test
    fun testParseRemovesCodeFence() {
        assertEquals(
            listOf("第一句", "第二句"),
            SentenceTranslationParser.parse("```text\n第一句\n第二句\n```"),
        )
        assertEquals(
            listOf("第一句", "第二句"),
            SentenceTranslationParser.parse("```text\n第一句\n第二句\n```\n"),
        )
    }

    // ---- isIdentityTranslation / translationDisplayTextOrNull ----

    @Test
    fun testIdentityTranslationDetectsEcho() {
        assertTrue(isIdentityTranslation("Hello world", "  Hello   world "))
        assertTrue(!isIdentityTranslation("Hello world", "你好，世界"))
        assertTrue(!isIdentityTranslation("  ", "你好"))
    }

    @Test
    fun testTranslationDisplayTextOrNullReturnsNullWhenBlank() {
        assertNull("".translationDisplayTextOrNull())
        assertNull("   ".translationDisplayTextOrNull())
        assertEquals("你好", "你好\n".translationDisplayTextOrNull())
    }

    @Test
    fun testTranslationDisplayTextPreservesBlankLinesAndPunctuation() {
        assertEquals(
            "你好，世界！\n\n再见。",
            "你好，世界！\n\n再见。".translationDisplayText(),
        )
    }
}
