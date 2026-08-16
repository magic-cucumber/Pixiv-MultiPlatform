package top.kagg886.pmf.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

/** autoTypo 共享行预处理的用例：原文与翻译渲染路径共用，保证排版一致。 */
class AutoTypoLinesTest {

    @Test
    fun testLinesAreTrimmedAndBlankLinesDropped() {
        assertEquals(
            listOf(
                AutoTypoLine("第一段。", 0, 4),
                AutoTypoLine("第二段！", 6, 10),
            ),
            autoTypoLines("第一段。\n\n第二段！"),
        )
    }

    @Test
    fun testTrailingWhitespaceKeepsOriginalOffsets() {
        // start/end 为行在原文中的原始边界（未 trim），句子定位与间隙渲染基于原始偏移
        assertEquals(
            listOf(AutoTypoLine("第一句。", 0, 8)),
            autoTypoLines("  第一句。  "),
        )
    }

    @Test
    fun testBlankTextYieldsEmptyList() {
        assertEquals(emptyList(), autoTypoLines(""))
        assertEquals(emptyList(), autoTypoLines("\n\n  \n"))
    }

    @Test
    fun testCollapseBigLinesOnlyAffectsThreePlusNewlines() {
        assertEquals("a\nb", collapseBigLines("a\n\n\nb"))
        // 双换行（段落间隔）保留
        assertEquals("a\n\nb", collapseBigLines("a\n\nb"))
        // 换行间带空白时不折叠（与原 replaceBigLines 语义一致，仅折叠连续换行）
        assertEquals("a \n  \n \n b", collapseBigLines("a \n  \n \n b"))
    }
}
