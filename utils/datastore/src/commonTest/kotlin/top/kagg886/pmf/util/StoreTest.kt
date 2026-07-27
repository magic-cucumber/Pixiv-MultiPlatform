package top.kagg886.pmf.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StoreTest {
    @AfterTest
    fun clearInstances() {
        Store.instancesLock.withLock { Store.instances.clear() }
    }

    @Test
    fun samePathReturnsTheCachedInstance() {
        val path = "same.preferences_pb".toPath()

        val first = Store.of(path, ::newScope, ::newDataStore)
        val second = Store.of(path, ::newScope, ::newDataStore)

        assertSame(first, second)
    }

    @Test
    fun differentPathsReturnIndependentInstances() {
        val first = Store.of("first.preferences_pb".toPath(), ::newScope, ::newDataStore)
        val second = Store.of("second.preferences_pb".toPath(), ::newScope, ::newDataStore)

        assertNotSame(first, second)
    }

    @Test
    fun concurrentCallsForOnePathCreateOnlyOneInstance() = runTest {
        val path = "concurrent.preferences_pb".toPath()
        var factoryCalls = 0

        val stores = List(64) {
            async(Dispatchers.Default) {
                Store.of(path, ::newScope) { _, _ ->
                    factoryCalls++
                    TestDataStore()
                }
            }
        }.awaitAll()

        assertTrue(stores.all { it === stores.first() })
        assertEquals(1, factoryCalls)
    }

    @Test
    fun factoryScopeCancellationEvictsTheCachedInstance() {
        val path = "cancel.preferences_pb".toPath()
        val scope = newScope()
        val first = Store.of(path, { scope }, ::newDataStore)

        scope.cancel()
        val second = Store.of(path, ::newScope, ::newDataStore)

        assertNotSame(first, second)
    }

    @Test
    fun oldFactoryCompletionCannotEvictAReplacementInstance() {
        val path = "replacement.preferences_pb".toPath()
        val scope = newScope()
        val first = Store.of(path, { scope }, ::newDataStore)
        val replacement = TestDataStore()
        Store.instancesLock.withLock { Store.instances[path] = replacement }

        scope.cancel()

        assertSame(replacement, Store.instancesLock.withLock { Store.instances[path] })
        assertNotSame(first, replacement)
    }

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob())

    private fun newDataStore(scope: CoroutineScope, path: Path): DataStore<Preferences> = TestDataStore()
}
