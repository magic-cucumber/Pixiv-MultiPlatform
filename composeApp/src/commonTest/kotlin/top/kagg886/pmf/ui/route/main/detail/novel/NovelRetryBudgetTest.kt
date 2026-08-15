package top.kagg886.pmf.ui.route.main.detail.novel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 自动重试预算逻辑的用例：预算内片段返回、标记消耗、二次调用不重复、部分已用。 */
class NovelRetryBudgetTest {

    @Test
    fun testFirstCallTakesAllUnbudgeted() {
        val counts = mutableMapOf<Int, Int>()
        val retriable = takeRetryBudget(listOf(1, 2, 3), counts)
        assertEquals(listOf(1, 2, 3), retriable)
        assertEquals(mapOf(1 to 1, 2 to 1, 3 to 1), counts)
    }

    @Test
    fun testSecondCallReturnsEmpty() {
        val counts = mutableMapOf<Int, Int>()
        takeRetryBudget(listOf(1, 2), counts)
        assertEquals(emptyList(), takeRetryBudget(listOf(1, 2), counts))
    }

    @Test
    fun testMixedBudgetOnlyTakesUnused() {
        val counts = mutableMapOf<Int, Int>(2 to 1)
        val retriable = takeRetryBudget(listOf(1, 2, 3), counts)
        assertEquals(listOf(1, 3), retriable)
        assertTrue(counts[2] == 1, "已用预算的片段不应被重复标记")
        assertTrue(counts[1] == 1 && counts[3] == 1)
    }

    @Test
    fun testEmptyFailedIdsIsNoOp() {
        val counts = mutableMapOf<Int, Int>()
        assertEquals(emptyList(), takeRetryBudget(emptyList(), counts))
        assertTrue(counts.isEmpty())
    }
}
