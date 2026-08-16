package top.kagg886.pmf.translate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking

/**
 * [KoogSessionCache] 并发/压力测试：验证同 key 共享会话、异 key 互不逐出、
 * 超容量才逐出并 close、构造失败不损坏缓存状态（close-mid-execute 防御）。
 */
class KoogSessionCacheTest {
    private class FakeClient(private val name: String) : AutoCloseable {
        var closed = false
            private set

        override fun close() {
            closed = true
        }

        override fun toString() = name
    }

    @Test
    fun testConcurrentSameKeySharesSingleSession() = runBlocking {
        val cache = KoogSessionCache<FakeClient>(maxSessions = 32)
        var created = 0
        val results = (1..32).map {
            async {
                cache.getOrCreate("k") {
                    created++
                    FakeClient("c$created")
                }
            }
        }.awaitAll()

        assertEquals(1, created, "并发取同 key 只应构造一次")
        assertTrue(results.all { it === results.first() })
        assertEquals(1, cache.createdCount)
        assertEquals(0, cache.closedCount)
    }

    @Test
    fun testDifferentKeysNoEvictionNoClose() = runBlocking {
        val cache = KoogSessionCache<FakeClient>(maxSessions = 32)
        (1..20).map { i ->
            async {
                cache.getOrCreate("key$i") { FakeClient("c$i") }
            }
        }.awaitAll()

        assertEquals(20, cache.createdCount)
        assertEquals(0, cache.closedCount, "容量内绝不关闭任何会话")
    }

    @Test
    fun testEvictionClosesOldestOnly() = runBlocking {
        val cache = KoogSessionCache<FakeClient>(maxSessions = 2)
        val first = cache.getOrCreate("k1") { FakeClient("c1") }
        val second = cache.getOrCreate("k2") { FakeClient("c2") }
        val third = cache.getOrCreate("k3") { FakeClient("c3") }

        assertTrue(first.closed, "超容量时应逐出并 close 最旧会话")
        assertTrue(!second.closed)
        assertTrue(!third.closed)
        assertEquals(1, cache.closedCount)

        // 被逐出的 key 可重建（k1 重建时 k2 被逐出）
        val recreated = cache.getOrCreate("k1") { FakeClient("c1-again") }
        assertTrue(!recreated.closed)
        assertEquals(4, cache.createdCount)
    }

    @Test
    fun testCreateFailureLeavesCacheIntact() = runBlocking {
        val cache = KoogSessionCache<FakeClient>(maxSessions = 8)
        val first = cache.getOrCreate("k1") { FakeClient("c1") }

        assertFailsWith<IllegalStateException> {
            cache.getOrCreate("k2") { throw IllegalStateException("boom") }
        }

        // 构造失败的 key 未提交，已有会话不受影响（1b：无半损坏缓存状态）
        assertSame(first, cache.getOrCreate("k1") { FakeClient("x") })
        assertEquals(1, cache.createdCount)
        assertEquals(0, cache.closedCount)
    }

    @Test
    fun testRepeatedGetNeverClosesInUseSession() = runBlocking {
        val cache = KoogSessionCache<FakeClient>(maxSessions = 8)
        val session = cache.getOrCreate("k") { FakeClient("c") }
        repeat(100) {
            assertSame(session, cache.getOrCreate("k") { FakeClient("x") })
        }
        assertTrue(!session.closed)
        assertEquals(1, cache.createdCount)
        assertEquals(0, cache.closedCount)
    }
}
