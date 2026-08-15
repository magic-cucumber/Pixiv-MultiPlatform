package top.kagg886.pmf.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NovelTranslationWindowTest {
    @Test
    fun testWindowIsCurrentViewportPlusTwoPagesOfLines() {
        assertEquals(10..42, calculateTranslationLineWindow(10, 20, 100, lookaheadPages = 2))
        assertEquals(5..7, calculateTranslationLineWindow(5, 5, 100, lookaheadPages = 2))
    }

    @Test
    fun testWindowIsClampedToTextLines() {
        assertEquals(90..99, calculateTranslationLineWindow(90, 99, 100, lookaheadPages = 2))
        assertEquals(0..0, calculateTranslationLineWindow(-5, -1, 1, lookaheadPages = 2))
    }

    @Test
    fun testWindowRequiresValidLineCount() {
        assertFailsWith<IllegalArgumentException> {
            calculateTranslationLineWindow(0, 0, 0)
        }
    }
}
