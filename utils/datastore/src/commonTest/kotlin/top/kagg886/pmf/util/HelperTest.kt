package top.kagg886.pmf.util

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class HelperTest {
    @Test
    fun persistedValueIsTheInitialValueWithoutCallingDefault() = runTest {
        val dataStore = TestDataStore().also { it.set("enabled", true) }
        var defaultCalls = 0

        val flow = dataStore.flow(backgroundScope, "enabled", Boolean::class) { defaultCalls++; false }

        assertEquals(true, flow.value)
        assertEquals(0, defaultCalls)
    }

    @Test
    fun missingValueUsesDefaultImmediately() = runTest {
        val flow = TestDataStore().flow(backgroundScope, "count", Int::class) { 42 }

        assertEquals(42, flow.value)
    }

    @Test
    fun reifiedFlowOverloadUsesThePersistedValue() = runTest {
        val dataStore = TestDataStore().also { it.set("name", "saved") }

        val flow = dataStore.flow(backgroundScope, "name") { "default" }

        assertEquals("saved", flow.value)
    }

    @Test
    fun getReturnsPersistedValueOrDefault() = runTest {
        val dataStore = TestDataStore().also { it.set("saved", 7) }

        assertEquals(7, dataStore.get("saved") { 0 })
        assertEquals(42, dataStore.get("missing") { 42 })
    }

    @Test
    fun setUpdatesAnExistingFlow() = runTest {
        val dataStore = TestDataStore()
        val flow = dataStore.flow(backgroundScope, "count", Int::class) { 0 }
        runCurrent()

        dataStore.set("count", 7)
        runCurrent()

        assertEquals(7, flow.value)
    }

    @Test
    fun removingAValueReturnsTheFlowToDefault() = runTest {
        val dataStore = TestDataStore().also { it.set("name", "saved") }
        val flow = dataStore.flow(backgroundScope, "name", String::class) { "default" }
        runCurrent()

        dataStore.remove("name")
        runCurrent()

        assertEquals("default", flow.value)
    }

    @Test
    fun defaultIsReevaluatedWhenAValueBecomesMissingAgain() = runTest {
        val dataStore = TestDataStore()
        var nextDefault = 0
        val flow = dataStore.flow(backgroundScope, "count", Int::class) { ++nextDefault }
        runCurrent()
        val callsBeforeRemoval = nextDefault

        dataStore.set("count", 10)
        runCurrent()
        dataStore.remove("count")
        runCurrent()

        assertEquals(callsBeforeRemoval + 1, nextDefault)
        assertEquals(nextDefault, flow.value)
    }

    @Test
    fun removeDeletesAValueWithoutKnowingItsType() = runTest {
        val dataStore = TestDataStore()

        dataStore.set("value", 1L)
        dataStore.remove("value")

        assertEquals(null, dataStore.dataValue(longPreferencesKey("value")))
    }

    @Test
    fun removeDoesNotDeleteOtherValues() = runTest {
        val dataStore = TestDataStore()
        dataStore.set("remove", "value")
        dataStore.set("keep", "value")

        dataStore.remove("remove")

        assertEquals(null, dataStore.dataValue(stringPreferencesKey("remove")))
        assertEquals("value", dataStore.dataValue(stringPreferencesKey("keep")))
    }

    @Test
    fun setSupportsEveryPreferenceValueType() = runTest {
        val dataStore = TestDataStore()

        dataStore.set("boolean", true)
        dataStore.set("int", 1)
        dataStore.set("long", 2L)
        dataStore.set("float", 3f)
        dataStore.set("double", 4.0)
        dataStore.set("string", "value")
        dataStore.set("set", setOf("one", "two"))

        assertEquals(true, dataStore.dataValue(booleanPreferencesKey("boolean")))
        assertEquals(1, dataStore.dataValue(intPreferencesKey("int")))
        assertEquals(2L, dataStore.dataValue(longPreferencesKey("long")))
        assertEquals(3f, dataStore.dataValue(floatPreferencesKey("float")))
        assertEquals(4.0, dataStore.dataValue(doublePreferencesKey("double")))
        assertEquals("value", dataStore.dataValue(stringPreferencesKey("string")))
        assertEquals(setOf("one", "two"), dataStore.dataValue(stringSetPreferencesKey("set")))
    }

    @Test
    fun setRejectsASetContainingNonStrings() = runTest {
        val dataStore = TestDataStore()

        val error = assertFailsWith<IllegalArgumentException> {
            dataStore.set("invalid", setOf<Any>(1))
        }

        assertEquals("Only Set<String> is supported by Preferences", error.message)
    }

    @Test
    fun unsupportedTypesAreRejected() = runTest {
        val dataStore = TestDataStore()

        assertFailsWith<IllegalArgumentException> {
            dataStore.flow(backgroundScope, "unsupported", Unsupported::class) { Unsupported() }
        }
        assertFailsWith<IllegalArgumentException> {
            dataStore.set("unsupported", Unsupported())
        }
    }

    @Test
    fun cancellingTheStateInScopeStopsFurtherUpdates() = runTest {
        val dataStore = TestDataStore()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val flow = dataStore.flow(scope, "enabled", Boolean::class) { false }
        runCurrent()

        scope.cancel()
        dataStore.set("enabled", true)
        runCurrent()

        assertEquals(false, flow.value)
    }

    private suspend fun <T> TestDataStore.dataValue(key: androidx.datastore.preferences.core.Preferences.Key<T>): T? =
        data.first()[key]

    private class Unsupported
}
