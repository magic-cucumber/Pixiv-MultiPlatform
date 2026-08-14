package top.kagg886.pmf.translate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 流式 JSON 增量提取与严格解析的用例。
 *
 * 覆盖小说正文流式翻译场景：模型逐 token 输出残缺 JSON 时，
 * 只提取已闭合的句子字符串，绝不把原始 JSON 上屏。
 */
class IncrementalSentenceParserTest {

    // ---- IncrementalSentenceParser.extractSentences ----

    @Test
    fun testCompleteJsonObject() {
        assertEquals(
            listOf("你好", "世界"),
            IncrementalSentenceParser.extractSentences("""{"sentences":["你好","世界"]}"""),
        )
    }

    @Test
    fun testCompleteBareArray() {
        assertEquals(
            listOf("a", "b"),
            IncrementalSentenceParser.extractSentences("""["a","b"]"""),
        )
    }

    @Test
    fun testUnclosedArrayKeepsClosedSentences() {
        assertEquals(
            listOf("你好", "世界"),
            IncrementalSentenceParser.extractSentences("""{"sentences":["你好","世界""""),
        )
    }

    @Test
    fun testTrailingCommaKeepsClosedSentences() {
        assertEquals(
            listOf("你好"),
            IncrementalSentenceParser.extractSentences("""{"sentences":["你好","""),
        )
    }

    @Test
    fun testUnterminatedStringIsNotEmitted() {
        assertEquals(
            emptyList(),
            IncrementalSentenceParser.extractSentences("""{"sentences":["你好"""),
        )
    }

    @Test
    fun testPartialPrefixBeforeArrayIsEmpty() {
        assertEquals(
            emptyList(),
            IncrementalSentenceParser.extractSentences("""{"sent"""),
        )
    }

    @Test
    fun testEscapedQuoteInsideSentence() {
        assertEquals(
            listOf("he said \"hi\""),
            IncrementalSentenceParser.extractSentences("""{"sentences":["he said \"hi\""]}"""),
        )
    }

    @Test
    fun testEscapedBackslash() {
        assertEquals(
            listOf("a\\b"),
            IncrementalSentenceParser.extractSentences("""{"sentences":["a\\b"]}"""),
        )
    }

    @Test
    fun testEscapedNewline() {
        assertEquals(
            listOf("第一句\n第二句"),
            IncrementalSentenceParser.extractSentences("""{"sentences":["第一句\n第二句"]}"""),
        )
    }

    @Test
    fun testCodeFenceWrapper() {
        assertEquals(
            listOf("a", "b"),
            IncrementalSentenceParser.extractSentences(
                """
                ```json
                {"sentences":["a","b"]}
                ```
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun testTrailingGarbageAfterArray() {
        assertEquals(
            listOf("a"),
            IncrementalSentenceParser.extractSentences("""{"sentences":["a"]} extra garbage"""),
        )
    }

    @Test
    fun testNonJsonStreamIsEmpty() {
        assertEquals(
            emptyList(),
            IncrementalSentenceParser.extractSentences("正在翻译中……"),
        )
    }

    @Test
    fun testBlankInputIsEmpty() {
        assertEquals(emptyList(), IncrementalSentenceParser.extractSentences("  "))
    }

    @Test
    fun testExtractMatchesParseOnCompleteJson() {
        val full = """{"sentences":["你好","世界"]}"""
        assertEquals(
            SentenceTranslationParser.parse(full),
            IncrementalSentenceParser.extractSentences(full),
        )
    }

    // ---- SentenceTranslationParser.parseStrict ----

    @Test
    fun testParseStrictAcceptsObjectAndArray() {
        assertEquals(
            listOf("你好", "世界"),
            SentenceTranslationParser.parseStrict("""{"sentences":["你好","世界"]}"""),
        )
        assertEquals(
            listOf("a", "b"),
            SentenceTranslationParser.parseStrict("""["a","b"]"""),
        )
    }

    @Test
    fun testParseStrictRejectsPlainText() {
        assertNull(SentenceTranslationParser.parseStrict("plain fallback text"))
    }

    @Test
    fun testParseStrictRejectsTruncatedJson() {
        // 截断的 JSON（maxTokens 截断或流未闭合）不得回退为原文
        assertNull(SentenceTranslationParser.parseStrict("""{"sentences":["你好""""))
    }

    @Test
    fun testParseStrictRejectsEmptyArray() {
        assertNull(SentenceTranslationParser.parseStrict("""{"sentences":[]}"""))
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
        assertEquals("你好", """{"sentences":["你好"]}""".translationDisplayTextOrNull())
    }
}
