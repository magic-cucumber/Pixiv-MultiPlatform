package top.kagg886.pmf.ui.route.main.detail.novel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 自动重试预算逻辑的用例：预算内片段返回、计数累加、达上限后不再重试。 */
class NovelRetryBudgetTest {

    @Test
    fun testFirstCallTakesAllUnbudgeted() {
        val counts = mutableMapOf<Int, Int>()
        val retriable = takeRetryBudget(listOf(1, 2, 3), counts)
        assertEquals(listOf(1, 2, 3), retriable)
        assertEquals(mapOf(1 to 1, 2 to 1, 3 to 1), counts)
    }

    @Test
    fun testSecondCallStillWithinBudget() {
        // 每 epoch 预算 2 次：第二次仍可取回，计数累加
        val counts = mutableMapOf<Int, Int>()
        takeRetryBudget(listOf(1, 2), counts)
        val second = takeRetryBudget(listOf(1, 2), counts)
        assertEquals(listOf(1, 2), second, "预算 2 次时第二次仍应重试")
        assertEquals(mapOf(1 to 2, 2 to 2), counts)
    }

    @Test
    fun testThirdCallReturnsEmptyAfterBudgetExhausted() {
        val counts = mutableMapOf<Int, Int>()
        takeRetryBudget(listOf(1), counts)
        takeRetryBudget(listOf(1), counts)
        assertEquals(emptyList(), takeRetryBudget(listOf(1), counts), "预算耗尽后不再自动重试")
        assertEquals(mapOf(1 to 2), counts)
    }

    @Test
    fun testMixedBudgetOnlyTakesWithinLimit() {
        val counts = mutableMapOf<Int, Int>(2 to 2) // 片段 2 已达预算上限
        val retriable = takeRetryBudget(listOf(1, 2, 3), counts)
        assertEquals(listOf(1, 3), retriable)
        assertTrue(counts[2] == 2, "已达预算上限的片段不应被再次标记")
        assertTrue(counts[1] == 1 && counts[3] == 1)
    }

    @Test
    fun testEmptyFailedIdsIsNoOp() {
        val counts = mutableMapOf<Int, Int>()
        assertEquals(emptyList(), takeRetryBudget(emptyList(), counts))
        assertTrue(counts.isEmpty())
    }
}
