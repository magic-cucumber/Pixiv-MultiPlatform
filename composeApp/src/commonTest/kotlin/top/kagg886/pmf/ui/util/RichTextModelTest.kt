package top.kagg886.pmf.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

/** 富文本结构化解析与翻译的用例：链接 URL 保留、翻译替换文本、失败整体回退。 */
class RichTextModelTest {

    @Test
    fun testParseHtmlPlainTextWithBreaks() {
        assertEquals(
            listOf(
                RichSegment.Text("第一段"),
                RichSegment.Break,
                RichSegment.Text("第二段"),
                RichSegment.Break,
            ),
            parseHtmlSegments("<p>第一段</p><p>第二段</p>"),
        )
    }

    @Test
    fun testParseHtmlLinkAndBold() {
        assertEquals(
            listOf(
                RichSegment.Text("see "),
                RichSegment.Link("here", "https://example.com"),
                RichSegment.Bold("bold"),
            ),
            parseHtmlSegments("""see <a href="https://example.com">here</a><strong> bold</strong>"""),
        )
    }

    @Test
    fun testParseHtmlBr() {
        assertEquals(
            listOf(RichSegment.Text("a"), RichSegment.Break, RichSegment.Text("b")),
            parseHtmlSegments("a<br>b"),
        )
    }

    @Test
    fun testParseHtmlBlankIsEmpty() {
        assertEquals(emptyList(), parseHtmlSegments(""))
        assertEquals(emptyList(), parseHtmlSegments("   "))
    }

    @Test
    fun testTranslateRichSegmentsPreservesLinks() = runBlocking {
        val segments = parseHtmlSegments("""点击 <a href="https://example.com">这里</a> 查看""")
        val translated = translateRichSegments(segments) { text ->
            when (text.trim()) {
                "点击" -> "Click"
                "这里" -> "here"
                "查看" -> "view"
                else -> null
            }
        }
        assertEquals(
            listOf(
                RichSegment.Text("Click "),
                RichSegment.Link("here", "https://example.com"),
                RichSegment.Text(" view"),
            ),
            translated,
        )
    }

    @Test
    fun testTranslateRichSegmentsKeepsUrlLikeDisplayUntranslated() = runBlocking {
        val segments = parseHtmlSegments("""<a href="https://example.com">https://example.com</a>""")
        val translated = translateRichSegments(segments) { null }
        assertEquals(
            listOf(RichSegment.Link("https://example.com", "https://example.com")),
            translated,
        )
    }

    @Test
    fun testTranslateRichSegmentsFlattensBold() = runBlocking {
        val segments = listOf(RichSegment.Bold("bold text"))
        val translated = translateRichSegments(segments) { "译:bold text" }
        assertEquals(listOf(RichSegment.Text("译:bold text")), translated)
    }

    @Test
    fun testTranslateRichSegmentsFailureReturnsNull() = runBlocking {
        val segments = parseHtmlSegments("hello")
        assertNull(translateRichSegments(segments) { null })
    }

    @Test
    fun testTranslateRichSegmentsEmptyReturnsEmpty() = runBlocking {
        assertEquals(emptyList(), translateRichSegments(emptyList()) { null })
    }

    @Test
    fun testIsUrlLike() {
        assertEquals(true, isUrlLike("https://example.com"))
        assertEquals(true, isUrlLike("www.example.com"))
        assertEquals(true, isUrlLike("ftp://example.com"))
        assertEquals(false, isUrlLike("这里"))
        assertEquals(false, isUrlLike("see here"))
    }
}
