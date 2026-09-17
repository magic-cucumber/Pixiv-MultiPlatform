package top.kagg886.pmf.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals

/** 句内片段切分（顿号/逗号/分号，括号组内不切）的用例。 */
class SplitSentenceFragmentsTest {

    @Test
    fun testSimpleSplit() {
        assertEquals(
            listOf("しろは", "お早う"),
            splitSentenceFragments("しろは、お早う"),
        )
    }

    @Test
    fun testCommaAndSemicolon() {
        assertEquals(
            listOf("a", "b", "c"),
            splitSentenceFragments("a，b；c"),
        )
    }

    @Test
    fun testBracketGroupSeparatorNotSplit() {
        // 、在括号组内：不产生片段边界
        assertEquals(
            listOf("と⌈こんにちは、元気⌋と"),
            splitSentenceFragments("と⌈こんにちは、元気⌋と"),
        )
    }

    @Test
    fun testConsecutiveSeparators() {
        assertEquals(
            listOf("a", "b"),
            splitSentenceFragments("a、、b"),
        )
    }

    @Test
    fun testSingleFragment() {
        assertEquals(
            listOf("こんにちは"),
            splitSentenceFragments("こんにちは"),
        )
    }

    @Test
    fun testBlankInput() {
        assertEquals(emptyList(), splitSentenceFragments(""))
        assertEquals(emptyList(), splitSentenceFragments("、、"))
    }
}
